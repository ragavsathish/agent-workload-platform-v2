(ns awp-v2.grounded-relations-acceptance-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.orc-adapter :as orc]
            [awp-v2.knowledge.quality-orc-runner :as quality-runner]
            [awp-v2.knowledge.read-models :as models])
  (:import [java.nio.file Files]))

(def owner "relation-owner@example.test")
(def other "relation-other@example.test")
(def source {:source-id "relation:turn" :conversation-id "relation" :message-id "turn"
             :transcript "Event modeling is a kind of software design. Event modeling. Software design. Event modeling and music are both interests."
             :captured-at "2026-11-08T10:00:00Z" :status :completed :origin :owner})
(defn option [dir] {:storage-dir (.getPath dir)})
(defn with-system [f]
  (let [dir (.toFile (Files/createTempDirectory "awp-relations-"
                                              (make-array java.nio.file.attribute.FileAttribute 0)))
        sys (knowledge/open-system (option dir))]
    (try (f sys dir)
         (finally (knowledge/close-system! sys)
                  (doseq [file (reverse (file-seq dir))] (io/delete-file file true))))))
(defn concept [id text evidence]
  {:proposal-id id :result-id (str "concept-" id) :source-id "relation:turn"
   :category-id "conversation-topic" :taxonomy-version "personal-v1"
   :text text :evidence evidence :model-version "seed" :prompt-version "seed"})
(defn setup-concepts! [sys actor]
  (knowledge/ingest-turn! sys actor source)
  (doseq [[id text evidence] [["event" "Event modeling" "Event modeling"]
                              ["design" "Software design" "software design"]
                              ["music" "Music" "music"]]]
    (knowledge/taxonomy-propose! sys actor (concept id text evidence))
    (knowledge/taxonomy-review! sys actor id (str "accept-" id) 0 :accepted)))
(defn candidate [kind evidence]
  {:left-id "event" :right-id "design" :relation-kind kind :evidence evidence})
(defn attempt [sys actor response]
  (orc/propose-relations! (assoc sys :relation-model! (fn [_] response)) actor
                          "relation:turn" "model-1" "prompt-1" "subject-v1"
                          ["event" "design"]))

(deftest relation-001-bounded-grounded-proposal
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (let [result (attempt sys owner {:status :completed :trace-id "trace-1"
                                     :candidates [(candidate :broader "Event modeling is a kind of software design.")]})
          edge (first (knowledge/taxonomy-relation-proposals sys owner))]
      (is (= :staged (:status result)))
      (is (= 1 (count (:relations result))))
      (is (= :pending (:status edge)))
      (is (= "model-1" (:model-version edge)))
      (is (= "trace-1" (:trace-id edge)))
      (is (string? (:source-digest edge)))
      (is (= "prompt-1" (:prompt-version edge)))
      (is (= "subject-v1" (:policy-version edge)))
      (is (= :broader (:relation-kind edge)))
      (is (empty? (knowledge/taxonomy-relations sys owner)))))))

(deftest relation-002-exact-list-not-semantic-proof
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (let [result (attempt sys owner {:status :completed :trace-id "trace-2"
                                     :candidates [(candidate :related "Event modeling. Software design.")]})]
      (is (= :pending (:status (first (:relations result)))))
      (is (empty? (knowledge/taxonomy-relations sys owner)))
      (is (= :rejected (:status (knowledge/taxonomy-relation-review!
                                sys owner (:relation-id (first (:relations result))) "reject-list" 0 :rejected))))))))

(deftest relation-003-unsupported-kind-and-mislabelled-fact
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (is (= :invalid (:status (attempt sys owner {:status :completed :trace-id "trace-3"
        :candidates [(candidate :causes "Event modeling is a kind of software design.")]}))))
    (is (empty? (knowledge/taxonomy-relation-proposals sys owner)))))
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (knowledge/ingest-turn! sys owner
      {:source-id "fact:turn" :conversation-id "fact" :message-id "turn"
       :transcript "Event modeling causes software design." :captured-at "2026-11-08T11:00:00Z"
       :status :completed :origin :owner})
    (let [result (orc/propose-relations! (assoc sys :relation-model!
                                              (fn [_] {:status :completed :trace-id "mislabelled"
                                                       :candidates [(candidate :related "Event modeling causes software design.")]}))
                                         owner "fact:turn" "model-1" "prompt-1" "subject-v1"
                                         ["event" "design"])
          id (:relation-id (first (:relations result)))]
      (is (= :pending (:status (first (:relations result)))))
      (is (empty? (knowledge/taxonomy-relations sys owner)))
      (is (= :rejected (:status (knowledge/taxonomy-relation-review! sys owner id "reject-fact" 0 :rejected))))))))

