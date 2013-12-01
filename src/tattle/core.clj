(ns tattle.core
  (:require [tattle.socket.server :as server]
            [clojure.java.shell :as sh]
            [clojure.java.io :as io]
            [clojure.string :refer [trim]]
            [taoensso.timbre :as timbre :refer :all])
  (:import [java.util.concurrent Executors]
           [java.io  DataOutputStream PrintWriter StringWriter Writer BufferedReader InputStreamReader BufferedInputStream OutputStreamWriter]))

(defn getstack [t]
  (let [res (StringWriter.)
        pw  (PrintWriter. res)
        trace (.printStackTrace t pw)]
    (.toString res)))
  


(defn echo-server [in out]
  (let [ps (PrintWriter. out true)
        _ (.close ps)]
    (.print ps "zGooo")))
  

