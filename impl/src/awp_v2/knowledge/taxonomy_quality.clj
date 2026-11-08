(ns awp-v2.knowledge.taxonomy-quality
  (:require [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def ^:private metrics [:false-additions :misses :excerpt-errors :category-errors :wrong-relations])
(defn- sha256 [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (pr-str value) "UTF-8"))))

(defn- count-errors [cases]
  (reduce
   (fn [totals {:keys [source gold predicted gold-relations predicted-relations]}]
     (let [gold (or gold []) predicted (or predicted [])
           gold-evidence (set (map :evidence gold))
           predicted-evidence (set (map :evidence predicted))
           matched (for [p predicted g gold :when (= (:evidence p) (:evidence g))] [p g])]
       (-> totals
           (update :false-additions + (count (remove #(contains? gold-evidence (:evidence %)) predicted)))
           (update :misses + (count (remove #(contains? predicted-evidence (:evidence %)) gold)))
           (update :excerpt-errors + (count (remove #(and (string? (:evidence %))
                                                        (not (str/blank? (:evidence %)))
                                                        (str/includes? (or source "") (:evidence %))) predicted)))
           (update :category-errors + (count (filter (fn [[p g]] (not= (:category-id p) (:category-id g))) matched)))
           (update :wrong-relations + (count (remove (set (or gold-relations [])) (or predicted-relations [])))))))
   (zipmap metrics (repeat 0)) cases))

(defn evaluate [{:keys [corpus-id corpus-digest reviewed? review-records thresholds
                        thresholds-committed-at model-version prompt-version taxonomy-version cases] :as input}]
  (let [counts (count-errors cases)
        required (into {} (map (fn [m] [(keyword (str "max-" (name m))) m]) metrics))
        qualified-input? (and reviewed? (string? corpus-id) (not (str/blank? corpus-id))
                              (= corpus-digest (str "sha256:" (sha256 cases)))
                              (string? thresholds-committed-at) (not (str/blank? thresholds-committed-at))
                              (= (set (map :id cases)) (set (map :case-id review-records)))
                              (= (count cases) (count review-records))
                              (every? #(and (string? (:reviewer-id %))
                                            (not (str/blank? (:reviewer-id %)))
                                            (= :approved (:status %))) review-records)
                              (string? model-version) (not (str/blank? model-version))
                              (string? prompt-version) (not (str/blank? prompt-version))
                              (string? taxonomy-version) (not (str/blank? taxonomy-version))
                              (seq cases) (every? #(and (contains? thresholds %)
                                                        (integer? (get thresholds %))
                                                        (<= 0 (get thresholds %))) (keys required)))]
    (merge (select-keys input [:corpus-id :corpus-digest :model-version :prompt-version :taxonomy-version])
           {:case-count (count cases) :reviewed? (boolean reviewed?) :thresholds thresholds
            :thresholds-committed-at thresholds-committed-at}
           counts
           {:status (if qualified-input?
                      (if (every? (fn [[limit metric]] (<= (get counts metric) (get thresholds limit))) required)
                        :passed :failed)
                      :open)})))
