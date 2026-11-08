(ns awp-v2.knowledge.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :knowledge
  {:description "Source-backed owner knowledge and explicit daily account decisions."
   :commands
   (into {} (for [[op rule] {:intake "AdmitSourceTurn"
                             :submission "RecordExtraction"
                             :submission-uncertain "MarkSubmissionUncertain"
                             :outcome "CompleteExtraction"
                             :review "ReviewSuggestion"
                             :daily-request "RequestDailyAccount"
                             :daily-submission "RequestDailyAccount"
                             :daily-uncertain "FailDailyAccount"
                             :daily-result "CompleteDailyAccount"
                             :legacy-snapshot "StartImport"
                             :legacy-resource "ImportResource"
                             :legacy-switch "PassGate"
                             :legacy-rollback "StartImport"
                             :taxonomy-proposal "RecordModelProposal"
                             :taxonomy-review "DecideModelProposal"
                             :taxonomy-map "MapCategoryVersion"
                             :taxonomy-model-outcome "RecordModelOutcome"
                             :taxonomy-relation "ProposeRelation"
                             :taxonomy-relation-review "DecideRelation"
                             :taxonomy-relation-attempt "RecordRelationAttempt"
                             :taxonomy-relation-correct "CorrectRelationProposal"
                             :quality-case "RegisterReviewCase"
                             :quality-review "ApproveReviewCase"
                             :quality-run "StartQualityRun"
                             :quality-holdout "ConsumeLockedHoldout"
                             :quality-verdict "RecordQualityVerdict"}]
              [(keyword "knowledge" (name op))
               {:description (name op)
                :schema [:map [:owner :string] [:data :map]]
                :reads #{:knowledge/state} :produces #{:knowledge/fact}
                :grain/allium [{:spec (cond
                                       (#{:legacy-snapshot :legacy-resource :legacy-switch :legacy-rollback} op)
                                       "specs/knowledge-extraction-migration.allium"
                                       (#{:taxonomy-relation-attempt :taxonomy-relation-correct} op)
                                       "specs/grounded-relations.allium"
                                       (#{:taxonomy-proposal :taxonomy-review :taxonomy-map :taxonomy-model-outcome
                                          :taxonomy-relation :taxonomy-relation-review} op)
                                       "specs/knowledge-taxonomy.allium"
                                       (#{:quality-case :quality-review :quality-run :quality-holdout :quality-verdict} op)
                                       "specs/knowledge-quality.allium"
                                       :else "specs/personal-knowledge.allium")
                                :kind :rule :name rule}]}]))
   :events {:knowledge/fact {:description "One knowledge decision or external outcome."
                             :schema [:map [:owner :string] [:kind :keyword] [:data :map]]}}
   :read-models {:knowledge/state {:description "Owner source, work, review, and account state."
                                   :consumes #{:knowledge/fact} :schema [:map-of :string :map] :version 2}}
   :queries (into {} (for [[op schema] {:tasks [:map [:owner :string] [:source-id :string]]
                                        :suggestions [:map [:owner :string]]
                                        :item [:map [:owner :string] [:item-id :string]]
                                        :profile [:map [:owner :string]]
                                        :account [:map [:owner :string] [:account-id :string]]
                                        :accounts [:map [:owner :string]]
                                        :taxonomy-item [:map [:owner :string] [:proposal-id :string]]
                                        :taxonomy-proposals [:map [:owner :string]]
                                        :taxonomy-current [:map [:owner :string]]
                                        :taxonomy-relations [:map [:owner :string]]
                                        :taxonomy-relation-item [:map [:owner :string] [:relation-id :string]]
                                        :taxonomy-relation-proposals [:map [:owner :string]]
                                        :taxonomy-relation-outcomes [:map [:owner :string]]
                                        :taxonomy-outcomes [:map [:owner :string]]
                                        :quality-cases [:map [:owner :string]]
                                        :quality-run-status [:map [:owner :string] [:run-id :string]]}]
                       [(keyword "knowledge" (name op))
                        {:description (name op) :schema schema :reads #{:knowledge/state}}]))
   :flows {:knowledge/intake {:description "A completed turn becomes independently tracked work."
                              :steps [{:from nil :to [:command :knowledge/intake]}
                                      {:from [:command :knowledge/intake] :to [:event :knowledge/fact]}
                                      {:from [:event :knowledge/fact] :to [:read-model :knowledge/state]}
                                      {:from [:read-model :knowledge/state] :to [:query :knowledge/tasks]}
                                      {:from [:query :knowledge/tasks] :to nil}]}}})
