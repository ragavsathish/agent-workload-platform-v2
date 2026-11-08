(ns awp-v2.knowledge.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(defn- apply-fact [state {:keys [kind data]}]
  (case kind
    :intake (let [{:keys [source-id]} data]
              (-> state
                  (assoc-in [:sources source-id] (select-keys data [:source-id :owner :conversation-id :message-id :transcript :captured-at]))
                  (assoc-in [:work source-id :journal] {:source-id source-id :mode :journal :status :pending :request-id (:journal-request-id data)})
                  (assoc-in [:work source-id :classification] {:source-id source-id :mode :classification :status :pending :request-id (:classification-request-id data)})))
    :submission (assoc-in state [:work (:source-id data) (:mode data) :task-id] (:task-id data))
    :submission-uncertain (assoc-in state [:work (:source-id data) (:mode data) :status] :uncertain)
    :outcome (let [{:keys [source-id mode status suggestions]} data
                   next (-> state
                            (assoc-in [:work source-id mode :status] status)
                            (assoc-in [:work source-id mode :reason] (:reason data))
                            (assoc-in [:work source-id mode :output-digest] (:output-digest data))
                            (assoc-in [:work source-id mode :result-id] (:result-id data))
                            (assoc-in [:work source-id mode :model] (:model data))
                            (assoc-in [:work source-id mode :processor-version] (:processor-version data))
                            (assoc-in [:work source-id mode :classification] (:classification data)))]
               (reduce (fn [s item]
                         (assoc-in s [:items (:item-id item)]
                                   {:item-id (:item-id item) :kind (:kind item)
                                    :owner (:owner data) :source-id source-id :mode mode
                                    :original-text (:text item) :current-text (:text item)
                                    :evidence (:evidence item) :entry-type (:entry-type item)
                                    :candidate-uri (:candidate-uri item) :result-id (:result-id data)
                                    :model (:model data) :processor-version (:processor-version data)
                                    :status :pending :revision 0 :reviews []}))
                       next suggestions))
    :review (let [{:keys [item-id decision text action-id]} data]
              (-> state
                  (update-in [:items item-id :revision] inc)
                  (assoc-in [:items item-id :status] decision)
                  (cond-> text (assoc-in [:items item-id :current-text] text))
                  (update-in [:items item-id :reviews] conj
                             (select-keys data [:action-id :decision :text :expected-revision :at]))
                  (assoc-in [:actions action-id] data)))
    :taxonomy-proposal (-> state
                           (assoc-in [:taxonomy-proposals (:proposal-id data)]
                                     (assoc (dissoc data :digest) :status :pending :revision 0 :reviews [] :mappings {}))
                           (assoc-in [:taxonomy-result-ids (:result-id data)] (:digest data)))
    :taxonomy-review (-> state
                         (assoc-in [:taxonomy-proposals (:proposal-id data) :status] (:decision data))
                         (update-in [:taxonomy-proposals (:proposal-id data) :revision] inc)
                         (update-in [:taxonomy-proposals (:proposal-id data) :reviews] conj
                                    (select-keys data [:action-id :expected-revision :decision :at]))
                         (assoc-in [:taxonomy-actions (:action-id data)] data))
    :taxonomy-map (assoc-in state [:taxonomy-proposals (:proposal-id data) :mappings (:to-version data)]
                            (select-keys data [:to-version :category-id :decision-id]))
    :taxonomy-model-outcome (-> state
                                (assoc-in [:taxonomy-outcomes (:result-id data)] (dissoc data :digest))
                                (assoc-in [:taxonomy-result-ids (:result-id data)] (:digest data)))
    :taxonomy-relation-attempt (assoc-in state [:taxonomy-relation-attempts (:result-id data)] data)
    :taxonomy-relation (assoc-in state [:taxonomy-relations (:relation-id data)]
                                 (assoc data :status :pending :revision 0 :reviews []))
    :taxonomy-relation-review (-> state
                                  (assoc-in [:taxonomy-relations (:relation-id data) :status] (:decision data))
                                  (update-in [:taxonomy-relations (:relation-id data) :revision] inc)
                                  (update-in [:taxonomy-relations (:relation-id data) :reviews] conj
                                             (select-keys data [:action-id :expected-revision :decision :at]))
                                  (assoc-in [:taxonomy-actions (:action-id data)] data))
    :taxonomy-relation-correct
    (let [old (get-in state [:taxonomy-relations (:relation-id data)])
          replacement (-> (select-keys data [:left-id :right-id :relation-kind :evidence])
                          (assoc :relation-id (:replacement-id data)
                                 :owner (:owner data)
                                 :source-id (:source-id old)
                                 :origin :owner-correction
                                 :replaces-id (:relation-id data)
                                 :status :pending :revision 0 :reviews []))]
      (-> state
          (assoc-in [:taxonomy-relations (:relation-id data) :status] :rejected)
          (assoc-in [:taxonomy-relations (:relation-id data) :corrected-by] (:replacement-id data))
          (update-in [:taxonomy-relations (:relation-id data) :revision] inc)
          (update-in [:taxonomy-relations (:relation-id data) :reviews] conj
                     (select-keys data [:action-id :expected-revision :decision :at]))
          (assoc-in [:taxonomy-relations (:replacement-id data)] replacement)
          (assoc-in [:taxonomy-actions (:action-id data)] data)))
    :quality-case (assoc-in state [:quality-cases (:case-id data)]
                            (assoc data :review-status :pending :privacy-status :pending :review-history []))
    :quality-review (-> state
                        (assoc-in [:quality-cases (:case-id data) :review-status] (:decision data))
                        (assoc-in [:quality-cases (:case-id data) :privacy-status] (:privacy-status data))
                        (assoc-in [:quality-cases (:case-id data) :expected-digest] (:expected-digest data))
                        (update-in [:quality-cases (:case-id data) :review-history] conj
                                   (select-keys data [:action-id :reviewer-id :reviewer-role :decision :privacy-status :expected-digest :at]))
                        (assoc-in [:quality-actions (:action-id data)] data))
    :quality-run (assoc-in state [:quality-runs (:run-id data)] (assoc data :status :prepared))
    :quality-holdout (-> state
                         (assoc-in [:quality-runs (:run-id data) :status] :running)
                         (assoc-in [:quality-holdouts (:corpus-digest data)] (:run-id data)))
    :quality-verdict (-> state
                         (assoc-in [:quality-runs (:run-id data) :status] (:outcome data))
                         (assoc-in [:quality-runs (:run-id data) :metrics-digest] (:metrics-digest data)))
    :legacy-snapshot (assoc-in state [:legacy-snapshots (:snapshot-id data)] (select-keys data [:snapshot-id :digest]))
    :legacy-resource (assoc-in state [:legacy-resources (:snapshot-id data) (:legacy-id data)] (:resource data))
    :legacy-switch (assoc state :read-source :import :selected-snapshot (:snapshot-id data))
    :legacy-rollback (assoc state :read-source :source)
    :daily-request (assoc-in state [:accounts (:account-id data)]
                             (assoc data :status :pending))
    :daily-submission (assoc-in state [:accounts (:account-id data) :task-id] (:task-id data))
    :daily-uncertain (assoc-in state [:accounts (:account-id data) :status] :uncertain)
    :daily-result (-> state
                      (assoc-in [:accounts (:account-id data) :status] (:status data))
                      (assoc-in [:accounts (:account-id data) :title] (:title data))
                      (assoc-in [:accounts (:account-id data) :story] (:story data))
                      (assoc-in [:accounts (:account-id data) :reason] (:reason data))
                      (assoc-in [:accounts (:account-id data) :result-id] (:result-id data))
                      (assoc-in [:accounts (:account-id data) :model] (:model data))
                      (assoc-in [:accounts (:account-id data) :processor-version] (:processor-version data))
                      (assoc-in [:accounts (:account-id data) :output-digest] (:output-digest data)))
    state))

(defreadmodel :knowledge state
  {:events #{:knowledge/fact} :version 2 :schema [:map-of :string :map]}
  "Owner-scoped knowledge state from immutable decisions."
  [state event]
  (let [owner (:owner event)]
    (assoc state owner (-> (apply-fact (get state owner {}) event)
                           (assoc :revision (:event/id event))))))

(defn owners [context] (rmp/project context :knowledge/state))
(defn owner-state [context owner]
  (get (owners context) owner))
