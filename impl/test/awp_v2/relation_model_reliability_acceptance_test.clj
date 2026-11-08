(ns awp-v2.relation-model-reliability-acceptance-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [ai.obney.orc.orc-service.interface :as sheet]
            [ai.obney.grain.control-plane.interface :as control-plane]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.orc-adapter :as orc]
            [awp-v2.grounded-relations-acceptance-test :as fixture]
            [litellm.router :as router])
  (:import [java.nio.file Files]))

(defn call! [sys actor response attempt-id]
  (orc/propose-relations! (assoc sys :relation-model! (fn [_] response)) actor
                          "relation:turn" "model-1" "prompt-1" "subject-v1"
                          ["event" "design"] {:attempt-id attempt-id}))

(def grounded {:status :completed :trace-id "model-trace"
               :candidates [{:left-id "event" :right-id "design" :kind "broader"
                             :evidence "Event modeling is a kind of software design."}]})

(deftest reliability-001-failure-safe-owner-outcome
  (fixture/with-system (fn [sys _]
    (fixture/setup-concepts! sys fixture/owner)
    (let [result (call! sys fixture/owner
                        {:status :failed :trace-id "failed-trace" :failure-category :provider-error
                         :candidates [] :diagnostic {:error "PRIVATE provider response"}} "first")
          outcome (first (knowledge/taxonomy-relation-outcomes sys fixture/owner))]
      (is (= :failed (:status result)))
      (is (= :provider-error (:failure-category outcome)))
      (is (= "failed-trace" (:trace-id outcome)))
      (is (not (.contains (pr-str outcome) "PRIVATE")))
      (is (empty? (knowledge/taxonomy-relation-outcomes sys fixture/other)))
      (is (empty? (knowledge/taxonomy-relation-proposals sys fixture/owner)))
      (is (empty? (knowledge/taxonomy-relations sys fixture/owner)))))))

(deftest reliability-002-completed-candidate-is-pending
  (fixture/with-system (fn [sys _]
    (fixture/setup-concepts! sys fixture/owner)
    (let [result (call! sys fixture/owner grounded "completed")
          outcome (first (knowledge/taxonomy-relation-outcomes sys fixture/owner))
          pending (first (knowledge/taxonomy-relation-proposals sys fixture/owner))]
      (is (= :staged (:status result)))
      (is (= :completed (:status outcome)))
      (is (= ["event" "design"] (:endpoint-ids outcome)))
      (is (= [1 1] (:endpoint-revisions outcome)))
      (is (= :pending (:status pending)))
      (is (= (:result-id outcome) (:result-id pending)))
      (is (= "model-trace" (:trace-id pending)))
      (is (string? (:source-digest pending)))
      (is (empty? (knowledge/taxonomy-relations sys fixture/owner)))))))

