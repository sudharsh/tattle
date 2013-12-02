(ns tattle.socket.server
  (:require  [clojure.java.shell :as sh]
             [clojure.java.io :as io]
             [clojure.string :as string]
             [cheshire.core :as json])
  (:import [java.net ServerSocket Socket SocketException]
           [java.io DataOutputStream InputStreamReader InputStream
            PrintWriter StringWriter OutputStreamWriter]))

(defrecord Servers [server connections closed])

(def handlers (ref {}))

(defn- getstack [t]
  (let [res (StringWriter.)
        pw  (PrintWriter. res)
        trace (.printStackTrace t pw)]
    (.toString res)))

(defn- on-thread [f]
  (doto (Thread. f) .start))

(defn- close [^Socket socket]
  (when-not (.isClosed socket)
    (doto socket
      (.close))))

(defn- read-stream [^InputStream ins]
  (let [br (io/reader ins)]
    (string/join "\n" (line-seq br))))

(defn- accept [serversock connections closed]
  (let [s (.accept serversock)
        ins (.getInputStream s)
        outs (.getOutputStream s)
        ps (PrintWriter. outs true)]
    (on-thread #(do
                  (dosync (commute connections conj s))
                  (try
                    (doto ps
                      (.print (json/generate-string
                               (execute-handler handlers ins outs)))
                      (.checkError))
                    (catch SocketException e))
                  (close s)
                  (dosync (commute connections disj s)
                          (swap! closed inc))))))

;; Pub functions
(defn create-server [port]
  "Creates a server on port exec'ing func with an input-stream
  and an output stream"
  (let [ss (ServerSocket. port)
        connections (ref #{})
        closed (atom 0)]
    (on-thread #(when-not (.isClosed ss)
                  (try
                    (accept ss connections closed)
                    (catch SocketException e (println (str "Foo: " (getstack e)))))
                  (recur)))
    (Servers. ss connections closed)))

(defn close-server [server]
  "Closes a server. Shuts down all connections"
  (doseq [s @(:connections server)]
    (close s))
  (dosync (ref-set (:connections server) #{}))
  (close (:server server)))

(defn count-connections [server]
  "Get the number of open connections to this agent"
  (count @(:connections server)))

(defn closed-connections [server]
  "Get the number of closed connections"
  @(:closed server))

(defn add-handler [command fun]
  "Add a handler for a given command"
  (dosync (commute handlers assoc command fun)))

(defn remove-handler [command]
  "Remove a handler"
  (dosync (commute handlers dissoc command)))

;; Execute a handler
(defn- execute-handler [handlers ins outs]
  (try
    (let [input (json/parse-string (read-stream ins) true)
          command (keyword (:command input))
          f (get @handlers command)]
      (if (nil? f)
        {:error  (str "invalid command" command)}
        {:response (f (:args input))}))
    (catch Exception e
      {:error      "internal"
       :stacktrace (getstack e)
       :exception  (str e)})))
