(ns awp-v2.incident.probe
  (:require [awp-v2.incident.api :as incident]
            [awp-v2.tasking.api :as tasking]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [clojure.java.io :as io]))

(def owner "incident-probe@example.test")
(def alert {:state "Alerting" :fingerprint "probe-alert"
            :labels {:alertname "Probe alert" :rule_uid "awp_probe"
                     :auto_investigate "true" :managed_by "agent-workload-platform"}})
(def rules {"awp_probe" {:title "Probe alert" :promql "up == 0" :summary "Probe metric is down."}})
(defn -main [phase dir]
  (let [tasks (tasking/open-system {:storage-dir (str (io/file dir "tasks"))
                                    :eligible-workspaces #{"awp"}})
        submit (fn [request]
                 (let [accepted (tasking/submit! tasks owner request)]
                   (if (= phase "create")
                     (throw (ex-info "simulated lost acknowledgement" {}))
                     accepted)))
        system (incident/open-system {:storage-dir (str (io/file dir "incidents"))
                                       :task-system tasks :rules rules :workspace "awp"
                                       :submit-task! submit
                                       :metric-fetch! (fn [_] {:status "success" :data {:result []}})})]
    (try
      (case phase
        "create"
        (do
          (incident/observe! system owner 1000 [alert])
          (let [row (first (incident/incidents system owner))]
            (assert (= :pending (:submission-status row)))
            (assert (= 1 (tasking/accepted-count tasks)))
            (spit (io/file dir "epoch-id") (:epoch-id row))))
        "verify"
        (do
          (incident/recover! system)
          (rmp/l1-clear!)
          (let [row (incident/incident system owner (slurp (io/file dir "epoch-id")))]
            (assert (= :accepted (:submission-status row)))
            (assert (= 1 (tasking/accepted-count tasks)))
            (assert (= :not-found (:error (incident/incident system "foreign@example.test" (:epoch-id row)))))
            (incident/observe! system owner 1060 [])
            (rmp/l1-clear!)
            (assert (false? (:active? (incident/incident system owner (:epoch-id row)))))
            (println "incident cross-process restart probe OK")))
        (throw (ex-info "unknown probe phase" {:phase phase})))
      (finally (incident/close-system! system) (tasking/close-system! tasks)))))
