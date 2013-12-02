(ns tattle.core
  (:require [tattle.socket.server :as server]
            [tattle.socket.client :as client]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [cheshire.core :as json]
            [taoensso.timbre :as timbre :refer :all])
  (:import (java.util.concurrent ScheduledThreadPoolExecutor TimeUnit))
  (:gen-class))

(def ^:private pool (atom nil))

(defonce gossip-interval (* 20 1000))
(defonce self (.getHostAddress (java.net.InetAddress/getLocalHost)))

(def nodes (ref (assoc {}
                  (keyword self) "up")))

(defn- thread-pool []
  (swap! pool (fn [p] (or p (ScheduledThreadPoolExecutor. 1)))))

(defn every [f timeout]
  (.scheduleWithFixedDelay (thread-pool)
                           f
                           0 timeout TimeUnit/MILLISECONDS))
(defn shutdown []
  (swap! pool (fn [p] (when p (.shutdown p)))))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))


(defn get-nodes [_]
  @nodes)


(defn add [{:keys [remote]}]
  (dosync (commute nodes assoc remote "added")))


(defn ping [{:keys [remote]}]
  (info (str "Updating metadata for" remote))
  (add {:remote remote}))


(defn gossip []
  (info "Starting gossip")
  (try
    (let [others (keys (dissoc @nodes (keyword self)))
          node (rand-nth others)
          payload {:command "ping"}            
          jsonified (json/generate-string payload)]
      (if (nil? node)
        (info "No nodes found to gossip with")
        (do
          (info (str "Exchanging gossip with " node))
          (client/write
           (client/connect {:host node :port 6000}) jsonified))))
    (catch Exception e (error e))))
                
            
(defn -main [& args]
  (info "Adding handlers and some such")
  (server/add-handler :uptime uptime)
  (server/add-handler :nodes get-nodes)
  (server/add-handler :add add)
  (server/add-handler :ping ping)
  (info (str "Setting gossip interval to " gossip-interval))
  (every gossip gossip-interval)
  (info "Starting agent")
  (server/create-server 6000)
  (info "Listening on port 6000"))