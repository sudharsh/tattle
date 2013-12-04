(ns tattle.handlers
  (:require [tattle.node :as node]
            [tattle.socket.client :as client]
            [taoensso.timbre :as timbre :refer :all]
            [cheshire.core :as json]
            [clojure.string :as string]
            [clojure.java.shell :as sh]))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn get-nodes [_]
  (node/get-nodes))

(defn ping! [{:keys [remote nodes]}]
  (info "Updating metadata")
  (do
    (node/merge-nodes nodes)
    (node/get-nodes)))
