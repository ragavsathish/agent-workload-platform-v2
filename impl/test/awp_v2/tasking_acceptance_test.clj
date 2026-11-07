(ns awp-v2.tasking-acceptance-test
  (:require [awp-v2.tasking.api :as tasking]
            [clojure.test :refer [deftest is testing]]
            [cognitect.anomalies :as anom]))

(def alice "alice@example.test")
(def bob "bob@example.test")
(def request-id #uuid "e58c1eca-b5b9-49e2-a329-20a7902f1ac4")

(defn request
  ([] (request request-id))
  ([id] {:request-id id :owner alice :instruction "Review change"
         :workspace "project-a"}))

(defn with-system [f]
  (let [system (tasking/open-system {:eligible-workspaces #{"project-a"}})]
    (try (f system) (finally (tasking/close-system! system)))))

(deftest admission-001-eligible-request-is-accepted-and-visible
  (with-system
    (fn [system]
      (let [result (tasking/submit! system alice (request))
            task (tasking/task system alice request-id)]
        (is (= request-id (:task-id result)))
        (is (= request-id (:task-id task)))
        (is (= "Review change" (:instruction task)))
        (is (= 1 (tasking/accepted-count system)))))))

(deftest admission-002-retry-after-lost-response-reuses-task
  (with-system
    (fn [system]
      (tasking/submit! system alice (request))
      (let [result (tasking/submit! system alice
                                    (assoc (request) :instruction " Review change "))]
        (is (= request-id (:task-id result)))
        (is (true? (:reused? result)))
        (is (= 1 (tasking/accepted-count system)))))))

(deftest admission-003-conflicting-reuse-is-refused
  (with-system
    (fn [system]
      (tasking/submit! system alice (request))
      (let [result (tasking/submit! system alice
                                    (assoc (request) :instruction "Different work"))]
        (is (= ::anom/conflict (::anom/category result)))
        (is (= "Review change" (:instruction (tasking/task system alice request-id))))
        (is (= 1 (tasking/accepted-count system)))))))

(deftest admission-004-ineligible-work-is-refused
  (with-system
    (fn [system]
      (doseq [bad [(assoc (request) :instruction " ")
                   (assoc (request) :workspace "elsewhere")]]
        (is (= ::anom/incorrect
               (::anom/category (tasking/submit! system alice bad)))))
      (is (zero? (tasking/accepted-count system))))))

(deftest admission-005-owners-cannot-cross-boundaries
  (with-system
    (fn [system]
      (tasking/submit! system alice (request))
      (is (= ::anom/forbidden
             (::anom/category (tasking/submit! system bob (request)))))
      (let [result (tasking/task system bob request-id)]
        (is (= ::anom/not-found (::anom/category result)))
        (is (nil? (:instruction result))))
      (is (= 1 (tasking/accepted-count system))))))

(deftest admission-006-simultaneous-retries-admit-one-task
  (with-system
    (fn [system]
      (let [gate (promise)
            calls (doall (repeatedly 2
                                    #(future @gate
                                             (tasking/submit! system alice (request)))))]
        (deliver gate true)
        (is (= [request-id request-id]
               (mapv :task-id (mapv deref calls))))
        (is (= 1 (tasking/accepted-count system)))))))
