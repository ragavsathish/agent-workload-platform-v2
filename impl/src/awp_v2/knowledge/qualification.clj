(ns awp-v2.knowledge.qualification)

(defn evaluate [cases {:keys [model prompt-version corpus-version]}]
  (when-not (and (seq cases) (every? string? [model prompt-version corpus-version]))
    (throw (ex-info "reviewed corpus and pinned versions required" {})))
  (let [false-additions (count (filter #(and (= :none (:expected-kind %)) (not= :none (:actual-kind %))) cases))
        missed (count (filter #(and (not= :none (:expected-kind %)) (= :none (:actual-kind %))) cases))
        correct (count (filter #(= (:expected-kind %) (:actual-kind %)) cases))
        evidence-errors (count (filter #(and (not= :none (:actual-kind %))
                                            (not= (:expected-evidence %) (:actual-evidence %))) cases))]
    {:status :open :model model :prompt-version prompt-version :corpus-version corpus-version
     :cases (count cases) :correct correct :false-additions false-additions
     :missed missed :evidence-errors evidence-errors
     :category-errors (- (count cases) correct false-additions missed)}))
(defn cutover-qualified? [{:keys [quality-gate migration-gate]}]
  (and (= :passed quality-gate) (= :passed migration-gate)))
