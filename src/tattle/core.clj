(ns tattle.core
  (:require [tattle.socket.server :as server]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [taoensso.timbre :as timbre :refer :all]))

(def nodes (ref {}))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn list-nodes [_]
  (keys @nodes))

(server/add-handler :uptime uptime)
(server/add-handler :list-nodes list-nodes)