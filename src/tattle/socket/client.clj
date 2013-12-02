(ns tattle.socket.client
  (:import [java.net Socket]
           [java.io PrintWriter InputStreamReader BufferedReader]))

(defn connect [{:keys [host port]}]
  (let [socket (Socket. host port)
        in (BufferedReader. (InputStreamReader. (.getInputStream socket)))
        out (PrintWriter. (.getOutputStream socket))
        conn (ref {:in in :out out :socket socket})]
    conn))

(defn write [conn msg]
  (doto (:out @conn)
    (.println (str msg "\r"))
    (.flush))
  (.close (:socket @conn)))
