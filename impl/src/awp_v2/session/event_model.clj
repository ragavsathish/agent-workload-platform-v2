(ns awp-v2.session.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :session
  {:description "Current owner orchestrator conversation decisions and safe projections."
   :commands
   {:session/fresh {:description "Select a fresh conversation."
                    :schema [:map [:owner :string] [:data :map]]
                    :reads #{:session/state} :produces #{:session/fact}
                    :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                    :name "StartFreshConversation"}]}
    :session/resume {:description "Select saved conversation."
                     :schema [:map [:owner :string] [:data :map]]
                     :reads #{:session/state} :produces #{:session/fact}
                     :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                     :name "ResumeConversation"}]}
    :session/submit {:description "Route an owner turn."
                     :schema [:map [:owner :string] [:data :map]]
                     :reads #{:session/state} :produces #{:session/fact}
                     :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                     :name "AcceptOrchestratorTurn"}
                                    {:spec "specs/session-orchestration.allium" :kind :rule
                                     :name "RouteAttachedTaskTurn"}]}
    :session/settle {:description "Record controlled turn outcome."
                     :schema [:map [:owner :string] [:data :map]]
                     :reads #{:session/state} :produces #{:session/fact}
                     :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                     :name "SettleTurn"}]}
    :session/observe {:description "Record safe public observation."
                      :schema [:map [:owner :string] [:data :map]]
                      :reads #{:session/state} :produces #{:session/fact}
                      :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                      :name "PublishSafeTranscriptEntry"}]}
    :session/stop {:description "Request an in-flight interruption."
                   :schema [:map [:owner :string] [:data :map]]
                   :reads #{:session/state} :produces #{:session/fact}
                   :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                   :name "RequestTurnStop"}]}
    :session/compact {:description "Queue or settle context maintenance."
                      :schema [:map [:owner :string] [:data :map]]
                      :reads #{:session/state} :produces #{:session/fact}
                      :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                      :name "CompactSelectedContext"}]}
    :session/attach {:description "Route future turns to an owned task."
                     :schema [:map [:owner :string] [:data :map]]
                     :reads #{:session/state} :produces #{:session/fact}
                     :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                     :name "AttachOwnedTask"}]}
    :session/detach {:description "Release temporary task routing."
                     :schema [:map [:owner :string] [:data :map]]
                     :reads #{:session/state} :produces #{:session/fact}
                     :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                     :name "DetachTask"}]}
    :session/voice-ended {:description "Release routing when voice contact ends."
                          :schema [:map [:owner :string] [:data :map]]
                          :reads #{:session/state} :produces #{:session/fact}
                          :grain/allium [{:spec "specs/session-orchestration.allium" :kind :rule
                                          :name "DetachTask"}]}}
   :events
   {:session/fact {:description "Committed selection, turn, context, or attachment fact."
                   :schema [:map [:owner :string] [:kind :keyword] [:data :map]]}}
   :read-models
   {:session/state {:description "Owner-scoped session projections."
                    :consumes #{:session/fact}
                    :schema [:map-of :string :map] :version 1}}
   :queries
   {:session/selection {:description "Active selection."
                        :schema [:map [:owner :string]] :reads #{:session/state}}
    :session/history {:description "Safe saved history."
                      :schema [:map [:owner :string] [:conversation-id :string]]
                      :reads #{:session/state}}
    :session/transcript {:description "Safe live transcript."
                         :schema [:map [:owner :string]] :reads #{:session/state}}
    :session/catalog {:description "Bounded owner catalog."
                      :schema [:map [:owner :string]] :reads #{:session/state}}
    :session/context {:description "Context usage view."
                      :schema [:map [:owner :string]] :reads #{:session/state}}
    :session/routing {:description "Selected routing target."
                      :schema [:map [:owner :string]] :reads #{:session/state}}
    :session/routing-command {:description "One owner routing command receipt."
                              :schema [:map [:owner :string] [:command-id :string]]
                              :reads #{:session/state}}
    :session/turn {:description "One owner turn decision and outcome."
                   :schema [:map [:owner :string] [:turn-id :string]]
                   :reads #{:session/state}}}
   :flows
   {:session/orchestration
    {:description "Committed decisions rebuild owner reads."
     :steps [{:from nil :to [:command :session/fresh]}
             {:from [:command :session/fresh] :to [:event :session/fact]}
             {:from [:event :session/fact] :to [:read-model :session/state]}
             {:from [:read-model :session/state] :to [:query :session/selection]}
             {:from [:query :session/selection] :to nil}]}}})
