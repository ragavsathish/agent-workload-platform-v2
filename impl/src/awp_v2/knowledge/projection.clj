(ns awp-v2.knowledge.projection
  "Rebuildable owner view; RDF serialization is derived from this data, never authoritative."
  (:require [awp-v2.knowledge.read-models :as models]
            [awp-v2.knowledge.legacy-view :as legacy]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(defn owner-view [system owner]
  (let [raw (models/owner-state system owner)
        state (if (= :import (:read-source raw)) (merge raw (legacy/interpretation raw)) raw)]
    {:owner owner :read-source (or (:read-source state) :source)
     :sources (or (:sources state) {}) :work (or (:work state) {})
     :items (or (:items state) {}) :accounts (or (:accounts state) {})
     :accepted (->> (:items state) vals
                    (filter #(and (= :assertion (:kind %)) (= :accepted (:status %))))
                    (sort-by :item-id) vec)}))
(defn- hash-id [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str value) "UTF-8"))))
(defn- literal [value]
  (str "\"" (-> (str value) (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")
                (str/replace "\n" "\\n") (str/replace "\r" "\\r")) "\""))
(defn graph-iri [owner] (str "urn:awp:v2:knowledge:owner:" (hash-id owner)))
(defn nquads [system owner]
  (let [{:keys [sources items accounts]} (owner-view system owner)
        graph (str "<" (graph-iri owner) ">")
        quad (fn [subject predicate object] (str "<" subject "> <" predicate "> " object " " graph " ."))
        source-lines (for [[id source] sources]
                       (quad (str "urn:awp:v2:source:" (hash-id (str owner ":" id)))
                             "https://schema.org/text" (literal (:transcript source))))
        item-lines (mapcat (fn [[id item]]
                             (let [sub (str "urn:awp:v2:item:" (hash-id (str owner ":" id)))]
                               [(quad sub "https://schema.org/identifier" (literal id))
                                (quad sub "https://schema.org/text" (literal (:current-text item)))
                                (quad sub "https://schema.org/isBasedOn" (literal (:source-id item)))
                                (quad sub "https://schema.org/actionStatus" (literal (name (:status item))))])) items)
        review-lines (mapcat (fn [[id item]]
                               (for [action (:reviews item)]
                                 (quad (str "urn:awp:v2:review:" (hash-id (str owner ":" (:action-id action))))
                                       "https://schema.org/isBasedOn" (literal id)))) items)
        account-lines (for [[id account] accounts :when (:story account)]
                        (quad (str "urn:awp:v2:account:" (hash-id (str owner ":" id)))
                              "https://schema.org/text" (literal (:story account))))]
    (str/join "\n" (sort (concat source-lines item-lines review-lines account-lines)))))

(defn sparql-update [system owner]
  (let [graph (graph-iri owner)
        lines (remove str/blank? (str/split-lines (nquads system owner)))
        triples (map #(str/replace % (str " <" graph "> .") " .") lines)]
    (str "CLEAR SILENT GRAPH <" graph ">; INSERT DATA { GRAPH <" graph "> {\n"
         (str/join "\n" triples) "\n} }")))