(deftest relation-004-empty-invalid-timeout-outcomes
  (doseq [[response expected] [[{:status :empty :trace-id "trace-empty" :candidates []} :empty]
                               [{:status :timeout :trace-id "trace-timeout" :candidates []} :timeout]
                               [{:status :failed :trace-id "trace-failed" :candidates []} :failed]
                               [{:status :completed :trace-id "trace-invalid" :candidates [(candidate :broader "invented")]} :invalid]]]
    (with-system (fn [sys _]
      (setup-concepts! sys owner)
      (is (= expected (:status (attempt sys owner response))))
      (is (empty? (knowledge/taxonomy-relation-proposals sys owner)))
      (is (= expected (:status (first (knowledge/taxonomy-relation-outcomes sys owner)))))))))

(deftest relation-005-idempotent-stage-and-conflict
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (let [response {:status :completed :trace-id "trace-5"
                    :candidates [(candidate :broader "Event modeling is a kind of software design.")]}
          _ (attempt sys owner response)]
      (is (= :reused (:status (attempt sys owner response))))
      (is (= 1 (count (knowledge/taxonomy-relation-proposals sys owner))))
      (is (= :conflict (:error (knowledge/taxonomy-relation-attempt! sys owner
           (-> (first (knowledge/taxonomy-relation-outcomes sys owner))
               (dissoc :digest)
               (assoc :trace-id "changed-trace"))))))))))

(deftest relation-006-accept-current-revision-and-owner
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (let [edge (first (:relations (attempt sys owner {:status :completed :trace-id "trace-6"
      :candidates [(candidate :broader "Event modeling is a kind of software design.")]})))
          id (:relation-id edge)]
      (is (= :not-found (:error (knowledge/taxonomy-relation-review! sys other id "foreign" 0 :accepted))))
      (is (= :accepted (:status (knowledge/taxonomy-relation-review! sys owner id "accept" 0 :accepted))))
      (is (= :conflict (:error (knowledge/taxonomy-relation-review! sys owner id "stale" 0 :accepted))))
      (orc/sync-taxonomy-owner! sys owner)
      (is (= [id] (mapv :relation-id (:awp-relations (first (orc/taxonomy-owner-search sys owner "Event"))))))
      (is (= [id] (mapv :relation-id (orc/taxonomy-owner-traverse sys owner "event"))))
      (is (= [id] (mapv :relation-id (:relations (orc/taxonomy-owner-export sys owner)))))))))

(deftest relation-007-correction-linked-replacement
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (let [original (first (:relations (attempt sys owner {:status :completed :trace-id "trace-7"
      :candidates [(candidate :broader "Event modeling is a kind of software design.")]})))
          replacement (knowledge/taxonomy-relation-correct! sys owner (:relation-id original)
                       "correction-7" 0 "corrected-7" "design" "event" :broader
                       "Event modeling is a kind of software design.")]
      (is (= :pending (:status replacement)))
      (is (= (:relation-id original) (:replaces-id replacement)))
      (is (= :rejected (:status (knowledge/taxonomy-relation-item sys owner (:relation-id original)))))
      (is (= :accepted (:status (knowledge/taxonomy-relation-review! sys owner "corrected-7" "accept-corrected" 0 :accepted))))
      (is (= ["corrected-7"] (mapv :relation-id (knowledge/taxonomy-relations sys owner))))))))

(deftest relation-008-reject-revoke-history
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (let [id (:relation-id (first (:relations (attempt sys owner {:status :completed :trace-id "trace-8"
      :candidates [(candidate :broader "Event modeling is a kind of software design.")]}))))]
      (knowledge/taxonomy-relation-review! sys owner id "accept-8" 0 :accepted)
      (knowledge/taxonomy-relation-review! sys owner id "revoke-8" 1 :revoked)
      (is (= :pending (:status (knowledge/taxonomy-relation! sys owner
        {:relation-id "reject-8" :left-id "event" :right-id "music" :relation-kind :related
         :source-id "relation:turn" :evidence "Event modeling and music are both interests."}))))
      (knowledge/taxonomy-relation-review! sys owner "reject-8" "reject-8-action" 0 :rejected)
      (is (empty? (knowledge/taxonomy-relations sys owner)))
      (is (= :rejected (:status (knowledge/taxonomy-relation-item sys owner "reject-8"))))
      (is (= [:accepted :revoked] (mapv :decision (:reviews (knowledge/taxonomy-relation-item sys owner id)))))))))

