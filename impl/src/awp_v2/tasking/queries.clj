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

(defquery :tasking list
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}}
  "List durable tasks belonging to the authenticated owner."
  [{:keys [actor-id] :as context}]
  {:query/result
   (->> (read-models/requests context)
        vals
        (filter #(= (commands/canonical-owner actor-id) (:owner %)))
        (map #(lifecycle/task-state context (:task-id %)))
        (sort-by (comp str :task-id))
        vec)})

(defquery :tasking artifact
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}}
  "Return a declared artifact reference to its authenticated owner."
  [{:keys [actor-id] {:keys [task-id kind]} :query :as context}]
  (let [task (lifecycle/task-state context task-id)
        kind-name (name kind)
        expected (if (= "scratch" (:workspace task)) "workspace" "patch")
        descriptor (get-in task [:result :artifacts kind-name])]
    (if (and task
             (= (commands/canonical-owner actor-id) (:owner task))
             (= :completed (:status task))
             (= expected kind-name)
             descriptor)
      {:query/result descriptor}
      {::anom/category ::anom/not-found
       ::anom/message "task artifact is unavailable"})))
