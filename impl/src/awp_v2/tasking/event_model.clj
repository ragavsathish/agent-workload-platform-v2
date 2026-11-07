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
                    {:spec "specs/tasking.allium" :kind :rule :name "ReuseTask"}]}}
   :events
   {:tasking/task-accepted
    {:description "A request identity was bound to a task."
     :schema [:map [:request-id :uuid] [:task-id :uuid]
              [:owner :string] [:instruction :string] [:workspace :string]]}}
   :read-models
   {:tasking/requests
    {:description "Accepted requests by stable identity."
     :consumes #{:tasking/task-accepted}
     :schema [:map-of :uuid
              [:map [:request-id :uuid] [:task-id :uuid]
               [:owner :string] [:instruction :string] [:workspace :string]]]
     :version 1}}
   :queries
   {:tasking/task
    {:description "Read an accepted task for its owner."
     :schema [:map [:request-id :uuid]]
     :reads #{:tasking/requests}}}
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
             {:from [:query :tasking/task] :to nil}]}}})
