(ns awp-v2.qualify.task-seed
  (:require [awp-v2.tasking.api :as tasking]
            [cognitect.anomalies :as anom]))

(defn -main [& _]
  (let [owner (System/getenv "AWP_OWNER_IDENTITY")
        id (parse-uuid (System/getenv "AWP_QUALIFICATION_TASK_ID"))
        storage (System/getenv "AWP_TASKING_STORAGE_DIR")
        system (tasking/open-system {:storage-dir storage
                                    :eligible-workspaces #{"scratch"}})]
    (try
      (let [accepted (tasking/submit! system owner
                                      {:request-id id :owner owner
                                       :instruction "Start a durable task conversation."
                                       :workspace "scratch"})
            dispatched (tasking/dispatch! system id "scratch")]
        (when (or (::anom/category accepted) (::anom/category dispatched)
                  (not= :running (:status dispatched)))
          (throw (ex-info "qualification task admission failed"
                          {:accepted accepted :dispatched dispatched}))))
      (finally
        (tasking/close-system! system)))))
