(ns awp-v2.deployment.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :deployment
  {:description "Requested operating level and observed service readiness."
   :commands {:deployment/select {:description "Select a deployment level."
                                  :schema [:map [:owner :string] [:data :map]]
                                  :reads #{:deployment/state} :produces #{:deployment/fact}
                                  :grain/allium [{:spec "specs/deployment-levels.allium" :kind :rule :name "SelectDeploymentLevel"}]}
              :deployment/observe {:description "Record observed service capabilities."
                                   :schema [:map [:owner :string] [:data :map]]
                                   :reads #{:deployment/state} :produces #{:deployment/fact}
                                   :grain/allium [{:spec "specs/deployment-levels.allium" :kind :rule :name "ObserveReadyCapabilities"}]}}
   :events {:deployment/fact {:description "Requested level or observed deployment health."
                              :schema [:map [:owner :string] [:kind :keyword] [:data :map]]}}
   :read-models {:deployment/state {:description "Requested and observed operating level."
                                   :consumes #{:deployment/fact} :schema [:map-of :string :map] :version 1}}
   :queries {:deployment/status {:description "Read deployment level and service status."
                                 :reads #{:deployment/state}
                                 :schema [:map [:owner :string]]}}
   :flows {:deployment/selection {:description "Level selection is persisted before capability effects."
                                  :steps [{:from nil :to [:command :deployment/select]}
                                          {:from [:command :deployment/select] :to [:event :deployment/fact]}
                                          {:from [:event :deployment/fact] :to [:read-model :deployment/state]}
                                          {:from [:read-model :deployment/state] :to [:query :deployment/status]}]}}})
