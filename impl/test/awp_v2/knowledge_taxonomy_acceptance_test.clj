(ns awp-v2.knowledge-taxonomy-acceptance-test
  (:require [clojure.test :refer :all]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.grain.event-store-v3.interface :as es]
            [clojure.java.io :as io]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.orc-adapter :as orc]
            [awp-v2.knowledge.taxonomy :as taxonomy]
            [awp-v2.knowledge.taxonomy-quality :as quality])
  (:import [java.nio.file Files]
           [java.security MessageDigest]
           [java.util UUID]))

(def owner "taxonomy-owner@example.test")
(def other "other-owner@example.test")
(def turn {:source-id "session:turn" :conversation-id "session" :message-id "turn"
           :transcript "I learned event modeling. I can build reliable systems. I decided to study music."
           :captured-at "2026-11-08T10:00:00Z" :status :completed :origin :owner})
(defn system [] (knowledge/open-system {:submit-task! (fn [request] {:task-id (:request-id request)})}))
(defn setup! [sys actor source]
  (knowledge/ingest-turn! sys actor source))
(defn proposal [id category evidence]
  {:proposal-id id :result-id (str "result-" id) :source-id "session:turn"
   :category-id category :taxonomy-version "personal-v1" :text (str "Meaning of " id)
   :evidence evidence :model-version "model-1" :prompt-version "prompt-1"})
(defn with-system [f]
  (let [sys (system)] (try (f sys) (finally (knowledge/close-system! sys)))))
(defn hash-id [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str value) "UTF-8"))))

