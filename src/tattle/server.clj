(ns tattle.server
  (:require  [clojure.java.shell :as sh]
             [clojure.java.io :as io]
             [clojure.string :as string]
             [cheshire.core :as json]
             [taoensso.timbre :as timbre :refer :all]
             [tattle.client :as client]
             [tattle.node :as node])
  (:import [java.net ServerSocket Socket SocketException]
           [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]
           [java.io InputStream PrintWriter StringWriter]))


(defrecord Servers [server connections closed])

;; FIXME: gossip interval should be tweakable
(defonce gossip-interval (* 20 1000))

(def ^:private pool (atom nil))
(def handlers (ref {}))

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
    (.readLine br)))

;; Execute a handler
(defn- execute-handler [handlers ins outs remote]
  (try
    (let [input (json/parse-string (read-stream ins) true)
          command (keyword (:command input))
          f (get @handlers command)
          args (merge input {:remote remote})]
      (info "Executing " command " with args " args)
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
        remote (.getHostAddress (.getInetAddress s))
        ins (.getInputStream s)
        outs (.getOutputStream s)
        ps (PrintWriter. outs true)]
    (on-thread
     #(do
       (debug (str remote " just connected"))
       (dosync (commute connections conj s))
       (try
         (doto ps
           (.print (json/generate-string
                    (execute-handler handlers ins outs remote)))
           ;; Force flush
           (.checkError))
         (catch SocketException e (error (getstack e))))
       (close s)
       (debug (str remote" disconnected"))
       (dosync (commute connections disj s)
               (swap! closed inc))))))

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

;; Internal handlers
(defn gossip [{:keys [host] :or { host (:ip (node/random-node)) }}] 
  (debug "Starting gossip")
  (try
    (let [payload {:command "ping" :nodes (node/get-nodes)}
          jsonified (json/generate-string payload)]
      (if (nil? host)
        (debug "No nodes found to gossip with")
        (do
          (debug "Exchanging gossip with " host)
          (let [connection (client/connect {:host host :port 6000})
                response (json/parse-string (client/write connection jsonified) true)]
            (debug "Merging node information")
            (node/merge-nodes (:response response))
            (.close (:socket @connection)))
          (node/get-nodes))))
    (catch Exception e (error "Error: " e))))

(defn bootstrap [{:keys [interface] :or { interface "eth0" }}]
  (info "Bootstrapping using interface:" interface)
   (let [ip (node/ip-for-interface interface)]
    (node/mark-node node/self ip)))

;; Pub functions
(defn create-server [port]
  "Creates a server on port exec'ing func with an input-stream
  and an output stream"
  (let [ss (ServerSocket. port)
        connections (ref #{})
        closed (atom 0)]
    (info "Adding internal handlers")
    (add-handler :bootstrap bootstrap)
    (add-handler :add gossip)
    (info "Starting agent")
    (on-thread #(when-not (.isClosed ss)
                  (try
                    (accept ss connections closed)
                    (catch SocketException e (println (str "Foo: " (getstack e)))))
                  (recur)))
    (info "Starting gossip every " gossip-interval " milliseconds")
    (every #(gossip {}) gossip-interval)
    (Servers. ss connections closed)))
