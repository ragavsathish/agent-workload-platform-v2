(ns awp-v2.tasking.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas commands
  {:tasking/submit-task
   [:map [:request-id :uuid] [:owner :string]
    [:instruction :string] [:workspace :string]]
   :tasking/dispatch-task [:map [:task-id :uuid] [:workspace :string] [:source-revision {:optional true} :string]]
   :tasking/record-progress [:map [:task-id :uuid] [:summary :string]]
   :tasking/complete-task [:map [:task-id :uuid] [:result :any]]
   :tasking/fail-task [:map [:task-id :uuid] [:failure :string]]
   :tasking/interrupt-task [:map [:task-id :uuid]]
   :tasking/abort-task [:map [:task-id :uuid]]
   :tasking/retry-task [:map [:task-id :uuid]]})

(defevent :tasking/task-accepted
  "One canonical owner request was admitted."
  {:schema [:map [:request-id :uuid] [:task-id :uuid]
            [:owner :string] [:instruction :string] [:workspace :string]]})

(defevent :tasking/task-transitioned
  "Durable controlled-worker transition."
  {:schema [:map [:task-id :uuid] [:action :keyword]
            [:status :keyword] [:recovery-required? :boolean]
            [:cursor {:optional true} :int]
            [:summary {:optional true} :string]
            [:source-revision {:optional true} :string]
            [:result {:optional true} :any]
            [:failure {:optional true} :string]]})

(defschemas queries
  {:tasking/task [:map [:request-id :uuid]]
   :tasking/progress [:map [:task-id :uuid] [:after :int] [:limit :int]]
   :tasking/list [:map]
   :tasking/artifact [:map [:task-id :uuid] [:kind :keyword]]})
