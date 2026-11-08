(ns awp-v2.knowledge.relation-qualification
  "Synthetic full-path relation qualification. Emits only safe counters and categories."
  (:gen-class)
  (:require [clojure.data.json :as json]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.orc-adapter :as orc]))

(defn run! []
  (let [system (knowledge/open-system {})
        owner "synthetic-relation-owner@example.test"
        source-id "synthetic:relation"
        model "qwen3.5:4b-mlx"
        prompt "subject-relation-prompt-v1"
        policy "subject-v1"
        evidence "Event modeling is a kind of software design."
        started (System/currentTimeMillis)]
    (try
      (knowledge/ingest-turn! system owner
        {:source-id source-id :conversation-id "synthetic" :message-id "relation"
         :transcript evidence :captured-at "2026-11-08T10:00:00Z"
         :status :completed :origin :owner})
      (doseq [[id label quote] [["event" "Event modeling" "Event modeling"]
                                ["design" "software design" "software design"]]]
        (knowledge/taxonomy-propose! system owner
          {:proposal-id id :result-id (str "seed-" id) :source-id source-id
           :category-id "conversation-topic" :taxonomy-version "personal-v1"
           :text label :evidence quote :model-version "seed" :prompt-version "seed"})
        (knowledge/taxonomy-review! system owner id (str "accept-" id) 0 :accepted))
      (let [result (orc/propose-relations! system owner source-id model prompt policy
                                           ["event" "design"] {:attempt-id "qualification-1"})
            outcomes (knowledge/taxonomy-relation-outcomes system owner)
            verdict (orc/relation-path-verdict result outcomes)
            attempt (some #(when (= (:result-id result) (:result-id %)) %) outcomes)]
        {:outcome (name (:outcome verdict))
         :semantic-quality "open"
         :model model :prompt prompt :policy policy
         :timeout-limit-ms 210000
         :elapsed-ms (- (System/currentTimeMillis) started)
         :attempt-status (some-> attempt :status name)
         :failure-category (some-> attempt :failure-category name)
         :trace-present (boolean (and (:trace-id attempt) (seq (:trace-id attempt))))
         :attempt-count (count outcomes)
         :pending-count (count (filter #(= :pending (:status %))
                                       (knowledge/taxonomy-relation-proposals system owner)))
         :current-count (count (knowledge/taxonomy-relations system owner))})
      (finally (knowledge/close-system! system)))))

(defn -main [& _]
  (let [result (try (run!)
                    (catch Throwable _
                      {:outcome "open" :semantic-quality "open"
                       :attempt-status "execution-failed" :failure-category "execution-failed"
                       :trace-present false :attempt-count 0 :pending-count 0
                       :current-count 0}))]
    (println (str "AWP_RELATION_QUALIFICATION:" (json/write-str result)))
    (flush)
    (System/exit (if (= "passed" (:outcome result)) 0 1))))