(deftest reliability-003-explicit-retry-preserves-failed-history
  (fixture/with-system (fn [sys _]
    (fixture/setup-concepts! sys fixture/owner)
    (let [failed (call! sys fixture/owner {:status :failed :trace-id "first-trace"
                                           :failure-category :provider-error :candidates []} "attempt-1")
          staged (call! sys fixture/owner grounded "attempt-2")
          outcomes (knowledge/taxonomy-relation-outcomes sys fixture/owner)]
      (is (= :failed (:status failed)))
      (is (= :staged (:status staged)))
      (is (not= (:result-id failed) (:result-id staged)))
      (is (= #{:failed :completed} (set (map :status outcomes))))
      (is (= [:failed :completed] (mapv :status outcomes)))
      (is (= 1 (count (knowledge/taxonomy-relation-proposals sys fixture/owner))))))))

(deftest reliability-004-same-identity-replay-does-not-invoke-model
  (fixture/with-system (fn [sys _]
    (fixture/setup-concepts! sys fixture/owner)
    (let [calls (atom 0)
          model-system (assoc sys :relation-model! (fn [_] (swap! calls inc) grounded))
          args [model-system fixture/owner "relation:turn" "model-1" "prompt-1"
                "subject-v1" ["event" "design"] {:attempt-id "same"}]]
      (is (= :staged (:status (apply orc/propose-relations! args))))
      (is (= :reused (:status (apply orc/propose-relations! args))))
      (is (= 1 @calls))
      (is (= 1 (count (knowledge/taxonomy-relation-proposals sys fixture/owner))))
      (is (= :conflict (:error (orc/propose-relations! model-system fixture/owner
                                              "relation:turn" "changed-model" "prompt-1"
                                              "subject-v1" ["event" "design"]
                                              {:attempt-id "same"})))))))
  (fixture/with-system (fn [sys _]
    (fixture/setup-concepts! sys fixture/owner)
    (let [calls (atom 0)
          model-system (assoc sys :relation-model! (fn [_] (swap! calls inc)
                        {:status :failed :trace-id "failed-trace"
                         :failure-category :provider-error :candidates []}))
          args [model-system fixture/owner "relation:turn" "model-1" "prompt-1"
                "subject-v1" ["event" "design"] {:attempt-id "failed-same"}]]
      (is (= :failed (:status (apply orc/propose-relations! args))))
      (is (= :failed (:status (apply orc/propose-relations! args))))
      (is (= 1 @calls))
      (is (= 1 (count (knowledge/taxonomy-relation-outcomes sys fixture/owner))))))))

(deftest reliability-005-empty-invalid-and-execution-are-distinct
  (doseq [[response expected category]
          [[{:status :empty :trace-id "empty" :candidates []} :empty :none]
           [{:status :completed :trace-id "invalid" :candidates [{:kind "causes"}]} :invalid :invalid-candidate]
           [{:status :timeout :trace-id "timeout" :candidates []} :timeout :timeout]
           [{:status :failed :trace-id "failed" :failure-category :output-schema :candidates []} :failed :output-schema]
           [{:status :failed :trace-id "failed" :diagnostic {:error "JSON parse error with PRIVATE payload"}
             :candidates []} :failed :output-schema]]]
    (fixture/with-system (fn [sys _]
      (fixture/setup-concepts! sys fixture/owner)
      (let [result (call! sys fixture/owner response "one")
            outcome (first (knowledge/taxonomy-relation-outcomes sys fixture/owner))]
        (is (= expected (:status result)))
        (is (= category (:failure-category outcome)))
        (is (not (.contains (pr-str outcome) "PRIVATE")))
        (is (empty? (knowledge/taxonomy-relation-proposals sys fixture/owner)))
        (is (empty? (knowledge/taxonomy-relations sys fixture/owner))))))))

(deftest reliability-006-restart-rollback-and-owner-reads
  (let [dir (.toFile (Files/createTempDirectory "awp-relation-reliability-"
                                            (make-array java.nio.file.attribute.FileAttribute 0)))
        option {:storage-dir (.getPath dir)}
        sys (knowledge/open-system option)]
    (try
      (fixture/setup-concepts! sys fixture/owner)
      (fixture/setup-concepts! sys fixture/other)
      (let [accepted (call! sys fixture/owner grounded "old-model")
            accepted-id (:relation-id (first (:relations accepted)))
            _ (knowledge/taxonomy-relation-review! sys fixture/owner accepted-id "accept-old" 0 :accepted)
            failed (orc/propose-relations!
                     (assoc sys :relation-model! (fn [_] {:status :failed :trace-id "failure"
                                                          :failure-category :provider-error :candidates []}))
                     fixture/owner "relation:turn" "model-2" "prompt-2" "subject-v1"
                     ["event" "design"] {:attempt-id "new-model-failed"})
            pending (call! sys fixture/owner grounded "rollback-to-old-model")]
        (is (= :failed (:status failed)))
        (is (= :staged (:status pending)))
        (is (empty? (knowledge/taxonomy-relations sys fixture/other)))
        (orc/sync-taxonomy-owner! sys fixture/owner)
        (let [before (orc/taxonomy-owner-export sys fixture/owner)]
          (knowledge/close-system! sys)
          (let [reopened (knowledge/open-system option)]
            (try
              (orc/sync-taxonomy-owner! reopened fixture/owner)
              (is (= before (orc/taxonomy-owner-export reopened fixture/owner)))
              (is (= 3 (count (knowledge/taxonomy-relation-outcomes reopened fixture/owner))))
              (is (= #{"model-1" "model-2"}
                     (set (map :model-version (knowledge/taxonomy-relation-outcomes reopened fixture/owner)))))
              (is (= 1 (count (knowledge/taxonomy-relations reopened fixture/owner))))
              (is (= 2 (count (knowledge/taxonomy-relation-proposals reopened fixture/owner))))
              (is (= 1 (count (:awp-relations (first (orc/taxonomy-owner-search
                                                      reopened fixture/owner "Event"))))))
              (is (= 1 (count (orc/taxonomy-owner-traverse reopened fixture/owner "event"))))
              (is (empty? (knowledge/taxonomy-relation-proposals reopened fixture/other)))
              (is (empty? (:relations (orc/taxonomy-owner-export reopened fixture/other))))
              (finally (knowledge/close-system! reopened))))))
      (finally (knowledge/close-system! sys)
               (doseq [file (reverse (file-seq dir))] (io/delete-file file true))))))

(deftest reliability-007-path-verdict-requires-same-attempt
  (is (= :passed (:outcome (orc/relation-path-verdict
                            {:status :staged :result-id "one" :relations [{:status :pending}]}
                            [{:result-id "one" :status :completed :trace-id "trace"}]))))
  (is (= :open (:outcome (orc/relation-path-verdict
                          {:status :staged :result-id "one" :relations [{:status :pending}]}
                          [{:result-id "other" :status :completed :trace-id "trace"}])))))

(deftest reliability-008-failed-live-verdict-open
  (is (= :open (:outcome (orc/relation-path-verdict
                          {:status :failed :result-id "one" :relations []}
                          [{:result-id "one" :status :failed :trace-id "trace"
                            :failure-category :provider-error}]))))
  (is (= :open (:semantic-quality (orc/relation-path-verdict
                                   {:status :staged :result-id "one" :relations [{:status :pending}]}
                                   [{:result-id "one" :status :completed :trace-id "trace"}]))))
  (fixture/with-system (fn [sys _]
    (fixture/setup-concepts! sys fixture/owner)
    (let [opts (atom nil)
          result (with-redefs [router/register! (fn [& _] nil)
                              control-plane/start (fn [& _] :control)
                              control-plane/stop (fn [& _] nil)
                              sheet/build-workflow! (fn [& _] :sheet)
                              sheet/execute (fn [_ _ _ & options]
                                              (reset! opts (apply hash-map options))
                                              {:status :timeout :error "deadline exceeded"})]
                   (orc/explore-local-relations-bounded! sys fixture/owner "relation:turn"
                                                         "model-1" "prompt-1" "subject-v1"
                                                         ["event" "design"]))]
      (is (= 210000 (:timeout-ms @opts)))
      (is (= :timeout (:status result)))
      (is (= :timeout (:failure-category result)))
      (is (string? (:trace-id result)))))))
