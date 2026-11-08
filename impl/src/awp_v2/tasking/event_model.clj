(ns awp-v2.tasking.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :tasking
  {:description "One owner request admits one task identity."
   :commands
   {:tasking/submit-task
    {:description "Admit or reuse an eligible canonical request."
     :schema [:map [:request-id :uuid] [:owner :string]
              [:instruction :string] [:workspace :string]]
     :reads #{:tasking/requests}
     :produces #{:tasking/task-accepted}
     :grain/allium [{:spec "specs/tasking.allium" :kind :rule :name "AdmitTask"}
                    {:spec "specs/tasking.allium" :kind :rule :name "ReuseTask"}]}
    :tasking/dispatch-task {:description "Start controlled work." :schema [:map [:task-id :uuid] [:workspace :string]]
                            :reads #{:tasking/requests :tasking/lifecycle}
                            :produces #{:tasking/task-transitioned}
                            :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "StartExecution"}]}
    :tasking/record-progress {:description "Record public progress." :schema [:map [:task-id :uuid] [:summary :string]]
                              :reads #{:tasking/requests :tasking/lifecycle}
                              :produces #{:tasking/task-transitioned}
                              :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "RecordProgress"}]}
    :tasking/complete-task {:description "Complete controlled work." :schema [:map [:task-id :uuid] [:result :any]]
                            :reads #{:tasking/requests :tasking/lifecycle}
                            :produces #{:tasking/task-transitioned}
                            :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "CompleteTask"}
                                           {:spec "specs/task-result-delivery.allium" :kind :rule :name "CaptureRepositoryPatch"}
                                           {:spec "specs/task-result-delivery.allium" :kind :rule :name "CaptureScratchWorkspace"}]}
    :tasking/fail-task {:description "Fail controlled work." :schema [:map [:task-id :uuid] [:failure :string]]
                        :reads #{:tasking/requests :tasking/lifecycle}
                        :produces #{:tasking/task-transitioned}
                        :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "FailTask"}
                                       {:spec "specs/task-result-delivery.allium" :kind :rule :name "FailCapture"}]}
    :tasking/interrupt-task {:description "Park interrupted work." :schema [:map [:task-id :uuid]]
                             :reads #{:tasking/requests :tasking/lifecycle}
                             :produces #{:tasking/task-transitioned}
                             :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "ParkInterruptedTask"}]}
    :tasking/abort-task {:description "Abort owned work." :schema [:map [:task-id :uuid]]
                         :reads #{:tasking/requests :tasking/lifecycle}
                         :produces #{:tasking/task-transitioned}
                         :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "AbortPendingTask"}
                                        {:spec "specs/task-lifecycle.allium" :kind :rule :name "AbortRunningTask"}]}
    :tasking/retry-task {:description "Retry parked work." :schema [:map [:task-id :uuid]]
                         :reads #{:tasking/requests :tasking/lifecycle}
                         :produces #{:tasking/task-transitioned}
                         :grain/allium [{:spec "specs/task-lifecycle.allium" :kind :rule :name "RetryParkedTask"}]}}
   :events
   {:tasking/task-accepted
    {:description "A request identity was bound to a task."
     :schema [:map [:request-id :uuid] [:task-id :uuid]
              [:owner :string] [:instruction :string] [:workspace :string]]}
    :tasking/task-transitioned
    {:description "One durable controlled-worker transition."
     :schema [:map [:task-id :uuid] [:action :keyword]
              [:status :keyword] [:recovery-required? :boolean]
              [:cursor {:optional true} :int]
              [:summary {:optional true} :string]
              [:result {:optional true} :any]
              [:failure {:optional true} :string]]}}
   :read-models
   {:tasking/requests
    {:description "Accepted requests by stable identity."
     :consumes #{:tasking/task-accepted}
     :schema [:map-of :uuid
              [:map [:request-id :uuid] [:task-id :uuid]
               [:owner :string] [:instruction :string] [:workspace :string]]]
     :version 1}
    :tasking/lifecycle
    {:description "Current state and bounded progress."
     :consumes #{:tasking/task-transitioned}
     :schema [:map-of :uuid :map]
     :version 1}}
   :queries
   {:tasking/task
    {:description "Read an accepted task for its owner."
     :schema [:map [:request-id :uuid]]
     :reads #{:tasking/requests :tasking/lifecycle}}
    :tasking/progress
    {:description "Bounded owner-visible progress."
     :schema [:map [:task-id :uuid] [:after :int] [:limit :int]]
     :reads #{:tasking/requests :tasking/lifecycle}}}
   :flows
   {:tasking/admission
    {:description "An accepted request is visible under its identity."
     :steps [{:from nil :to [:command :tasking/submit-task]}
             {:from [:command :tasking/submit-task]
              :to [:event :tasking/task-accepted]}
             {:from [:event :tasking/task-accepted]
              :to [:read-model :tasking/requests]}
             {:from [:read-model :tasking/requests]
              :to [:query :tasking/task]}
             {:from [:query :tasking/task] :to nil}]}
    :tasking/lifecycle
    {:description "Controlled work changes projected task state."
     :steps [{:from nil :to [:command :tasking/dispatch-task]}
             {:from [:command :tasking/dispatch-task] :to [:event :tasking/task-transitioned]}
             {:from [:event :tasking/task-transitioned] :to [:read-model :tasking/lifecycle]}
             {:from [:read-model :tasking/lifecycle] :to [:query :tasking/progress]}
             {:from [:query :tasking/progress] :to nil}]}}})
