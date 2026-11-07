(ns awp-v2.live.main
  (:require [awp-v2.live.http :as http]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.gateway-adapter :as knowledge-gateway]
            [awp-v2.knowledge.native-worker :as native-worker]
            [awp-v2.knowledge.orc-adapter :as orc-adapter]
            [awp-v2.session.api :as session]
            [awp-v2.song.api :as song]
            [awp-v2.song.native-worker :as song-worker]
            [awp-v2.song.pi-durable :as pi-durable]
            [awp-v2.tasking.api :as tasking]
            [awp-v2.tasking.commands :as task-commands]
            [awp-v2.tasking.lifecycle :as lifecycle]
            [clojure.string :as str]))

(defn- required [name]
  (let [value (System/getenv name)]
    (when (str/blank? value)
      (throw (ex-info (str name " is required") {})))
    value))

(defn -main [& _]
  (let [workspaces (->> (str/split (or (System/getenv "AWP_ELIGIBLE_WORKSPACES") "") #",")
                        (map str/trim) (remove str/blank?) set)
        task-system (tasking/open-system {:storage-dir (required "AWP_TASKING_STORAGE_DIR")
                                          :eligible-workspaces workspaces})
        owned-task? (fn [owner task-id]
                      (try
                        (let [task (lifecycle/task-state task-system (parse-uuid task-id))]
                          (and task
                               (= (task-commands/canonical-owner owner) (:owner task))
                               (#{:pending :running :sleeping} (:status task))))
                        (catch Exception _ false)))
        knowledge-system (knowledge/open-system
                           {:storage-dir (or (System/getenv "AWP_KNOWLEDGE_STORAGE_DIR")
                                             (str (required "AWP_SESSION_STORAGE_DIR") "-knowledge"))
                            :after-write! (fn [system owner] (orc-adapter/sync-owner! system owner))
                            :run-worker! (when-let [script (System/getenv "AWP_KNOWLEDGE_WORKER_SCRIPT")]
                                           (native-worker/runner {:script script}))
                            :submit-task! (if (System/getenv "AWP_KNOWLEDGE_WORKER_SCRIPT")
                                            (fn [request] {:task-id (:request-id request)})
                                            (when (and (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_URL")
                                                     (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_TOKEN"))
                                            (knowledge-gateway/task-submitter
                                              {:base-url (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_URL")
                                               :token (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_TOKEN")})))})
        song-owner (when (and (System/getenv "AWP_PI_DURABLE_OWNER_URL")
                              (System/getenv "AWP_PI_DURABLE_OWNER_TOKEN_FILE"))
                     (pi-durable/client {:base-url (System/getenv "AWP_PI_DURABLE_OWNER_URL")
                                         :token-file (System/getenv "AWP_PI_DURABLE_OWNER_TOKEN_FILE")}))
        song-artifacts (or (System/getenv "AWP_SONG_ARTIFACT_DIR")
                           (str (required "AWP_SESSION_STORAGE_DIR") "-songs"))
        song-script (or (System/getenv "AWP_SONG_RENDER_SCRIPT")
                        "runtime/products/voice-agent/apps/pi-agent/dist/song-worker-cli.js")
        song-renderer (when (.isFile (java.io.File. song-script))
                        (song-worker/renderer {:script song-script :artifact-dir song-artifacts}))
        song-system (song/open-system
                      {:storage-dir (or (System/getenv "AWP_SONG_STORAGE_DIR")
                                        (str (required "AWP_SESSION_STORAGE_DIR") "-song"))
                       :eligible-workspaces workspaces
                       :submit-task! (fn [request]
                                       (case (:stage request)
                                         :lyrics (if song-owner (pi-durable/submit-lyrics! song-owner request)
                                                     (throw (ex-info "lyric owner unavailable" {})))
                                         :render (if song-renderer {:task-id (:task-id request)}
                                                     (throw (ex-info "song renderer unavailable" {})))))
                       :get-task! (when song-owner (fn [task-id] (pi-durable/get-task! song-owner task-id)))
                       :abort-task! (when song-owner (fn [{:keys [owner task-id]}]
                                                       (pi-durable/abort! song-owner task-id owner)))
                       :run-render! song-renderer
                       :get-audio! (song-worker/local-audio song-artifacts)})
        system (session/open-system
                 {:storage-dir (required "AWP_SESSION_STORAGE_DIR")
                  :owned-task? owned-task?})
        server (http/start! {:system system
                             :task-system task-system
                             :knowledge-system knowledge-system
                             :song-system song-system
                             :token (required "AWP_GRAIN_SESSION_TOKEN")
                             :port (Integer/parseInt (or (System/getenv "AWP_GRAIN_SESSION_PORT")
                                                         "9447"))})]
    (.addShutdownHook (Runtime/getRuntime)
      (Thread. (fn []
                 (http/stop! server)
                 (session/close-system! system)
                 (knowledge/close-system! knowledge-system)
                 (song/close-system! song-system)
                 (tasking/close-system! task-system))))
    (println (str "Grain session bridge listening on 127.0.0.1:" (http/port server)))
    @(promise)))
