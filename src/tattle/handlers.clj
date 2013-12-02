(ns tattle.handlers
  (:require [clojure.string :as string]
            [taoensso.timbre :as timbre :refer :all]
            [clojure.java.shell :as sh]))

(defn uptime [_]
  (string/trim (:out (sh/sh "uptime"))))

(defn get-nodes [{:keys [nodes]}]
  @nodes)

(defn add [{:keys [remote nodes]}]
  (dosync (commute nodes assoc remote "added")))

(defn ping [{:keys [remote nodes]}]
  (info (str "Updating metadata for" remote))
  (add {:remote remote :nodes nodes}))





