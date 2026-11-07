(ns awp-v2.knowledge.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [awp-v2.knowledge.read-models :as models]
            [awp-v2.knowledge.legacy-view :as legacy]
            [awp-v2.knowledge.schemas]
            [cognitect.anomalies :as anom]))

(defn- missing [] {::anom/category ::anom/not-found ::anom/message "not-found"})
(defn- state [{:keys [actor-id] {:keys [owner]} :query :as ctx}]
  (when (= actor-id owner) (models/owner-state ctx owner)))
(defn- active [state]
  (if (= :import (:read-source state)) (merge state (legacy/interpretation state)) state))
(defquery :knowledge tasks {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (get-in (state ctx) [:work (get-in ctx [:query :source-id])]) vals (sort-by :mode) vec)})
(defquery :knowledge suggestions {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (get (active (state ctx)) :items) vals (sort-by :item-id) vec)})
(defquery :knowledge item {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (if-let [item (get-in (active (state ctx)) [:items (get-in ctx [:query :item-id])])]
    {:query/result item} (missing)))
(defquery :knowledge profile {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (get (active (state ctx)) :items) vals
                      (filter #(and (= :assertion (:kind %)) (= :accepted (:status %))))
                      (sort-by :item-id) vec)})
(defquery :knowledge account {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (if-let [account (get-in (active (state ctx)) [:accounts (get-in ctx [:query :account-id])])]
    {:query/result account} (missing)))
(defquery :knowledge accounts {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (get (active (state ctx)) :accounts) vals (sort-by :account-id) vec)})
