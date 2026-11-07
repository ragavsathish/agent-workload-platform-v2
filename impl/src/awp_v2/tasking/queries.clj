(ns awp-v2.tasking.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [awp-v2.tasking.commands :as commands]
            [awp-v2.tasking.lifecycle :as lifecycle]
            [awp-v2.tasking.read-models :as read-models]
            [awp-v2.tasking.schemas]
            [cognitect.anomalies :as anom]))

(defquery :tasking task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}}
  "Return one accepted task to its authenticated owner."
  [{:keys [actor-id] {:keys [request-id]} :query :as context}]
  (let [item (get (read-models/requests context) request-id)]
    (if (and item (= (commands/canonical-owner actor-id) (:owner item)))
      {:query/result (lifecycle/task-state context (:task-id item))}
      {::anom/category ::anom/not-found
       ::anom/message "task not found"})))
