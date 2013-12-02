(ns tattle.socket.server
  (:require  [clojure.java.shell :as sh]
             [clojure.java.io :as io]
             [clojure.string :as string]
             [tattle.socket.client :as client]
             [cheshire.core :as json]
             [taoensso.timbre :as timbre :refer :all])
  (:import [java.net ServerSocket Socket SocketException]
           [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]
           [java.io DataOutputStream InputStreamReader InputStream
            PrintWriter StringWriter OutputStreamWriter]))

(defrecord Servers [server connections closed])
(defonce self (.getHostAddress (java.net.InetAddress/getLocalHost)))

;; FIXME: gossip interval should be tweakable
(defonce gossip-interval (* 20 1000))


(def ^:private pool (atom nil))
(def handlers (ref {}))
(def nodes (ref (assoc {}
                  (keyword self) "up")))

(defn- thread-pool []
  (swap! pool (fn [p] (or p (ScheduledThreadPoolExecutor. 1)))))

(defn- every [f timeout]
  (.scheduleWithFixedDelay (thread-pool)
                           f
                           0 timeout TimeUnit/MILLISECONDS))

(defn- shutdown []
  (swap! pool (fn [p] (when p (.shutdown p)))))

(defn- getstack [^Throwable t]
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

(defn- read-stream [^InputStream ins]
  (let [br (io/reader ins)]
    (string/join "\n" (line-seq br))))

;; Execute a handler
(defn- execute-handler [handlers ins outs remote]
  (try
    (let [input (json/parse-string (read-stream ins) true)
          command (keyword (:command input))
          f (get @handlers command)
          args {:remote remote :nodes nodes}]
      (if (nil? f)
        {:error  (str "invalid command" command)}
        ;; Call the handler with a bunch of metadata
        {:response (f args)}))
    (catch Exception e
      {:error      "internal"
       :stacktrace (getstack e)
       :exception  (str e)})))


(defn- accept [serversock connections closed]
  (let [s (.accept serversock)
        sa (.getCanonicalHostName (.getInetAddress s))
        ins (.getInputStream s)
        outs (.getOutputStream s)
        ps (PrintWriter. outs true)]
    (on-thread #(do
                  (debug (str sa " just connected"))
                  (dosync (commute connections conj s))
                  (try
                    (doto ps
                      (.print (json/generate-string
                               (execute-handler handlers ins outs sa)))
                      ;; Force flush
                      (.checkError))
                    (catch SocketException e))
                  (close s)
                  (debug (str sa " disconnected"))
                  (dosync (commute connections disj s)
                          (swap! closed inc))))))

(defn gossip []
  (info "Starting gossip")
  (try
    (let [others (keys (dissoc @nodes (keyword self)))
          node (rand-nth others)
          payload {:command "ping"}            
          jsonified (json/generate-string payload)]
      (if (nil? node)
        (info "No nodes found to gossip with")
        (do
          (info (str "Exchanging gossip with " node))
          (client/write
           (client/connect {:host node :port 6000}) jsonified))))
    (catch Exception e (error e))))

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
    (info "Starting gossip every " gossip-interval " milliseconds")
    (every gossip gossip-interval)
    (Servers. ss connections closed)))

(defn close-server [server]
  "Closes a server. Shuts down all connections"
  (info (str "Closing server"))
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
  (info (str "Adding handler " command))
  (dosync (commute handlers assoc command fun)))

(defn remove-handler [command]
  "Remove a handler"
  (info (str "Removing handler " command))
  (dosync (commute handlers dissoc command)))

