(ns awp-v2.incident.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(defn- apply-fact [state {:keys [kind data]}]
  (case kind
    :opened (-> state
                (assoc-in [:epochs (:epoch-id data)]
                          (assoc data :active? true :submission-status :pending))
                (assoc-in [:latest (:key data)] (:epoch-id data)))
    :repeated (assoc-in state [:epochs (:epoch-id data) :last-seen] (:at data))
    :reactivated (-> state
                     (assoc-in [:epochs (:epoch-id data) :active?] true)
                     (assoc-in [:epochs (:epoch-id data) :last-seen] (:at data)))
    :resolved (assoc-in state [:epochs (:epoch-id data) :active?] false)
    :submitted (-> state
                   (assoc-in [:epochs (:epoch-id data) :submission-status] :accepted)
                   (assoc-in [:epochs (:epoch-id data) :task-id] (:task-id data))
                   (assoc-in [:epochs (:epoch-id data) :last-submitted] (:at data)))
    :failure (-> state
                 (update :failures (fnil inc 0))
                 (assoc :freshness :stale :last-poll-at (:at data)))
    :success (assoc state :failures 0 :freshness :current :last-poll-at (:at data))
    state))

(defreadmodel :incident state
  {:events #{:incident/fact} :version 1 :schema [:map-of :string :map]}
  "Owner incident epochs, requests, and alert-source freshness."
  [state event]
  (update state (:owner event)
          (fn [owner-state]
            (-> (apply-fact (or owner-state {}) event)
                (update :revision (fnil inc 0))))))

(defn owners [context] (rmp/project context :incident/state))
(defn owner-state [context owner] (get (owners context) owner))
