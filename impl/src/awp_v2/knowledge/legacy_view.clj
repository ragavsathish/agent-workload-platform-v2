(ns awp-v2.knowledge.legacy-view
  (:require [clojure.string :as str]))

(defn- subject [resource] (get-in resource [:value :subject]))
(defn- rdf-types [resource]
  (->> (get-in resource [:value :quads])
       (filter #(= "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" (get-in % [:p :value])))
       (map #(get-in % [:o :value])) set))
(defn- has-type? [resource suffix]
  (some #(str/ends-with? % suffix) (rdf-types resource)))
(defn semantic-kind [resource]
  (let [graph (or (get-in resource [:value :graph]) "")
        value (:value resource)]
    (cond
      (has-type? resource "DailyJournal") :daily
      (has-type? resource "ReviewAction") :review-action
      (and (has-type? resource "Claim") (str/ends-with? graph ":accepted")) :accepted
      (and (has-type? resource "Claim") (str/ends-with? graph ":review")) :candidate
      (and (str/ends-with? graph ":journal")
           (has-type? resource "CreativeWork")
           (:text value)
           (some #(= "https://schema.org/additionalType" (get-in % [:p :value]))
                 (:quads value))) :journal
      :else nil)))
(defn- review-order [resource]
  [(or (get-in resource [:value :createdAt]) "") (:legacy-id resource)])
(defn- property-value [resource predicate]
  (some (fn [quad]
          (when (= predicate (get-in quad [:p :value]))
            (get-in quad [:o :value])))
        (get-in resource [:value :quads])))
(defn interpretation [state]
  (let [records (vec (->> (get-in state [:legacy-resources (:selected-snapshot state)]) vals
                          (filter #(= :imported (:status %)))))
        accepted-subjects (set (map subject (filter #(= :accepted (semantic-kind %)) records)))
        actions (->> records (filter #(= :review-action (semantic-kind %)))
                     (group-by #(get-in % [:value :target])))
        items (into {} (for [resource records
                             :let [semantic (semantic-kind resource)]
                             :when (#{:journal :candidate :accepted} semantic)
                             :let [legacy-id (:legacy-id resource)
                                   value (:value resource)
                                   history (->> (get actions (subject resource))
                                                (sort-by review-order)
                                                (mapv #(select-keys % [:legacy-id :source-id :value])))] ]
                         [legacy-id {:item-id legacy-id :owner (:owner resource) :source-id (:source-id resource)
                                     :kind (if (= :journal semantic) :journal :assertion)
                                     :legacy-category (property-value resource "https://schema.org/additionalType")
                                     :original-text (or (:text value) (:label value))
                                     :current-text (or (:text value) (:label value))
                                     :status (cond
                                               (= "deleted" (:status value)) :deleted
                                               (or (= :accepted semantic)
                                                   (contains? accepted-subjects (subject resource))
                                                   (= "accepted" (:status value))) :accepted
                                               :else :pending)
                                     :reviews history :legacy? true}]))
        accounts (into {} (for [resource records :when (= :daily (semantic-kind resource))]
                            [(:legacy-id resource) {:account-id (:legacy-id resource)
                                                    :owner (:owner resource) :source-refs (get-in resource [:value :sourceRefs])
                                                    :journal-date (property-value resource "urn:voice-agent:personal-knowledge:ontology#journalDate")
                                                    :time-zone (property-value resource "urn:voice-agent:personal-knowledge:ontology#timeZone")
                                                    :legacy-version-id (property-value resource "https://schema.org/identifier")
                                                    :story (get-in resource [:value :text]) :status :completed :legacy? true}]))]
    {:items items :accounts accounts}))
