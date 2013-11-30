(ns tattle.socket.server
  (:import [java.net ServerSocket Socket SocketException]
           [java.io InputStreamReader OutputStreamWriter]))

(defrecord Servers [server connections closed])

(defn on-thread [f]
  (doto (Thread. f) .start))

(defn- close [socket]
  (when-not (.isClosed socket)
    (doto socket
      (.shutdownInput)
      (.shutdownOutput)
      (.close))))

(defn- accept [serversock connections closed handler]
  (let [s (.accept serversock)
        ins (.getInputStream s)
        outs (.getOutputStream s)]
    (on-thread #(do
                  (dosync (commute connections conj s))
                  (try
                    (println "am here")
                    (handler ins outs)
                    (catch SocketException e))
                  (println "oops")
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
                    (catch SocketException e))
                  (recur)))
    (Servers. ss connections closed)))
    
(defn close-server [server]
  (doseq [s @(:connections server)]
    (close s))
  (dosync (ref-set (:connections server) #{}))
  (.close (:server server)))

(defn count-connections [server]
  (count @(:connections server)))

(defn closed-connections [server]
  @(:closed server))
