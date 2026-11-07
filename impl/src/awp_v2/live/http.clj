(ns awp-v2.live.http
  (:require [awp-v2.session.api :as session]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
           [java.net InetSocketAddress]))

(def ^:private max-body-bytes 131072)
(def ^:private operations
  {"fresh" :fresh "resume" :resume "selection" :selection
   "history" :history "transcript" :transcript "catalog" :catalog
   "context" :context "routing" :routing "turn" :turn "submit" :submit
   "observe" :observe "settle" :settle "stop" :stop
   "compact" :compact "attach" :attach "detach" :detach
   "voice-ended" :voice-ended})

(defn- send! [^HttpExchange exchange code value]
  (let [body (.getBytes (json/write-str value) "UTF-8")]
    (.set (.getResponseHeaders exchange) "Content-Type" "application/json")
    (.set (.getResponseHeaders exchange) "Cache-Control" "no-store")
    (.sendResponseHeaders exchange code (alength body))
    (with-open [out (.getResponseBody exchange)] (.write out body))))

(defn- parse-body [^HttpExchange exchange]
  (let [length (some-> (.getRequestHeaders exchange) (.getFirst "Content-Length"))]
    (when (and length (> (Long/parseLong length) max-body-bytes))
      (throw (ex-info "request too large" {:status 413})))
    (let [body (with-open [input (.getRequestBody exchange)]
                 (.readNBytes input (inc max-body-bytes)))]
      (when (> (alength body) max-body-bytes)
        (throw (ex-info "request too large" {:status 413})))
      (if (zero? (alength body)) {}
          (json/read-str (String. body "UTF-8") :key-fn keyword)))))

(defn- run-operation [system owner op data]
  (case op
    :fresh (session/fresh! system owner (:command-id data))
    :resume (session/resume! system owner (:command-id data) (:conversation-id data))
    :selection (session/selection system owner)
    :history (session/history system owner (:conversation-id data))
    :transcript (session/transcript system owner)
    :catalog (session/catalog system owner)
    :context (session/context system owner)
    :routing (session/routing system owner)
    :turn (session/turn system owner (:turn-id data))
    :submit (session/submit! system owner
                             {:turn-id (:turn-id data)
                              :origin (some-> (:origin data) keyword)
                              :messages (mapv (fn [m] (select-keys m [:role :content]))
                                              (:messages data))})
    :observe (session/observe! system owner (:turn-id data)
                               (update (:observation data) :kind keyword))
    :settle (session/settle! system owner (:turn-id data)
                             (some-> (:status data) keyword) (:answer data))
    :stop (session/stop! system owner (:turn-id data))
    :compact (session/compact! system owner (:command-id data) data)
    :attach (session/attach! system owner (:task-id data))
    :detach (session/detach! system owner)
    :voice-ended (session/voice-ended! system owner)))

(defn- status [result]
  (case (:error result)
    :incorrect 400 :not-found 404 :forbidden 403 :busy 409 :conflict 409 200))

(defn start! [{:keys [system token port host] :or {port 0 host "127.0.0.1"}}]
  (when-not (and system (string? token) (not (str/blank? token)))
    (throw (ex-info "system and token required" {})))
  (let [server (HttpServer/create (InetSocketAddress. ^String host (int port)) 0)]
    (.createContext server "/internal/session"
      (reify HttpHandler
        (handle [_ exchange]
          (try
            (let [path (.getPath (.getRequestURI exchange))
                  op (get operations (last (str/split path #"/")))
                  owner (.getFirst (.getRequestHeaders exchange) "X-AWP-Owner")
                  auth (.getFirst (.getRequestHeaders exchange) "Authorization")]
              (cond
                (not= auth (str "Bearer " token)) (send! exchange 401 {:error "unauthorized"})
                (or (str/blank? owner) (> (count owner) 256))
                (send! exchange 400 {:error "invalid owner"})
                (nil? op) (send! exchange 404 {:error "not-found"})
                :else (let [result (run-operation system owner op (parse-body exchange))]
                        (send! exchange (status result) result))))
            (catch Throwable e
              (send! exchange (or (:status (ex-data e)) 400)
                     {:error "invalid request"}))))))
    (.setExecutor server (java.util.concurrent.Executors/newVirtualThreadPerTaskExecutor))
    (.start server)
    server))

(defn stop! [^HttpServer server] (.stop server 0))
(defn port [^HttpServer server] (.getPort (.getAddress server)))
