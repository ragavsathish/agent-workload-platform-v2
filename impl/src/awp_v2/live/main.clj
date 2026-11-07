(ns awp-v2.live.main
  (:require [awp-v2.live.http :as http]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.gateway-adapter :as knowledge-gateway]
            [awp-v2.session.api :as session]
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
                            :submit-task! (when (and (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_URL")
                                                     (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_TOKEN"))
                                            (knowledge-gateway/task-submitter
                                              {:base-url (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_URL")
                                               :token (System/getenv "AWP_LEGACY_KNOWLEDGE_GATEWAY_TOKEN")}))})
        system (session/open-system
                 {:storage-dir (required "AWP_SESSION_STORAGE_DIR")
                  :owned-task? owned-task?})
        server (http/start! {:system system
                             :task-system task-system
                             :knowledge-system knowledge-system
                             :token (required "AWP_GRAIN_SESSION_TOKEN")
                             :port (Integer/parseInt (or (System/getenv "AWP_GRAIN_SESSION_PORT")
                                                         "9447"))})]
    (.addShutdownHook (Runtime/getRuntime)
      (Thread. (fn []
                 (http/stop! server)
                 (session/close-system! system)
                 (knowledge/close-system! knowledge-system)
                 (tasking/close-system! task-system))))
    (println (str "Grain session bridge listening on 127.0.0.1:" (http/port server)))
    @(promise)))
