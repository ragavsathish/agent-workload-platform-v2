(ns awp-v2.workbench-acceptance-test
  (:require [clojure.test :refer :all]
            [awp-v2.knowledge.api :as knowledge])
  (:import [java.nio.file Files]))

(def owner "owner-workbench@example.test")
(def foreign "foreign-workbench@example.test")
(def reviewer "reviewer-workbench@example.test")
(def adjudicator "adjudicator-workbench@example.test")
(defn opts [dir]
  {:storage-dir (.getPath dir)
   :workbench-review-authorized? (fn [actor case-owner]
                                    (and (= owner case-owner) (#{reviewer adjudicator} actor)))})
(defn with-system [f]
  (let [dir (.toFile (Files/createTempDirectory "awp-v2-workbench-"
                                                (make-array java.nio.file.attribute.FileAttribute 0)))
        sys (knowledge/open-system (opts dir))]
    (try (f sys dir) (finally (knowledge/close-system! sys)))))
(def case-data {:case-id "case-1" :source-id "opaque-source" :source-digest "source-digest" :branch :journal})
(def metadata-data {:snapshot-id "snapshot-1" :snapshot-digest "digest-1"
                    :legacy-id "opaque-metadata" :resource-digest "resource-digest"})
(defn command [sys actor op data]
  (knowledge/workbench-command! sys actor owner op data))
(defn action [id revision extra]
  (merge {:action-id id :expected-revision revision :at "2026-11-08T00:00:00Z"} extra))

(deftest workbench-001-grain-pending-inventory
  (with-system (fn [sys _]
    (is (= :registered (:status (command sys owner :workbench-case case-data))))
    (is (= :registered (:status (command sys owner :workbench-metadata metadata-data))))
    (let [view (knowledge/workbench-view sys owner)]
      (is (= :pending (-> view :cases first :review-status)))
      (is (= :pending (-> view :cases first :privacy-status)))
      (is (= :quarantined (-> view :metadata first :disposition)))
      (is (empty? (knowledge/taxonomy-current sys owner)))))))

(deftest workbench-002-retry-and-restart
  (with-system (fn [sys dir]
    (command sys owner :workbench-case case-data)
    (let [data (action "family-1" 0 {:case-id "case-1" :family-id "family-1" :split :development})]
      (is (= :recorded (:status (command sys owner :workbench-family data))))
      (is (= :reused (:status (command sys owner :workbench-family (assoc data :at "later"))))))
    (knowledge/close-system! sys)
    (let [again (knowledge/open-system (opts dir))]
      (try (is (= 1 (-> (knowledge/workbench-view again owner) :cases first :revision)))
           (finally (knowledge/close-system! again)))))))

(deftest workbench-006-distinct-adjudicator
  (with-system (fn [sys _]
    (command sys owner :workbench-case case-data)
    (is (= :not-found (:error (command sys owner :workbench-review
        (action "self" 0 {:case-id "case-1" :decision :approved :reviewer-role :independent
                           :expected-digest "expected"})))))
    (is (= :recorded (:status (command sys reviewer :workbench-review
        (action "dispute" 0 {:case-id "case-1" :decision :disputed :reviewer-role :independent
                              :expected-digest "provisional"})))))
    (is (= :conflict (:error (command sys reviewer :workbench-review
        (action "same-reviewer" 1 {:case-id "case-1" :decision :adjudicated
                                    :reviewer-role :adjudicator :expected-digest "final"})))))
    (is (= :recorded (:status (command sys adjudicator :workbench-review
        (action "different-reviewer" 1 {:case-id "case-1" :decision :adjudicated
                                         :reviewer-role :adjudicator :expected-digest "final"})))))
    (is (= 2 (count (-> (knowledge/workbench-view sys owner) :cases first :history)))))))

(deftest workbench-007-family-cannot-cross-split
  (with-system (fn [sys _]
    (command sys owner :workbench-case case-data)
    (command sys owner :workbench-case (assoc case-data :case-id "case-2" :source-id "source-2"))
    (is (= :recorded (:status (command sys owner :workbench-family
      (action "first" 0 {:case-id "case-1" :family-id "family" :split :development})))))
    (is (= :conflict (:error (command sys owner :workbench-family
      (action "second" 0 {:case-id "case-2" :family-id "family" :split :challenge}))))))))

(deftest workbench-010-disposition-preserves-opaque-evidence
  (with-system (fn [sys _]
    (command sys owner :workbench-metadata metadata-data)
    (is (= :recorded (:status (command sys owner :workbench-disposition
      (action "exclude" 0 {:snapshot-id "snapshot-1" :snapshot-digest "digest-1"
                           :legacy-id "opaque-metadata" :disposition :excluded-metadata
                           :source-id "" :reason-digest "reason-digest" :evidence-digest "evidence-digest"
                           :verified-link-digest ""})))))
    (let [record (-> (knowledge/workbench-view sys owner) :metadata first)]
      (is (= :excluded-metadata (:disposition record)))
      (is (= "evidence-digest" (:evidence-digest record)))
      (is (= 1 (:revision record)))))))

(deftest workbench-011-owner-and-revision-guard
  (with-system (fn [sys _]
    (command sys owner :workbench-case case-data)
    (is (= :not-found (:error (command sys foreign :workbench-family
      (action "foreign" 0 {:case-id "case-1" :family-id "family" :split :development})))))
    (is (empty? (:cases (knowledge/workbench-view sys foreign))))
    (command sys owner :workbench-family
      (action "first" 0 {:case-id "case-1" :family-id "family" :split :development}))
    (is (= :conflict (:error (command sys owner :workbench-family
      (action "stale" 0 {:case-id "case-1" :family-id "other" :split :development}))))))))

(deftest workbench-012-replay-keeps-review-out-of-current-knowledge
  (with-system (fn [sys dir]
    (command sys owner :workbench-case case-data)
    (command sys reviewer :workbench-review
      (action "review" 0 {:case-id "case-1" :decision :approved :reviewer-role :independent
                          :expected-digest "expected"}))
    (let [before (knowledge/workbench-view sys owner)]
      (knowledge/close-system! sys)
      (let [again (knowledge/open-system (opts dir))]
        (try (is (= before (knowledge/workbench-view again owner)))
             (is (empty? (knowledge/taxonomy-current again owner)))
             (is (empty? (knowledge/profile again owner)))
             (finally (knowledge/close-system! again))))))))

(deftest workbench-013-no-holdout-or-live-choice
  (with-system (fn [sys _]
    (command sys owner :workbench-case case-data)
    (command sys owner :workbench-metadata metadata-data)
    (let [view (knowledge/workbench-view sys owner)]
      (is (= 1 (count (:cases view))))
      (is (= 1 (count (:metadata view))))
      (is (= :not-found (:error (knowledge/quality-run sys owner "no-run"))))
      (is (empty? (knowledge/profile sys owner)))))))
