(ns awp-v2.deployment-acceptance-test
  (:require [clojure.test :refer :all]
            [awp-v2.deployment.api :as deployment]
            [awp-v2.tasking.api :as tasking]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]))

(deftest deployment-001-omitted-level-defaults-to-full
  (let [tasks (tasking/open-system {:eligible-workspaces #{"scratch"}})]
    (try
      (is (= "full" (:deployment-level tasks)))
      (finally (tasking/close-system! tasks)))))

(deftest deployment-002-voice-refuses-before-task-acceptance
  (let [tasks (tasking/open-system {:eligible-workspaces #{"scratch"} :deployment-level "voice"})]
    (try
      (let [result (tasking/submit! tasks "owner@example.test"
                    {:request-id (random-uuid) :workspace "scratch" :instruction "run"})]
        (is (= "durable task execution unavailable at voice level" (:cognitect.anomalies/message result)))
        (is (zero? (tasking/accepted-count tasks))))
      (finally (tasking/close-system! tasks)))))

(deftest deployment-005-changing-level-does-not-change-accepted-task-identity
  (let [tasks (tasking/open-system {:eligible-workspaces #{"project-a"}})
        sys (deployment/open-system {})
        request-id #uuid "11111111-1111-4111-8111-111111111111"]
    (try
      (tasking/submit! tasks "owner@example.test"
        {:request-id request-id :owner "owner@example.test" :workspace "project-a" :instruction "Review"})
      (deployment/select! sys "s1" "durable")
      (deployment/select! sys "s2" "full")
      (is (= request-id (:task-id (tasking/task tasks "owner@example.test" request-id))))
      (is (= 1 (tasking/accepted-count tasks)))
      (finally (deployment/close-system! sys) (tasking/close-system! tasks)))))

(deftest deployment-006-interrupted-task-keeps-identity-until-explicit-recovery
  (let [tasks (tasking/open-system {:eligible-workspaces #{"project-a"}})
        request-id #uuid "22222222-2222-4222-8222-222222222222"]
    (try
      (tasking/submit! tasks "owner@example.test"
        {:request-id request-id :owner "owner@example.test" :workspace "project-a" :instruction "Review"})
      (tasking/dispatch! tasks request-id "project-a")
      (tasking/interrupt! tasks request-id)
      (let [task (tasking/task tasks "owner@example.test" request-id)]
        (is (= request-id (:task-id task)))
        (is (:recovery-required? task))
        (is (= :pending (:status task))))
      (finally (tasking/close-system! tasks)))))

(deftest deployment-007-selection-is-replay-safe-and-status-separates-requested-from-active
  (let [dir (.toString (java.nio.file.Files/createTempDirectory
                        "awp-deployment-test-"
                        (make-array java.nio.file.attribute.FileAttribute 0)))
        sys (deployment/open-system {:storage-dir dir :owner "owner@example.test"})]
    (try
      (is (= :selected (:status (deployment/select! sys "selection-1" "durable"))))
      (is (= :reused (:status (deployment/select! sys "selection-1" "durable"))))
      (is (= :conflict (:error (deployment/select! sys "selection-1" "full"))))
      (deployment/observe! sys "durable" {"grain" true "pi" true "broker" true
                                           "gateway" true "scheduler" true} "ready")
      (rmp/l1-clear!)
      (let [status (deployment/status sys)]
        (is (= :durable (:requested-level status)))
        (is (= :durable (:active-level status)))
        (is (= :ready (:readiness status))))
      (deployment/close-system! sys)
      (rmp/l1-clear!)
      (let [reopened (deployment/open-system {:storage-dir dir :owner "owner@example.test"})]
        (try
          (is (= :durable (:requested-level (deployment/status reopened))))
          (is (= :reused (:status (deployment/select! reopened "selection-1" "durable"))))
          (finally (deployment/close-system! reopened))))
      (finally (deployment/close-system! sys)))))

(deftest deployment-008-missing-service-cannot-report-ready
  (let [sys (deployment/open-system {})]
    (try
      (deployment/select! sys "selection-ready" "durable")
      (deployment/observe! sys "durable" {"grain" true "pi" true "broker" true
                                           "gateway" true "scheduler" false} "ready")
      (rmp/l1-clear!)
      (is (= :degraded (:readiness (deployment/status sys))))
      (finally (deployment/close-system! sys)))))
