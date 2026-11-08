(ns awp-v2.deployment.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.deployment.read-models :as models]
            [awp-v2.deployment.schemas]
            [cognitect.anomalies :as anom])
  (:import [java.util UUID]))
(def ^:private levels #{:voice :durable :full})
(defn- refuse [reason] {::anom/category ::anom/conflict ::anom/message (name reason)})
(defn- emit [owner state kind data result]
  (let [tag [:deployment-owner (UUID/nameUUIDFromBytes (.getBytes owner "UTF-8"))]
        revision (or (:revision state) 0)]
    {:command-result/events [(es/->event {:type :deployment/fact :tags #{tag}
                                           :body {:owner owner :kind kind :data data}})]
     :command-result/cas {:tags #{tag} :predicate-fn (fn [events] (= revision (count (into [] events))))}
     :command/result result}))
(defcommand :deployment select {:grain.event-model/reads #{:deployment/state} :grain.event-model/produces #{:deployment/fact}}
  [{:keys [actor-id] {:keys [owner data]} :command :as ctx}]
  (let [state (models/owner-state ctx owner) prior (:selection-id state)
        level (some-> (:level data) keyword)]
    (cond
      (not= owner actor-id) {::anom/category ::anom/not-found ::anom/message "not-found"}
      (not (contains? levels level)) (refuse :invalid-level)
      (= level (:requested-level state)) {:command/result {:status :reused :level level}}
      (and (= prior (:selection-id data)) (not= level (:requested-level state))) (refuse :conflict)
      :else (emit owner state :selected {:selection-id (:selection-id data) :level level}
                  {:status :selected :level level}))))
(defcommand :deployment observe {:grain.event-model/reads #{:deployment/state} :grain.event-model/produces #{:deployment/fact}}
  [{:keys [actor-id] {:keys [owner data]} :command :as ctx}]
  (let [state (models/owner-state ctx owner)]
    (if (not= owner actor-id)
      {::anom/category ::anom/not-found ::anom/message "not-found"}
      (emit owner state :observed data {:status :observed}))))
