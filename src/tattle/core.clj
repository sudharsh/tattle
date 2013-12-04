(ns tattle.core
  (:require [tattle.server :as server]
            [tattle.handlers :as handlers]
            [taoensso.timbre :as timbre :refer :all])
  (:gen-class))
               
            
(defn -main [& args]
  (info "Adding handlers and some such")
  ;; FIXME: Read this from a config of-course :)
  (server/add-handler :uptime handlers/uptime)
  (server/add-handler :nodes handlers/get-nodes)
  (server/add-handler :ping handlers/ping!)
  (server/add-handler :status handlers/status)
  (server/create-server 6000)
  (info "Listening on port 6000"))