(ns awp-v2.tasking.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas commands
  {:tasking/submit-task
   [:map [:request-id :uuid] [:owner :string]
    [:instruction :string] [:workspace :string]]})

(defevent :tasking/task-accepted
  "One canonical owner request was admitted."
  {:schema [:map [:request-id :uuid] [:task-id :uuid]
            [:owner :string] [:instruction :string] [:workspace :string]]})

(defschemas queries
  {:tasking/task [:map [:request-id :uuid]]})
