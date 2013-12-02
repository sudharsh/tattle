(ns tattle.core
  (:require [tattle.socket.server :as server]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [taoensso.timbre :as timbre :refer :all]))

(def nodes (ref (assoc {} (keyword
                           (.getHostAddress (java.net.InetAddress/getLocalHost)))
                       "up")))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn get-nodes [_]
  @nodes)

(server/add-handler :uptime uptime)
(server/add-handler :nodes get-nodes)