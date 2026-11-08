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
(defquery :knowledge taxonomy-item {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (if-let [value (get-in (state ctx) [:taxonomy-proposals (get-in ctx [:query :proposal-id])])]
    {:query/result value} (missing)))
(defquery :knowledge taxonomy-proposals {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (:taxonomy-proposals (state ctx)) vals (sort-by :proposal-id) vec)})
(defquery :knowledge taxonomy-current {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (:taxonomy-proposals (state ctx)) vals
                      (filter #(= :accepted (:status %))) (sort-by :proposal-id) vec)})
(defquery :knowledge taxonomy-relations {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (let [s (state ctx)]
    {:query/result (->> (:taxonomy-relations s) vals
                        (filter #(and (= :accepted (:status %))
                                      (= :accepted (get-in s [:taxonomy-proposals (:left-id %) :status]))
                                      (= :accepted (get-in s [:taxonomy-proposals (:right-id %) :status]))))
                        (sort-by :relation-id) vec)}))
(defquery :knowledge taxonomy-relation-item {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (if-let [value (get-in (state ctx) [:taxonomy-relations (get-in ctx [:query :relation-id])])]
    {:query/result value} (missing)))
(defquery :knowledge taxonomy-relation-proposals {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (:taxonomy-relations (state ctx)) vals (sort-by :relation-id) vec)})
(defquery :knowledge taxonomy-relation-outcomes {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (:taxonomy-relation-attempts (state ctx)) vals
                      (sort-by (juxt :recorded-at :result-id)) vec)})
(defquery :knowledge taxonomy-outcomes {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (:taxonomy-outcomes (state ctx)) vals (sort-by :result-id) vec)})
(defquery :knowledge quality-cases {:grain.event-model/reads #{:knowledge/state}} [ctx]
  {:query/result (->> (:quality-cases (state ctx)) vals (sort-by :case-id) vec)})
(defquery :knowledge quality-run-status {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (if-let [value (get-in (state ctx) [:quality-runs (get-in ctx [:query :run-id])])]
    {:query/result value} (missing)))
(defquery :knowledge workbench-view {:grain.event-model/reads #{:knowledge/state}} [ctx]
  (let [s (state ctx)]
    {:query/result {:cases (->> (:workbench-cases s) vals (sort-by :case-id) vec)
                    :metadata (->> (:workbench-metadata s) vals (mapcat vals)
                                   (sort-by (juxt :snapshot-id :legacy-id)) vec)}}))
