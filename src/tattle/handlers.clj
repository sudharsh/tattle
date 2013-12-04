(ns tattle.handlers
  (:require [tattle.node :as node]
            [tattle.client :as client]
            [taoensso.timbre :as timbre :refer :all]
            [cheshire.core :as json]
            [clojure.string :as string]
            [clojure.java.shell :as sh]))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn get-nodes [_]
  (node/get-nodes))

(defn ping! [{:keys [remote nodes]}]
  (debug "Updating metadata")
  (do
    (node/merge-nodes nodes)
    (node/get-nodes)))

(defn status [_]
  (node/network-summary))