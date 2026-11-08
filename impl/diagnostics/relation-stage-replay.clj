;; Synthetic red-capable replay of the slice 19 failed ORC outcome at the full
;; proposal-to-Grain boundary. This is not a live model qualification.
(require '[awp-v2.knowledge.api :as k]
         '[awp-v2.knowledge.orc-adapter :as orc])
(let [sys (k/open-system {})
      owner "synthetic-relation-owner@example.test"
      source-id "synthetic:relation"
      quote "Event modeling is a kind of software design."
      mode (or (System/getenv "AWP_RELATION_REPLAY") "failed")]
  (try
    (k/ingest-turn! sys owner {:source-id source-id :conversation-id "synthetic" :message-id "relation"
                               :transcript quote :captured-at "2026-11-08T10:00:00Z"
                               :status :completed :origin :owner})
    (doseq [[id label evidence] [["event" "Event modeling" "Event modeling"]
                                 ["design" "software design" "software design"]]]
      (k/taxonomy-propose! sys owner {:proposal-id id :result-id (str "seed-" id)
                                      :source-id source-id :category-id "conversation-topic"
                                      :taxonomy-version "personal-v1" :text label :evidence evidence
                                      :model-version "seed" :prompt-version "seed"})
      (k/taxonomy-review! sys owner id (str "accept-" id) 0 :accepted))
    (let [response (case mode
                     "failed" {:status :failed :trace-id "redacted-observed-trace" :candidates []}
                     "completed" {:status :completed :trace-id "redacted-observed-trace"
                                  :candidates [{:left-id "event" :right-id "design"
                                                :kind "broader" :evidence quote}]})
          result (orc/propose-relations! (assoc sys :relation-model! (fn [_] response))
                                         owner source-id "pinned-model" "prompt-v1"
                                         "subject-v1" ["event" "design"])
          pending (k/taxonomy-relation-proposals sys owner)
          current (k/taxonomy-relations sys owner)
          ok (and (= :staged (:status result)) (= 1 (count pending))
                  (= :pending (:status (first pending))) (empty? current))]
      (println "SLICE20_REPLAY" (pr-str {:mode mode :status (:status result)
                                          :attempt-count (count (k/taxonomy-relation-outcomes sys owner))
                                          :pending-count (count pending)
                                          :published-count (count current)
                                          :expected-pending? ok}))
      (when-not ok (throw (ex-info "proposal did not enter pending review" {:mode mode}))))
    (finally (k/close-system! sys))))
