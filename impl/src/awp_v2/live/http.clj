(ns awp-v2.live.http
  (:require [awp-v2.session.api :as session]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.song.api :as song]
            [awp-v2.tasking.api :as tasking]
            [awp-v2.tasking.lifecycle :as task-life]
            [awp-v2.tasking.commands :as task-commands]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [cognitect.anomalies :as anom])
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

(defn- send-audio! [^HttpExchange exchange ^bytes body]
  (.set (.getResponseHeaders exchange) "Content-Type" "audio/mp4")
  (.set (.getResponseHeaders exchange) "Cache-Control" "no-store")
  (.sendResponseHeaders exchange 200 (alength body))
  (with-open [out (.getResponseBody exchange)] (.write out body)))

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

(defn- run-operation [system knowledge-system owner op data]
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
    :settle (let [result (session/settle! system owner (:turn-id data)
                                          (some-> (:status data) keyword) (:answer data))]
              (when (and knowledge-system (= :completed (:status result)))
                (try
                  (knowledge/ingest-session-turn! knowledge-system system owner (:turn-id data))
                  (catch Throwable _
                    ;; An intake outage cannot rewrite the settled conversation answer.
                    nil)))
              result)
    :stop (session/stop! system owner (:turn-id data))
    :compact (session/compact! system owner (:command-id data) data)
    :attach (session/attach! system owner (:task-id data))
    :detach (session/detach! system owner)
    :voice-ended (session/voice-ended! system owner)))

(defn- status [result]
  (case (:error result)
    :incorrect 400 :not-found 404 :forbidden 403 :busy 409 :conflict 409
    :approval-required 409 :rejected 409 :not-ready 409 200))

