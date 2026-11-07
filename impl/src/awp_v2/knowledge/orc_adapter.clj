(ns awp-v2.knowledge.orc-adapter
  "ORC is a rebuildable concept index. Grain knowledge decisions remain authoritative."
  (:require [ai.obney.orc.ontology.interface :as ontology]
            [awp-v2.knowledge.projection :as projection]
            [awp-v2.knowledge.read-models :as models]
            [clojure.string :as str])
  (:import [java.security MessageDigest]
           [java.util UUID]))

(defn- stable-uuid [value]
  (UUID/nameUUIDFromBytes (.getBytes (str value) "UTF-8")))

(defn- digest [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str value) "UTF-8"))))

(defn ontology-id [owner] (stable-uuid (str "awp-v2:knowledge:ontology:" owner)))
(defn candidate-ontology-id [owner source-id]
  (stable-uuid (str "awp-v2:knowledge:candidates:" owner ":" source-id)))
(defn item-uri [owner item-id]
  (str "urn:awp:v2:knowledge:item:" (digest (str owner ":" item-id))))

(defn- failed! [result]
  (when (:cognitect.anomalies/category result)
    (throw (ex-info "ORC ontology projection failed" {:result result})))
  result)

(defn- ensure-ontology! [system owner]
  (let [id (ontology-id owner)]
    (when-not (ontology/ontology-exists? system id)
      (failed! (ontology/create-ontology!
                system {:command/id id :name (str "AWP owner " (digest owner))
                        :scope :custom :description "Owner-scoped derived AWP knowledge concepts"
                        :base-uri (str "urn:awp:v2:knowledge:owner:" (digest owner))})))
    id))

(defn- concept-description [item]
  (str "kind=" (name (:kind item)) "; status=" (name (:status item))
       "; source=" (:source-id item) "; evidence=" (:evidence item)))

(defn sync-owner! [system owner]
  (let [id (ensure-ontology! system owner)
        items (-> (projection/owner-view system owner) :items)]
    (doseq [[item-id item] (sort-by key items)
            :when (and (not (str/blank? (str (:current-text item))))
                       (#{:journal :assertion} (:kind item)))]
      (let [uri (item-uri owner item-id)
            label (:current-text item)
            description (concept-description item)
            existing (ontology/get-concept-by-uri system id uri)]
        (if existing
          (when (or (not= label (:label existing))
                    (not= description (:description existing)))
            (failed! (ontology/update-concept!
                       system id (:id existing)
                       {:label label :description description}
                       {:command/id (stable-uuid (str uri ":" label ":" description))})))
          (failed! (ontology/create-concept!
                    system id {:command/id (stable-uuid uri)
                               :uri uri :label label :description description
                               :scope :custom
                               :provenance {:kind :source-extracted
                                            :owner-hash (digest owner)
                                            :source-id (:source-id item)
                                            :item-id item-id
                                            :result-id (:result-id item)}})))))
    {:ontology-id id :item-count (count items)}))

(defn owner-concepts
  "Scoped lookup of current Grain items. Rejected items cannot reappear through ORC."
  [system owner]
  (let [{:keys [items]} (projection/owner-view system owner)
        id (ontology-id owner)]
    (if-not (ontology/ontology-exists? system id)
      []
      (->> items
           (filter (fn [[_ item]] (not= :rejected (:status item))))
           (sort-by key)
           (keep (fn [[item-id item]]
                   (when-let [concept (ontology/get-concept-by-uri system id (item-uri owner item-id))]
                     (assoc concept :awp-status (:status item) :awp-source-id (:source-id item)))))
           vec))))

(defn explore-source-candidates!
  "Explicit, gated ORC model exploration. Its separate ontology is never a published review view."
  [system owner source-id model]
  (let [source (get-in (models/owner-state system owner) [:sources source-id])]
    (when-not (and source (= source-id (:source-id source))
                   (string? model) (not (str/blank? model)))
      (throw (ex-info "owned source and model required" {:source-id source-id})))
    (ontology/build-ontology-from-sources
      system {:sources [{:type "text" :content (:transcript source)
                         :source-ref source-id}]
              :config {:ontology-id (candidate-ontology-id owner source-id)
                       :name (str "AWP candidate " (digest (str owner ":" source-id)))
                       :base-uri (str "urn:awp:v2:knowledge:candidate:" (digest (str owner ":" source-id)) ":")
                       :domain "Owner knowledge journal and assertion candidates"
                       :model model :enable-colbert? false :enable-embeddings? false}})))
