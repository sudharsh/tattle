(ns tattle.core
  (:require [tattle.socket.server :as server]
            [clojure.java.shell :as sh]
            [clojure.string :as string]
            [taoensso.timbre :as timbre :refer :all])
  (:gen-class))


(def nodes (ref
            (assoc {}
              (keyword
               (.getHostAddress (java.net.InetAddress/getLocalHost)))
              "up")))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn get-nodes [_]
  @nodes)

(defn -main [& args]
  (info "Adding handlers and some such")
  (server/add-handler :uptime uptime)
  (server/add-handler :nodes get-nodes)
  (info "Starting agent")
  (server/create-server 6000)
  (info "Listening on port 6000"))