(defn- task-operation [task-system owner op data]
  (let [task-id (some-> (:task-id data) parse-uuid)]
    (case op
      "list" {:tasks (tasking/all-tasks task-system)}
      "submit" (tasking/submit! task-system owner
                                {:request-id (parse-uuid (:request-id data))
                                 :owner owner
                                 :instruction (:instruction data)
                                 :workspace (:workspace data)})
      "read" (let [task (task-life/task-state task-system task-id)]
                 (if (= (:owner task) (task-commands/canonical-owner owner))
                   task {:error "not-found"}))
      "owned-list" {:tasks (->> (tasking/all-tasks task-system)
                                (filter #(= (:owner %) (task-commands/canonical-owner owner)))
                                vec)}
      "abort" (tasking/abort! task-system owner task-id)
      "retry" (tasking/retry! task-system owner task-id)
      "owned-progress" (tasking/progress task-system owner task-id
                                         (long (or (:after data) 0))
                                         (long (or (:limit data) 100)))
      "dispatch" (tasking/dispatch! task-system task-id (:workspace data))
      "progress" (tasking/report-progress! task-system task-id (:summary data))
      "complete" (tasking/complete! task-system task-id (:result data))
      "fail" (tasking/fail! task-system task-id (:failure data))
      "interrupt" (tasking/interrupt! task-system task-id)
      {:error "not-found"})))

(defn start! [{:keys [system task-system knowledge-system song-system token port host] :or {port 0 host "127.0.0.1"}}]
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
                :else (let [result (run-operation system knowledge-system owner op (parse-body exchange))]
                        (send! exchange (status result) result))))
            (catch Throwable e
              (send! exchange (or (:status (ex-data e)) 400)
                     {:error "invalid request"}))))))
    (when task-system
      (.createContext server "/internal/task"
        (reify HttpHandler
          (handle [_ exchange]
            (try
              (let [auth (.getFirst (.getRequestHeaders exchange) "Authorization")
                    path (.getPath (.getRequestURI exchange))
                    op (last (str/split path #"/"))
                    owner (.getFirst (.getRequestHeaders exchange) "X-AWP-Owner")]
                (cond
                  (not= auth (str "Bearer " token)) (send! exchange 401 {:error "unauthorized"})
                  (not (contains? #{"list" "dispatch" "progress" "complete" "fail" "interrupt"
                                   "submit" "read" "owned-list" "abort" "retry" "owned-progress"} op))
                  (send! exchange 404 {:error "not-found"})
                  :else (let [result (task-operation task-system owner op (parse-body exchange))]
                          (send! exchange (cond
                                            (= ::anom/not-found (::anom/category result)) 404
                                            (= ::anom/forbidden (::anom/category result)) 403
                                            (= ::anom/incorrect (::anom/category result)) 400
                                            (or (:error result) (::anom/category result)) 409
                                            :else 200) result))))
              (catch Throwable _
                (send! exchange 400 {:error "invalid request"})))))))
    (when knowledge-system
      (.createContext server "/internal/knowledge"
        (reify HttpHandler
          (handle [_ exchange]
            (try
              (let [auth (.getFirst (.getRequestHeaders exchange) "Authorization")
                    owner (.getFirst (.getRequestHeaders exchange) "X-AWP-Owner")
                    op (last (str/split (.getPath (.getRequestURI exchange)) #"/"))]
                (cond
                  (not= auth (str "Bearer " token)) (send! exchange 401 {:error "unauthorized"})
                  (or (str/blank? owner) (> (count owner) 256)) (send! exchange 400 {:error "invalid owner"})
                  :else
                  (let [data (parse-body exchange)
                        result (case op
                                 "tasks" (knowledge/tasks knowledge-system owner (:source-id data))
                                 "suggestions" (knowledge/suggestions knowledge-system owner)
                                 "item" (knowledge/item knowledge-system owner (:item-id data))
                                 "profile" (knowledge/profile knowledge-system owner)
                                 "review" (knowledge/review! knowledge-system owner (:item-id data)
                                                              (-> (select-keys data [:action-id :expected-revision :text])
                                                                  (assoc :decision (get {"accept" :accepted "correct" :corrected
                                                                                 "reject" :rejected "accepted" :accepted
                                                                                 "corrected" :corrected "rejected" :rejected}
                                                                                (:decision data)))))
                                 "accounts" (knowledge/accounts knowledge-system owner)
                                 "account" (knowledge/account knowledge-system owner (:account-id data))
                                 "daily-request" (knowledge/daily-request! knowledge-system owner
                                                                            {:journal-date (:journal-date data)
                                                                             :time-zone (:time-zone data)
                                                                             :sources (mapv #(update % :speaker keyword) (:sources data))})
                                 "daily-worker-result" (knowledge/ingest-daily-worker-result! knowledge-system owner
                                                     (update data :sources (fn [sources] (mapv #(update % :speaker keyword) sources))))
                                 "worker-result" (knowledge/ingest-worker-result! knowledge-system owner
                                                   (update data :suggestions (fn [items] (mapv #(update % :kind keyword) items))))
                                 "daily-result" (knowledge/daily-result! knowledge-system owner (:account-id data)
                                                                          {:title (:title data) :story (:story data)
                                                                           :reason (:reason data)
                                                                           :sources (mapv #(update % :speaker keyword) (:sources data))})
                                 "outcome" (knowledge/outcome! knowledge-system owner (:source-id data)
                                                               (some-> (:mode data) keyword)
                                                               (some-> (:status data) keyword)
                                                               {:task-id (:task-id data) :reason (:reason data)
                                                                :suggestions (mapv #(update % :kind keyword) (:suggestions data))})
                                 {:error :not-found})]
                    (send! exchange (status result) result))))
              (catch Throwable _ (send! exchange 400 {:error "invalid request"})))))))
    (when song-system
      (.createContext server "/internal/song"
        (reify HttpHandler
          (handle [_ exchange]
            (try
              (let [auth (.getFirst (.getRequestHeaders exchange) "Authorization")
                    owner (.getFirst (.getRequestHeaders exchange) "X-AWP-Owner")
                    op (last (str/split (.getPath (.getRequestURI exchange)) #"/"))]
                (cond
                  (not= auth (str "Bearer " token)) (send! exchange 401 {:error "unauthorized"})
                  (or (str/blank? owner) (> (count owner) 256)) (send! exchange 400 {:error "invalid owner"})
                  :else
                  (let [data (parse-body exchange)
                        id (:generation-id data)
                        result (case op
                                 "create" (song/create! song-system owner data)
                                 "get" (song/get-song song-system owner id)
                                 "decide" (song/decide! song-system owner id
                                                        {:decision-id (:decision-id data)
                                                         :manifest-digest (:manifest-digest data)
                                                         :choice (some-> (:choice data) keyword)})
                                 "render" (song/render! song-system owner id)
                                 "cancel" (song/cancel! song-system owner id)
                                 "audio" (song/audio song-system owner id)
                                 "lyrics-result" (song/lyrics-result! song-system owner id
                                                                     (:lyrics-task-id data) (:manifest data))
                                 "render-result" (song/render-result! song-system owner id
                                                                       (:render-task-id data) (:artifact-id data))
                                 {:error :not-found})]
                    (if (bytes? result)
                      (send-audio! exchange result)
                      (send! exchange (status result) result)))))
              (catch Throwable _ (send! exchange 400 {:error "invalid request"})))))))
    (.setExecutor server (java.util.concurrent.Executors/newVirtualThreadPerTaskExecutor))
    (.start server)
    server))

(defn stop! [^HttpServer server] (.stop server 0))
(defn port [^HttpServer server] (.getPort (.getAddress server)))
