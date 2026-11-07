(ns awp-v2.tasking.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.tasking.read-models :as read-models]
            [awp-v2.tasking.schemas]
            [clojure.string :as str]
            [cognitect.anomalies :as anom]))

(defn canonical-owner [owner]
  (when (string? owner) (str/lower-case (str/trim owner))))

(defn canonical-input [{:keys [owner instruction workspace]}]
  {:owner (canonical-owner owner)
   :instruction (when (string? instruction) (str/trim instruction))
   :workspace (when (string? workspace) (str/trim workspace))})

(defn- refusal [category message]
  {::anom/category category ::anom/message message})

(defcommand :tasking submit-task
  {:grain.event-model/reads #{:tasking/requests}
   :grain.event-model/produces #{:tasking/task-accepted}}
  "Admit a bounded task request or reuse its accepted identity."
  [{:keys [actor-id eligible-workspaces] {:keys [request-id] :as command} :command :as context}]
  (let [{:keys [owner instruction workspace] :as input} (canonical-input command)
        prior (get (read-models/requests context) request-id)]
    (cond
      (or (nil? actor-id) (not= owner (canonical-owner actor-id)))
      (refusal ::anom/forbidden "owner is not authorized")

      (or (str/blank? instruction)
          (str/blank? workspace)
          (not (contains? eligible-workspaces workspace)))
      (refusal ::anom/incorrect "instruction or workspace is ineligible")

      prior
      (if (= input (select-keys prior [:owner :instruction :workspace]))
        {:command/result {:task-id (:task-id prior) :reused? true}}
        (refusal ::anom/conflict "request identity has different input"))

      :else
      {:command-result/events
       [(es/->event {:type :tasking/task-accepted
                     :tags #{[:task-request request-id]}
                     :body (assoc input :request-id request-id :task-id request-id)})]
       :command-result/cas {:tags #{[:task-request request-id]}
                            :predicate-fn (fn [events] (empty? (into [] events)))}
       :command/result {:task-id request-id :reused? false}})))
