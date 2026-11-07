(ns awp-v2.lifecycle-acceptance-test
  (:require [awp-v2.tasking.api :as tasking]
            [awp-v2.tasking.controlled-worker :as worker]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]))

(def owner "owner@example.test")
(def other "other@example.test")
(def task-id #uuid "4b860912-01a3-455b-9fa4-7a71c93500ee")
(def tenant-id #uuid "9b0fedbc-c8d7-4563-90bc-22ed52daf065")

(defn- temp-dir []
  (.toFile (Files/createTempDirectory "awp-v2-lifecycle-"
                                     (make-array java.nio.file.attribute.FileAttribute 0))))

(defn- remove-tree! [dir]
  (doseq [f (reverse (file-seq dir))]
    (io/delete-file f true)))

(defn- options [dir]
  {:storage-dir (.getPath dir)
   :tenant-id tenant-id
   :eligible-workspaces #{"project-a" "project-b"}
   :workspace-roots {"project-a" (str (io/file dir "project-a"))
                     "project-b" (str (io/file dir "project-b"))}})

(defn- request []
  {:request-id task-id :owner owner :instruction "Write proof"
   :workspace "project-a"})

(defn- with-system [f]
  (let [dir (temp-dir)]
    (try
      (.mkdirs (io/file dir "project-a"))
      (.mkdirs (io/file dir "project-b"))
      (let [system (tasking/open-system (options dir))]
        (try (f dir system)
             (finally (tasking/close-system! system))))
      (finally (remove-tree! dir)))))

(defn- accepted [system]
  (tasking/submit! system owner (request)))

(deftest lifecycle-001-accepted-work-starts-with-selected-capability
  (with-system
    (fn [dir system]
      (accepted system)
      (let [session (worker/start! system task-id "project-a")]
        (is (= :running (:status (tasking/task system owner task-id))))
        (is (= task-id (:task-id session)))
        (is (= ::anom/forbidden
               (::anom/category (worker/write! session "project-b" "foreign.txt" "wrong"))))
        (is (not (.exists (io/file dir "project-b" "foreign.txt"))))
        (is (= :written (:status (worker/write! session "project-a" "proof.txt" "right"))))
        (is (= "right" (slurp (io/file dir "project-a" "proof.txt"))))))))

(deftest lifecycle-002-progress-is-bounded-cursor-addressable-and-private
  (with-system
    (fn [_ system]
      (accepted system)
      (let [session (worker/start! system task-id "project-a")]
        (worker/progress! session "Preparing the workspace.")
        (worker/progress! session "token=supersecret private reasoning: hidden")
        (worker/progress! session (apply str (repeat 400 "x")))
        (let [first-page (deref (future (tasking/progress system owner task-id 0 2)) 1000 ::timeout)
              later (tasking/progress system owner task-id 2 10)]
          (is (not= ::timeout first-page))
          (is (= [1 2] (mapv :cursor first-page)))
          (is (= [3] (mapv :cursor later)))
          (is (every? #(<= (count (:summary %)) 240) (concat first-page later)))
          (is (not (re-find #"supersecret|reasoning" (pr-str first-page)))))))))

(deftest lifecycle-003-successful-result-survives-fresh-start
  (with-system
    (fn [dir system]
      (accepted system)
      (let [session (worker/start! system task-id "project-a")]
        (worker/complete! session {:text "done"}))
      (is (= :completed (:status (tasking/task system owner task-id))))
      (tasking/close-system! system)
      (let [reopened (tasking/open-system (options dir))]
        (try
          (is (= {:text "done"} (:result (tasking/task reopened owner task-id))))
          (is (= :completed (:status (tasking/task reopened owner task-id))))
          (finally (tasking/close-system! reopened)))))))

(deftest lifecycle-004-failure-survives-fresh-start
  (with-system
    (fn [dir system]
      (accepted system)
      (worker/fail! (worker/start! system task-id "project-a") "worker failed")
      (tasking/close-system! system)
      (let [reopened (tasking/open-system (options dir))]
        (try
          (let [task (tasking/task reopened owner task-id)]
            (is (= :failed (:status task)))
            (is (= "worker failed" (:failure task)))
            (is (nil? (:result task))))
          (finally (tasking/close-system! reopened)))))))

(deftest lifecycle-005-abort-is-terminal-despite-late-result
  (with-system
    (fn [_ system]
      (accepted system)
      (let [session (worker/start! system task-id "project-a")]
        (is (= :cancelled (:status (tasking/abort! system owner task-id))))
        (worker/complete! session {:text "too late"})
        (let [task (tasking/task system owner task-id)]
          (is (= :cancelled (:status task)))
          (is (nil? (:result task))))))))

(deftest lifecycle-006-uncertain-dispatch-retains-identity-after-fresh-start
  (with-system
    (fn [dir system]
      (accepted system)
      (tasking/close-system! system)
      (let [reopened (tasking/open-system (options dir))]
        (try
          (is (true? (:reused? (tasking/submit! reopened owner (request)))))
          (is (= task-id (:task-id (worker/start! reopened task-id "project-a"))))
          (is (= task-id (:task-id (worker/start! reopened task-id "project-a"))))
          (is (= 1 (tasking/accepted-count reopened)))
          (finally (tasking/close-system! reopened)))))))

(deftest lifecycle-007-parked-task-retries-with-prior-progress
  (with-system
    (fn [_ system]
      (accepted system)
      (let [session (worker/start! system task-id "project-a")]
        (worker/progress! session "Before interruption.")
        (worker/interrupt! session)
        (is (= :pending (:status (tasking/task system owner task-id))))
        (is (true? (:recovery-required? (tasking/task system owner task-id))))
        (is (= task-id (:task-id (tasking/retry! system owner task-id))))
        (is (= :running (:status (worker/start! system task-id "project-a"))))
        (is (= [1] (mapv :cursor (tasking/progress system owner task-id 0 10))))))))

(deftest lifecycle-008-other-owner-cannot-observe-or-control
  (with-system
    (fn [_ system]
      (accepted system)
      (worker/start! system task-id "project-a")
      (doseq [result [(tasking/task system other task-id)
                      (tasking/progress system other task-id 0 10)
                      (tasking/abort! system other task-id)
                      (tasking/retry! system other task-id)]]
        (is (= ::anom/not-found (::anom/category result)))
        (is (nil? (:result result)))))))