(deftest relation-009-endpoint-and-cycle-refusal
  (with-system (fn [sys _]
    (setup-concepts! sys owner)
    (is (= :invalid (:status (attempt sys owner {:status :completed :trace-id "trace-9"
       :candidates [{:left-id "event" :right-id "event" :relation-kind :broader
                     :evidence "Event modeling is a kind of software design."}]}))))
    (knowledge/taxonomy-propose! sys owner (concept "pending" "Pending" "Event modeling"))
    (is (= :conflict (:error (knowledge/taxonomy-relation! sys owner
      {:relation-id "pending-edge" :left-id "event" :right-id "pending"
       :relation-kind :related :source-id "relation:turn" :evidence "Event modeling"}))))
    (is (= :not-found (:error (knowledge/taxonomy-relation! sys owner
      {:relation-id "missing-edge" :left-id "event" :right-id "missing"
       :relation-kind :related :source-id "relation:turn" :evidence "Event modeling"}))))
    (knowledge/taxonomy-relation! sys owner
      {:relation-id "forward" :left-id "event" :right-id "design" :relation-kind :broader
       :source-id "relation:turn" :evidence "Event modeling is a kind of software design."})
    (knowledge/taxonomy-relation-review! sys owner "forward" "accept-forward" 0 :accepted)
    (knowledge/taxonomy-relation! sys owner
      {:relation-id "reverse" :left-id "design" :right-id "event" :relation-kind :broader
       :source-id "relation:turn" :evidence "Event modeling is a kind of software design."})
    (is (= :conflict (:error (knowledge/taxonomy-relation-review! sys owner "reverse" "accept-reverse" 0 :accepted))))
    (is (= ["forward"] (mapv :relation-id (knowledge/taxonomy-relations sys owner)))))))

(deftest relation-010-restart-repair-and-isolation
  (with-system (fn [sys dir]
    (setup-concepts! sys owner)
    (setup-concepts! sys other)
    (let [id (:relation-id (first (:relations (attempt sys owner {:status :completed :trace-id "trace-10"
      :candidates [(candidate :broader "Event modeling is a kind of software design.")]}))))]
      (knowledge/taxonomy-relation-review! sys owner id "accept-10" 0 :accepted)
      (orc/sync-taxonomy-owner! sys owner)
      (knowledge/taxonomy-relation-review! sys owner id "revoke-10" 1 :revoked)
      (knowledge/close-system! sys)
      (let [reopened (knowledge/open-system (option dir))]
        (try
          (is (empty? (knowledge/taxonomy-relations reopened owner)))
          (is (empty? (orc/taxonomy-owner-traverse reopened owner "event")))
          (is (empty? (:relations (orc/taxonomy-owner-export reopened owner))))
          (is (empty? (:relations (orc/taxonomy-owner-export reopened other))))
          (finally (knowledge/close-system! reopened))))))))

(deftest relation-011-quality-actual-path-contract
  (let [called (atom nil)
        text "Event modeling is a kind of software design."
        result (with-redefs [orc/propose-relations!
                             (fn [_ owner source-id model prompt policy endpoints]
                               (reset! called {:owner owner :source-id source-id :model model
                                               :prompt prompt :policy policy :endpoints endpoints})
                               {:status :staged :relations [{:left-id "event" :right-id "design"
                                                             :relation-kind :broader :evidence text}]})]
                 (quality-runner/run-one
                   {"id" "relation-case" "owner" owner "sourceText" text
                    "modelId" "pinned-model" "branch" "relation"
                    "promptId" "prompt-hash" "policyId" "policy-v1"
                    "endpointConcepts" [{"id" "event" "label" "Event modeling"
                                         "evidence" "Event modeling"}
                                        {"id" "design" "label" "software design"
                                         "evidence" "software design"}]}))]
    (is (= "completed" (:status result)))
    (is (= [{:kind "broader" :leftId "event" :rightId "design" :evidence text
             :owner owner :published false}] (:predictions result)))
    (is (= ["event" "design"] (:endpoints @called)))
    (is (= "policy-v1" (:policy @called)))))

(deftest relation-012-unreviewed-quality-open
  (with-system (fn [sys _]
    (is (empty? (filter #(= :relation (:branch %)) (knowledge/quality-cases sys owner)))))))
