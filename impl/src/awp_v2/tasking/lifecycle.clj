(ns awp-v2.tasking.lifecycle
  (:require [ai.obney.grain.command-processor-v2.interface :as cp :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.query-processor.interface :as qp :refer [defquery]]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]
            [awp-v2.tasking.commands :as admission]
            [awp-v2.tasking.read-models :as accepted]
            [awp-v2.tasking.schemas]
            [clojure.string :as str]
            [cognitect.anomalies :as anom])
  (:import [java.time OffsetDateTime]))

(def transitions
  {:tasking/dispatch-task :dispatch
   :tasking/record-progress :progress
   :tasking/complete-task :complete
   :tasking/fail-task :fail
   :tasking/interrupt-task :interrupt
   :tasking/abort-task :abort
   :tasking/retry-task :retry})

(defn- refusal [category message]
  {::anom/category category ::anom/message message})

(defn- safe-summary [summary]
  (let [s (str/trim summary)]
    (if (re-find #"(?i)token|secret|password|reasoning|api.?key|\n|\r" s)
      "Progress updated."
      (subs s 0 (min 240 (count s))))))

(defreadmodel :tasking lifecycle
  {:events #{:tasking/task-transitioned}
   :version 1
   :schema [:map-of :uuid :map]}
  "Current task state and bounded progress from committed transitions."
  [state event]
  (let [id (:task-id event)
        prior (get state id {:status :pending :recovery-required? false :progress []})
        next (cond-> (assoc prior
                            :status (:status event)
                            :recovery-required? (:recovery-required? event)
                            :revision (:event/id event))
               (= :progress (:action event))
               (update :progress (fn [items]
                                   (->> (conj (vec items) (select-keys event [:cursor :summary]))
                                        (take-last 2000) vec)))
               (= :complete (:action event)) (assoc :result (:result event))
               (= :dispatch (:action event)) (assoc :source-revision (:source-revision event))
               (= :fail (:action event)) (assoc :failure (:failure event)))]
    (assoc state id next)))

(defn states [context]
  (rmp/project context :tasking/lifecycle))

(defn- accepted-task [context task-id]
  (some (fn [[_ task]] (when (= task-id (:task-id task)) task))
        (accepted/requests context)))

(defn task-state [context task-id]
  (when-let [task (accepted-task context task-id)]
    (merge task {:status :pending :recovery-required? false}
           (get (states context) task-id))))

(defn- transition [action {:keys [actor-id] {:keys [task-id workspace source-revision summary result failure]} :command :as context}]
  (let [task (task-state context task-id)
        status (:status task)
        owner? (= (admission/canonical-owner actor-id) (:owner task))
        protected? (#{:abort :retry} action)
        allowed? (case action
                   :dispatch (and (= status :pending) (not (:recovery-required? task))
                                  (= workspace (:workspace task)))
                   :progress (= status :running)
                   :complete (= status :running)
                   :fail (#{:pending :running} status)
                   :interrupt (= status :running)
                   :abort (#{:pending :running :sleeping} status)
                   :retry (and (= status :pending) (:recovery-required? task))
                   false)]
    (cond
      (nil? task) (refusal ::anom/not-found "task not found")
      (and protected? (not owner?)) (refusal ::anom/not-found "task not found")
      (and (= action :dispatch) (= status :running) (= workspace (:workspace task)))
      {:command/result {:task-id task-id :status :running}}
      (not allowed?) (refusal ::anom/conflict "task transition is not allowed")
      :else
      (let [cursor (inc (or (some-> task :progress last :cursor) 0))
            new-status (case action
                         :dispatch :running :progress :running
                         :complete :completed :fail :failed
                         :interrupt :pending :abort :cancelled :retry :pending)
            recovery? (= action :interrupt)
            body (cond-> {:task-id task-id :action action
                          :status new-status :recovery-required? recovery?}
                   (and (= action :dispatch) source-revision) (assoc :source-revision source-revision)
                   (= action :progress) (assoc :cursor cursor :summary (safe-summary summary))
                   (= action :complete) (assoc :result result)
                   (= action :fail) (assoc :failure failure))
            revision (:revision task)]
        {:command-result/events
         [(es/->event {:type :tasking/task-transitioned
                       :tags #{[:task task-id]} :body body})]
         :command-result/cas
         {:tags #{[:task task-id]}
          :predicate-fn (fn [events]
                          (= revision (:event/id (last (into [] events)))))}
         :command/result (merge {:task-id task-id :status new-status}
                                (select-keys body [:cursor]))}))))

(defcommand :tasking dispatch-task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :dispatch context))
(defcommand :tasking record-progress
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :progress context))
(defcommand :tasking complete-task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :complete context))
(defcommand :tasking fail-task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :fail context))
(defcommand :tasking interrupt-task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :interrupt context))
(defcommand :tasking abort-task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :abort context))
(defcommand :tasking retry-task
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}
   :grain.event-model/produces #{:tasking/task-transitioned}}
  [context] (transition :retry context))

(defquery :tasking progress
  {:grain.event-model/reads #{:tasking/requests :tasking/lifecycle}}
  [{:keys [actor-id] {:keys [task-id after limit]} :query :as context}]
  (let [task (task-state context task-id)]
    (if (and task (= (admission/canonical-owner actor-id) (:owner task)))
      {:query/result (->> (:progress task)
                          (filter #(> (:cursor %) after))
                          (take (max 1 (min 100 limit))) vec)}
      (refusal ::anom/not-found "task not found"))))

(defn issue! [context name params]
  (loop [attempt 0]
    (let [command (assoc params :command/name name
                         :command/id (random-uuid)
                         :command/timestamp (OffsetDateTime/now))
          result (cp/process-command (assoc context :command command))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result)) (< attempt 4))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (or (:command/result result) result)))))

(defn progress-query [context task-id after limit]
  (let [result (qp/process-query
                 (assoc context :query {:query/name :tasking/progress
                                        :query/id (random-uuid)
                                        :query/timestamp (OffsetDateTime/now)
                                        :task-id task-id :after after :limit limit}))]
    (or (:query/result result) result)))
