(ns awp-v2.tasking.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(defreadmodel :tasking requests
  {:events #{:tasking/task-accepted}
   :version 1
   :schema [:map-of :uuid
            [:map [:request-id :uuid] [:task-id :uuid]
             [:owner :string] [:instruction :string] [:workspace :string]]]}
  "Accepted requests by stable identity."
  [state event]
  (assoc state (:request-id event)
         (select-keys event [:request-id :task-id :owner :instruction :workspace])))

(defn requests [context]
  (rmp/project context :tasking/requests))
