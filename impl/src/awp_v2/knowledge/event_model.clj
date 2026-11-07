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
                             :legacy-rollback "StartImport"}]
              [(keyword "knowledge" (name op))
               {:description (name op)
                :schema [:map [:owner :string] [:data :map]]
                :reads #{:knowledge/state} :produces #{:knowledge/fact}
                :grain/allium [{:spec (if (#{:legacy-snapshot :legacy-resource :legacy-switch :legacy-rollback} op)
                                         "specs/knowledge-extraction-migration.allium"
                                         "specs/personal-knowledge.allium")
                                :kind :rule :name rule}]}]))
   :events {:knowledge/fact {:description "One knowledge decision or external outcome."
                             :schema [:map [:owner :string] [:kind :keyword] [:data :map]]}}
   :read-models {:knowledge/state {:description "Owner source, work, review, and account state."
                                   :consumes #{:knowledge/fact} :schema [:map-of :string :map] :version 1}}
   :queries (into {} (for [[op schema] {:tasks [:map [:owner :string] [:source-id :string]]
                                        :suggestions [:map [:owner :string]]
                                        :item [:map [:owner :string] [:item-id :string]]
                                        :profile [:map [:owner :string]]
                                        :account [:map [:owner :string] [:account-id :string]]
                                        :accounts [:map [:owner :string]]}]
                       [(keyword "knowledge" (name op))
                        {:description (name op) :schema schema :reads #{:knowledge/state}}]))
   :flows {:knowledge/intake {:description "A completed turn becomes independently tracked work."
                              :steps [{:from nil :to [:command :knowledge/intake]}
                                      {:from [:command :knowledge/intake] :to [:event :knowledge/fact]}
                                      {:from [:event :knowledge/fact] :to [:read-model :knowledge/state]}
                                      {:from [:read-model :knowledge/state] :to [:query :knowledge/tasks]}
                                      {:from [:query :knowledge/tasks] :to nil}]}}})
