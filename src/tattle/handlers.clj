(ns tattle.handlers
  (:require [clojure.string :as string]
            [taoensso.timbre :as timbre :refer :all]
            [clojure.java.shell :as sh]))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn get-nodes [{:keys [nodes]}]
  @nodes)

(defn add! [{:keys [node nodes]}]
  (dosync (commute nodes assoc node "added")))

(defn ping! [{:keys [node remote nodes]}]
  (info (str "Updating metadata for" node))
  (if (nil? node)
    (add! {:node remote :nodes nodes})
    (add! {:node node :nodes nodes}))
  @nodes)
