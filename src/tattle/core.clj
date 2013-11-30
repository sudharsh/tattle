(ns tattle.core
  (:require [tattle.socket.server :as server]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :refer [trim]]
            [taoensso.timbre :as timbre :refer :all])
  (:import [java.util.concurrent Executors]
           [java.io PrintStream BufferedInputStream OutputStreamWriter]))


(defn get-system-stats [ins outs]
  (try
    (let [cmd (slurp ins)
          output (:out (sh/sh (trim cmd)))]
      (doto outs
        (.write (.getBytes output))
        (.flush)))
    (catch Exception e (spit "/tmp/foo.txt" (str (.getMessage e))))))

  
  
  

