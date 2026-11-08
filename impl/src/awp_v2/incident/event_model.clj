(ns awp-v2.incident.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))
(defeventmodel :incident
  {:description "Managed alert epochs and stable investigation intent."
   :commands (into {} (for [[op rule] {:observe "OpenManagedIncident"
                                        :resolve "ResolveAbsentIncident"
                                        :failure "RecordAlertSourceFailure"
                                        :success "RecordAlertSourceRecovery"
                                        :submitted "ConfirmInvestigationTask"}]
                        [(keyword "incident" (name op))
                         {:description (name op) :schema [:map [:owner :string] [:data :map]]
                          :reads #{:incident/state} :produces #{:incident/fact}
                          :grain/allium [{:spec "specs/operational-incidents.allium" :kind :rule :name rule}]}]))
   :events {:incident/fact {:description "Owner incident decision or observation."
                            :schema [:map [:owner :string] [:kind :keyword] [:data :map]]}}
   :read-models {:incident/state {:description "Owner incident epochs and alert freshness."
                                  :consumes #{:incident/fact} :schema [:map-of :string :map] :version 1}}
   :queries {:incident/get {:description "Read one owned incident."
                            :schema [:map [:owner :string] [:epoch-id :string]]
                            :reads #{:incident/state}}
             :incident/list {:description "List owned incidents."
                             :schema [:map [:owner :string]] :reads #{:incident/state}}}
   :flows {:incident/investigation {:description "A managed alert becomes one owner-visible request."
                                    :steps [{:from nil :to [:command :incident/observe]}
                                            {:from [:command :incident/observe] :to [:event :incident/fact]}
                                            {:from [:event :incident/fact] :to [:read-model :incident/state]}
                                            {:from [:read-model :incident/state] :to [:query :incident/get]}
                                            {:from [:query :incident/get] :to nil}]}}})
