(ns awp-v2.knowledge-quality-acceptance-test
  (:require [clojure.test :refer :all]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.quality-ledger-gate :as ledger])
  (:import [java.nio.file Files]))

(def owner "owner-quality@example.test")
(def other "other-quality@example.test")
(def reviewer "independent-reviewer@example.test")
(def adjudicator "adjudicator@example.test")
(defn options [dir] {:storage-dir (.getPath dir)
                     :quality-review-authorized? (fn [actor case-owner]
                                                   (and (#{reviewer adjudicator} actor) (= case-owner owner)))
                     :quality-gate-authorized? (fn [actor run-id]
                                                 (and (= actor owner) (= run-id "run-1")))})
(defn with-system [f]
  (let [dir (.toFile (Files/createTempDirectory "awp-v2-quality-"
                                              (make-array java.nio.file.attribute.FileAttribute 0)))
        sys (knowledge/open-system (options dir))]
    (try (f sys dir) (finally (knowledge/close-system! sys)))))
(def case-data {:case-id "case-1" :source-id "source-1" :branch :journal
                :family-id "family-1" :split :locked_holdout :source-digest "source-digest"})
(def run-data {:run-id "run-1" :policy-digest "policy-digest" :corpus-digest "corpus-digest"
               :baseline-id "incumbent" :candidate-id "candidate" :rollback-id "incumbent"
               :config-digest "config-digest" :split :locked_holdout :case-ids ["case-1"]})

(deftest quality-002-grain-review-does-not-publish
  (with-system (fn [sys _]
    (is (= :registered (:status (knowledge/quality-register-case! sys owner case-data))))
    (is (= :not-found (:error (knowledge/quality-review-case! sys owner owner
                                {:case-id "case-1" :action-id "review-1" :decision :approved
                                 :privacy-status :passed :expected-digest "gold-digest"}))))
    (is (= :approved (:status (knowledge/quality-review-case! sys reviewer owner
                                {:case-id "case-1" :action-id "review-1" :decision :approved
                                 :privacy-status :passed :expected-digest "gold-digest"}))))
    (is (= :approved (:review-status (first (knowledge/quality-cases sys owner)))))
    (is (empty? (knowledge/taxonomy-current sys owner))))))

(deftest quality-003-family-split-conflict
  (with-system (fn [sys _]
    (is (= :registered (:status (knowledge/quality-register-case! sys owner case-data))))
    (is (= :conflict (:error (knowledge/quality-register-case! sys owner
                                   (assoc case-data :case-id "case-2" :split :development))))))))

(deftest quality-007-frozen-run-and-holdout-once
  (with-system (fn [sys _]
    (knowledge/quality-register-case! sys owner case-data)
    (is (= :incorrect (:error (knowledge/quality-start-run! sys owner run-data))))
    (knowledge/quality-review-case! sys reviewer owner
      {:case-id "case-1" :action-id "review-1" :decision :approved
       :privacy-status :passed :expected-digest "gold-digest"})
    (is (= :prepared (:status (knowledge/quality-start-run! sys owner run-data))))
    (is (= :consumed (:status (knowledge/quality-consume-holdout! sys owner "run-1"))))
    (is (= :conflict (:error (knowledge/quality-consume-holdout! sys owner "run-1"))))
    (is (= :passed (:status (knowledge/quality-record-verdict! sys owner "run-1" :passed "metrics-digest"))))
    (is (= "incumbent" (:rollback-id (knowledge/quality-run sys owner "run-1")))))))

(deftest quality-008-restart-and-owner-isolation
  (with-system (fn [sys dir]
    (knowledge/quality-register-case! sys owner case-data)
    (knowledge/quality-review-case! sys reviewer owner
      {:case-id "case-1" :action-id "review-1" :decision :approved
       :privacy-status :passed :expected-digest "gold-digest"})
    (is (empty? (knowledge/quality-cases sys other)))
    (knowledge/close-system! sys)
    (let [reopened (knowledge/open-system (options dir))]
      (try
        (is (= :approved (:review-status (first (knowledge/quality-cases reopened owner)))))
        (is (empty? (knowledge/quality-cases reopened other)))
        (is (empty? (knowledge/taxonomy-current reopened owner)))
        (finally (knowledge/close-system! reopened)))))))

(deftest quality-007-runner-verifies-grain-review-and-freeze
  (with-system (fn [sys _]
    (knowledge/quality-register-case! sys owner case-data)
    (knowledge/quality-review-case! sys reviewer owner
      {:case-id "case-1" :action-id "review-1" :decision :approved
       :privacy-status :passed :expected-digest "gold-digest"})
    (knowledge/quality-start-run! sys owner run-data)
    (let [request {"owner" owner "runId" "run-1" "partition" "locked_holdout"
                   "policyDigest" "policy-digest" "corpusDigest" "corpus-digest"
                   "configDigest" "config-digest" "baselineId" "incumbent"
                   "candidateId" "candidate" "rollbackId" "incumbent"
                   "cases" [{"id" "case-1" "sourceDigest" "source-digest"
                              "expectedDigest" "gold-digest"}]}]
      (is (= "open" (:status (ledger/verify-one sys
                          (assoc request "cases" [{"id" "case-1" "sourceDigest" "wrong"
                                                   "expectedDigest" "gold-digest"}])))))
      (is (= "ready" (:status (ledger/verify-one sys request))))
      (is (= "open" (:status (ledger/verify-one sys request))))))))

(deftest quality-002-dispute-requires-distinct-adjudicator
  (with-system (fn [sys _]
    (knowledge/quality-register-case! sys owner case-data)
    (is (= :disputed (:status (knowledge/quality-review-case! sys reviewer owner
      {:case-id "case-1" :action-id "dispute-1" :decision :disputed
       :privacy-status :passed :expected-digest "provisional"}))))
    (is (= :incorrect (:error (knowledge/quality-start-run! sys owner run-data))))
    (is (= :conflict (:error (knowledge/quality-review-case! sys reviewer owner
      {:case-id "case-1" :action-id "self-adjudicate" :decision :adjudicated
       :reviewer-role :adjudicator :privacy-status :passed :expected-digest "final"}))))
    (is (= :adjudicated (:status (knowledge/quality-review-case! sys adjudicator owner
      {:case-id "case-1" :action-id "adjudicate-1" :decision :adjudicated
       :reviewer-role :adjudicator :privacy-status :passed :expected-digest "final"}))))
    (is (= 2 (count (:review-history (first (knowledge/quality-cases sys owner))))))
    (is (= :prepared (:status (knowledge/quality-start-run! sys owner run-data)))))))
