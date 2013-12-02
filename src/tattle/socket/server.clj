
(ns tattle.socket.server
  (:require  [clojure.java.shell :as sh]
             [clojure.java.io :as io]
             [clojure.string :refer [trim]])
  (:import [java.net ServerSocket Socket SocketException]
           [java.io DataOutputStream InputStreamReader PrintWriter StringWriter OutputStreamWriter]))

(defrecord Servers [server connections closed])

(defn- getstack [t]
  (let [res (StringWriter.)
        pw  (PrintWriter. res)
        trace (.printStackTrace t pw)]
    (.toString res)))

(defn- on-thread [f]
  (doto (Thread. f) .start))

(defn- close [socket]
  (when-not (.isClosed socket)
    (doto socket
      (.close))))

(defn- accept [serversock connections closed handler]
  (let [s (.accept serversock)
        ins (.getInputStream s)
        outs (.getOutputStream s)]
    (on-thread #(do
                  (dosync (commute connections conj s))
                  (try
                    (handler ins outs)
                    (catch SocketException e))
                  (close s)
                  (dosync (commute connections disj s)
                          (swap! closed inc))))))

(defn create-server [port handler]
  "Creates a server on port exec'ing func with an input-stream
  and an output stream"
  (let [ss (ServerSocket. port)
        connections (ref #{})
        closed (atom 0)]
    (on-thread #(when-not (.isClosed ss)
                  (try
                    (accept ss connections closed handler)
                    (catch SocketException e (println (str "Foo: " (getstack e)))))
                  (recur)))
    (Servers. ss connections closed)))
    
(defn close-server [server]
  (doseq [s @(:connections server)]
    (close s))
  (dosync (ref-set (:connections server) #{}))
  (close (:server server)))

(defn count-connections [server]
  (count @(:connections server)))

(defn closed-connections [server]
  @(:closed server))

(defn get-uptime [ins outs]
  (try
    (let [ps (PrintWriter. outs true)
          output (trim (:out (sh/sh "uptime")))]
      (doto ps
        (.print output)
        (.checkError)))
    (catch Exception e (println (getstack e)))))