(deftest taxonomy-001-versioned-category-hierarchy
  (let [tree (taxonomy/categories "personal-v1")]
    (is (= 7 (count (filter #(= "journal" (:parent-id %)) tree))))
    (is (= 3 (count (filter #(= "assertion" (:parent-id %)) tree))))
    (is (not-any? #(= "none" (:category-id %)) tree))
    (is (= "assertion" (:parent-id (first (filter #(= "conversation-topic" (:category-id %)) tree)))))))

(deftest taxonomy-002-version-mapping-retains-original
  (with-system (fn [sys]
    (setup! sys owner turn)
    (let [p (knowledge/taxonomy-propose! sys owner (proposal "p2" "learning" "I learned event modeling."))]
      (is (= :pending (:status p)))
      (is (= :accepted (:status (knowledge/taxonomy-review! sys owner "p2" "accept-p2" 0 :accepted))))
      (is (= :accepted (:status (knowledge/taxonomy-map! sys owner "p2" "personal-v2" "reflection" "map-p2"))))
      (let [item (knowledge/taxonomy-item sys owner "p2")]
        (is (= "learning" (:category-id item)))
        (is (= "personal-v1" (:taxonomy-version item)))
        (is (= "reflection" (get-in item [:mappings "personal-v2" :category-id]))))))))

(deftest taxonomy-003-unsupported-category-refused
  (with-system (fn [sys]
    (setup! sys owner turn)
    (is (= :incorrect (:error (knowledge/taxonomy-propose! sys owner (proposal "bad" "wizard" "I learned event modeling.")))))
    (is (= :not-found (:error (knowledge/taxonomy-item sys owner "bad"))))
    (is (empty? (knowledge/taxonomy-current sys owner))))))

(deftest taxonomy-004-exact-proposal-awaits-review
  (with-system (fn [sys]
    (setup! sys owner turn)
    (let [p (knowledge/taxonomy-propose! sys owner (proposal "p4" "learning" "I learned event modeling."))]
      (is (= :pending (:status p)))
      (is (= "model-1" (:model-version (knowledge/taxonomy-item sys owner "p4"))))
      (is (empty? (knowledge/taxonomy-current sys owner)))
      (is (= :accepted (:status (knowledge/taxonomy-review! sys owner "p4" "accept-p4" 0 :accepted))))
      (is (= 1 (count (knowledge/taxonomy-current sys owner))))
      (is (= :reused (:status (knowledge/taxonomy-propose! sys owner (proposal "p4" "learning" "I learned event modeling.")))))))))

(deftest taxonomy-005-quote-alone-does-not-accept-skill
  (with-system (fn [sys]
    (setup! sys owner (assoc turn :transcript "How can I build reliable systems?"))
    (is (= :pending (:status (knowledge/taxonomy-propose! sys owner (proposal "p5" "skill-evidence" "How can I build reliable systems?")))))
    (is (empty? (knowledge/taxonomy-current sys owner)))
    (is (= :rejected (:status (knowledge/taxonomy-review! sys owner "p5" "reject-p5" 0 :rejected))))
    (is (= :rejected (:status (knowledge/taxonomy-item sys owner "p5")))))))

(deftest taxonomy-006-relation-requires-separate-review
  (with-system (fn [sys]
    (setup! sys owner turn)
    (knowledge/taxonomy-propose! sys owner (proposal "left" "conversation-topic" "I learned event modeling."))
    (knowledge/taxonomy-propose! sys owner (proposal "right" "conversation-topic" "I can build reliable systems."))
    (is (= :conflict (:error (knowledge/taxonomy-relation! sys owner {:relation-id "r6" :left-id "left" :right-id "right" :relation-kind :broader :source-id "session:turn" :evidence "I learned event modeling."}))))
    (knowledge/taxonomy-review! sys owner "left" "accept-left" 0 :accepted)
    (knowledge/taxonomy-review! sys owner "right" "accept-right" 0 :accepted)
    (is (= :pending (:status (knowledge/taxonomy-relation! sys owner {:relation-id "r6" :left-id "left" :right-id "right" :relation-kind :broader :source-id "session:turn" :evidence "I learned event modeling."}))))
    (is (empty? (knowledge/taxonomy-relations sys owner)))
    (is (= :accepted (:status (knowledge/taxonomy-relation-review! sys owner "r6" "accept-r6" 0 :accepted))))
    (is (= 1 (count (knowledge/taxonomy-relations sys owner))))
    (knowledge/taxonomy-relation! sys owner {:relation-id "cycle" :left-id "right" :right-id "left"
                                             :relation-kind :broader :source-id "session:turn"
                                             :evidence "I can build reliable systems."})
    (is (= :conflict (:error (knowledge/taxonomy-relation-review! sys owner "cycle" "accept-cycle" 0 :accepted)))))))

(deftest taxonomy-007-replay-conflict-and-empty
  (with-system (fn [sys]
    (setup! sys owner turn)
    (let [p (proposal "p7" "learning" "I learned event modeling.")]
      (is (= :pending (:status (knowledge/taxonomy-propose! sys owner p))))
      (is (= :reused (:status (knowledge/taxonomy-propose! sys owner p))))
      (is (= :conflict (:error (knowledge/taxonomy-propose! sys owner (assoc p :text "Changed")))))
      (is (= :empty (:status (knowledge/taxonomy-model-outcome! sys owner "empty-result" "session:turn" :empty "model-1" "prompt-1"))))
      (is (= :timeout (:status (knowledge/taxonomy-model-outcome! sys owner "timeout-result" "session:turn" :timeout "model-1" "prompt-1"))))
      (is (= #{:empty :timeout} (set (map :status (knowledge/taxonomy-outcomes sys owner)))))
      (is (= 1 (count (knowledge/taxonomy-proposals sys owner))))))))

(deftest taxonomy-008-rebuild-revocation
  (with-system (fn [sys]
    (setup! sys owner turn)
    (doseq [[id quote] [["a" "I learned event modeling."] ["b" "I can build reliable systems."]]]
      (knowledge/taxonomy-propose! sys owner (proposal id "conversation-topic" quote))
      (knowledge/taxonomy-review! sys owner id (str "accept-" id) 0 :accepted))
    (knowledge/taxonomy-relation! sys owner {:relation-id "r8" :left-id "a" :right-id "b" :relation-kind :broader :source-id "session:turn" :evidence "I learned event modeling."})
    (knowledge/taxonomy-relation-review! sys owner "r8" "accept-r8" 0 :accepted)
    (knowledge/taxonomy-review! sys owner "a" "revoke-a" 1 :revoked)
    (is (= 1 (count (knowledge/taxonomy-current sys owner))))
    (is (empty? (knowledge/taxonomy-relations sys owner)))
    (is (= :revoked (:status (knowledge/taxonomy-item sys owner "a"))))
    (is (= 2 (count (:reviews (knowledge/taxonomy-item sys owner "a"))))))))

(deftest taxonomy-009-scoped-orc-reads
  (with-system (fn [sys]
    (setup! sys owner turn)
    (setup! sys other (assoc turn :transcript "I learned astronomy."))
    (knowledge/taxonomy-propose! sys owner (proposal "same" "learning" "I learned event modeling."))
    (knowledge/taxonomy-propose! sys other (assoc (proposal "same" "learning" "I learned astronomy.")
                                              :text "Astronomy learning"))
    (doseq [actor [owner other]]
      (knowledge/taxonomy-review! sys actor "same" (str "accept-" actor) 0 :accepted)
      (orc/sync-taxonomy-owner! sys actor))
    (is (= 1 (count (orc/taxonomy-owner-concepts sys owner))))
    (is (= 1 (count (orc/taxonomy-owner-concepts sys other))))
    (is (not= (:uri (first (orc/taxonomy-owner-concepts sys owner)))
              (:uri (first (orc/taxonomy-owner-concepts sys other)))))
    (is (empty? (orc/taxonomy-owner-search sys owner "Astronomy")))
    (is (= 1 (count (orc/taxonomy-owner-search sys other "Astronomy"))))
    (is (empty? (orc/taxonomy-owner-traverse sys owner "missing")))
    (is (not-any? #(= "Astronomy learning" (:label %))
                  (:concepts (orc/taxonomy-owner-export sys owner))))
    (is (= (:uri (first (orc/taxonomy-owner-concepts sys other)))
           (:uri (orc/taxonomy-owner-concept sys other "same"))))
    (is (= :not-found (:error (knowledge/taxonomy-item sys owner "other-only")))))))

(deftest taxonomy-010-reviewed-quality-report
  (let [cases [{:id "positive" :gold [{:category-id "learning" :evidence "I learned event modeling."}]
                :predicted [{:category-id "learning" :evidence "I learned event modeling."}]
                :source "I learned event modeling."}
               {:id "negative" :gold [] :predicted [] :source "Check the logs."}]
        report (quality/evaluate {:corpus-id "reviewed-v1" :corpus-digest "sha256:reviewed"
                                  :reviewed? true :thresholds {:max-false-additions 0 :max-misses 0
                                                                :max-excerpt-errors 0 :max-category-errors 0
                                                                :max-wrong-relations 0}
                                  :model-version "model-1" :prompt-version "prompt-1"
                                  :taxonomy-version "personal-v1" :cases cases})]
    (is (= :open (:status report)))
    (is (= 0 (:false-additions report)))
    (is (= 0 (:misses report)))))

(deftest taxonomy-010-scoring-distinguishes-error-kinds
  (let [report (quality/evaluate
                 {:corpus-id "synthetic-errors" :reviewed? false
                  :model-version "model-1" :prompt-version "prompt-1"
                  :taxonomy-version "personal-v1"
                  :cases [{:id "errors" :source "I learned event modeling. I will practice."
                           :gold [{:category-id "learning" :evidence "I learned event modeling."}
                                  {:category-id "goal" :evidence "I will practice."}]
                           :predicted [{:category-id "goal" :evidence "I learned event modeling."}
                                       {:category-id "skill-evidence" :evidence "invented"}]
                           :gold-relations [] :predicted-relations [[:broader "a" "b"]]}]})]
    (is (= :open (:status report)))
    (is (= 1 (:false-additions report)))
    (is (= 1 (:misses report)))
    (is (= 1 (:excerpt-errors report)))
    (is (= 1 (:category-errors report)))
    (is (= 1 (:wrong-relations report)))))

(deftest taxonomy-011-unreviewed-quality-stays-open
  (is (= :open (:status (quality/evaluate {:corpus-id "synthetic" :reviewed? false
                                         :model-version "model-1" :prompt-version "prompt-1"
                                         :taxonomy-version "personal-v1" :cases []})))))

(deftest taxonomy-004-orc-bounded-candidate-enters-grain-review
  (with-system (fn [sys]
    (setup! sys owner turn)
    (let [source-id "session:turn" model "pinned-model"
          id (orc/candidate-ontology-id owner source-id)
          name (str "AWP candidate " (hash-id (str owner ":" source-id ":" model)))
          uri "urn:awp:test:bounded-candidate"
          trace-id (UUID/randomUUID)]
      (ontology/create-ontology! sys {:command/id id :name name :scope :custom
                                      :description "Unreviewed owner source concepts; expected=1"
                                      :base-uri "urn:awp:test:"})
      (ontology/create-concept! sys id {:command/id (UUID/randomUUID) :uri uri
                                        :label "Event modeling learned"
                                        :description "I learned event modeling."
                                        :scope :custom
                                        :provenance {:kind :source-extracted :owner-hash (hash-id owner)
                                                     :source-id source-id :model model :trace-id trace-id}})
      (let [staged (orc/stage-taxonomy-candidates! sys owner source-id model
                                                   [{:uri uri :category-id "learning"
                                                     :evidence "I learned event modeling."}])
            p (first (:proposals staged))]
        (is (= :staged (:status staged)))
        (is (= :pending (:status p)))
        (is (= trace-id (:trace-id (knowledge/taxonomy-item sys owner (:proposal-id p)))))
        (is (empty? (knowledge/taxonomy-current sys owner)))
        (knowledge/taxonomy-review! sys owner (:proposal-id p) "bounded-accept" 0 :accepted)
        (orc/sync-taxonomy-owner! sys owner)
        (is (= 1 (count (orc/taxonomy-owner-concepts sys owner)))))))))

(deftest taxonomy-008-shared-event-store-restart-and-rebuild
  (let [dir (.toFile (Files/createTempDirectory "awp-taxonomy-restart-"
                                               (make-array java.nio.file.attribute.FileAttribute 0)))
        options {:storage-dir (.getPath dir)
                 :submit-task! (fn [request] {:task-id (:request-id request)})}]
    (try
      (let [sys (knowledge/open-system options)]
        (try
          (setup! sys owner turn)
          (doseq [[id evidence] [["accepted-1" "I learned event modeling."]
                                 ["accepted-2" "I can build reliable systems."]
                                 ["pending" "I decided to study music."]
                                 ["rejected" "I can build reliable systems."]]]
            (knowledge/taxonomy-propose! sys owner (proposal id "conversation-topic" evidence)))
          (doseq [id ["accepted-1" "accepted-2"]]
            (knowledge/taxonomy-review! sys owner id (str "accept-" id) 0 :accepted))
          (knowledge/taxonomy-review! sys owner "rejected" "reject" 0 :rejected)
          (knowledge/taxonomy-relation! sys owner {:relation-id "persisted-edge"
                                                   :left-id "accepted-1" :right-id "accepted-2"
                                                   :relation-kind :related :source-id "session:turn"
                                                   :evidence "I learned event modeling."})
          (knowledge/taxonomy-relation-review! sys owner "persisted-edge" "accept-edge" 0 :accepted)
          (orc/sync-taxonomy-owner! sys owner)
          (is (= 2 (count (orc/taxonomy-owner-concepts sys owner))))
          (is (pos? (count (into [] (es/read (:event-store sys) {:types #{:knowledge/fact}})))))
          (is (pos? (count (into [] (es/read (:event-store sys) {:types #{:ontology/concept-created}})))))
          (finally (knowledge/close-system! sys))))
      (let [restarted (knowledge/open-system options)]
        (try
          (is (= 2 (count (knowledge/taxonomy-current restarted owner))))
          (is (= 1 (count (knowledge/taxonomy-relations restarted owner))))
          (is (= 2 (count (orc/taxonomy-owner-concepts restarted owner))))
          (is (= :pending (:status (knowledge/taxonomy-item restarted owner "pending"))))
          (is (= :rejected (:status (knowledge/taxonomy-item restarted owner "rejected"))))
          (is (empty? (knowledge/taxonomy-current restarted other)))
          (is (= 2 (:item-count (orc/sync-taxonomy-owner! restarted owner))))
          (is (= 2 (count (orc/taxonomy-owner-concepts restarted owner))))
          (finally (knowledge/close-system! restarted))))
      (finally (doseq [f (reverse (file-seq dir))] (io/delete-file f true))))))
