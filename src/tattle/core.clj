(ns tattle.core
  (:require [tattle.socket.server :as server]
            [tattle.socket.client :as client]
            [tattle.handlers :as handlers]
            [taoensso.timbre :as timbre :refer :all])
  (:gen-class))
               
            
(defn -main [& args]
  (info "Adding handlers and some such")
  ;; FIXME: Read this from a config of-course :)
  (server/add-handler :uptime handlers/uptime)
  (server/add-handler :nodes handlers/get-nodes)
  (server/add-handler :add handlers/add)
  (server/add-handler :ping handlers/ping)
  (info "Starting agent")
  (server/create-server 6000)
  (info "Listening on port 6000"))