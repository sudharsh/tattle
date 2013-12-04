(ns tattle.node
  (:require [cheshire.core :as json])
  (:import  [java.net NetworkInterface Inet4Address]))

(defrecord Node [id ip status last-seen])
(defonce self (str (java.util.UUID/randomUUID)))
  
(def nodes (ref {}))

(defn get-nodes []
  (map #(merge {} %1) (vals @nodes)))

(defn timestamp []
  (quot (System/currentTimeMillis) 1000))

(defn ip-for-interface [interface]
  (first (->> (NetworkInterface/getByName interface)
              .getInetAddresses
              enumeration-seq
              (filter #(instance? Inet4Address %1))
              (map #(.getHostAddress %1)))))
(defn mark-node [id ip & {:keys [status]
                          :or { status "ok" }}]
  (let [t (timestamp)
        n (Node. id ip status t)]
    (dosync (commute nodes assoc id n))))

(defn random-node []
  (let [n (vals @nodes)]
    (if (<= (count n) 1)
      nil
      (rand-nth (filter #(not (= self (:id %1))) n)))))


(defn network-summary []
  (frequencies (map :status (vals @nodes))))

(defn merge-nodes [response]
  (doseq [node response]
    (let [{:keys [id ip status] :or { status "ok" }} node]
      (mark-node id ip :status status)))
  (get-nodes))

