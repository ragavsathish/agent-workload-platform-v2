(ns awp-v2.knowledge.migration
  "Deterministic legacy snapshot import and reconciliation. Snapshot payloads stay outside the repository."
  (:require [clojure.set :as set]
            [awp-v2.knowledge.api :as api]
            [awp-v2.knowledge.read-models :as models]))

(defn empty-state [] {:snapshots {} :resources {} :read-source :source})
(defn- complete-provenance? [{:keys [owner source-id legacy-id]}]
  (every? #(and (string? %) (not (.isBlank ^String %))) [legacy-id owner source-id]))
(defn import-snapshot [state {:keys [snapshot-id digest resources]}]
  (when-not (and (string? snapshot-id) (string? digest) (vector? resources))
    (throw (ex-info "invalid snapshot manifest" {})))
  (when-let [prior (get-in state [:snapshots snapshot-id])]
    (when (not= digest (:digest prior))
      (throw (ex-info "snapshot identity conflicts with digest" {:snapshot-id snapshot-id}))))
  (reduce (fn [result resource]
            (let [key [snapshot-id (:legacy-id resource)]
                  mapped (assoc resource :snapshot-id snapshot-id
                                         :status (if (complete-provenance? resource) :imported :quarantined)
                                         :quarantine-reason (when-not (complete-provenance? resource) :provenance-unverified))]
              (if-let [prior (get-in result [:resources key])]
                (if (= prior mapped) result
                  (throw (ex-info "legacy resource conflicts with import" {:resource key})))
                (assoc-in result [:resources key] mapped))))
          (assoc-in state [:snapshots snapshot-id] {:digest digest :status :frozen})
          resources))
(defn accepted-facts [state owner]
  (->> (:resources state) vals
       (filter #(and (= owner (:owner %)) (= :imported (:status %)) (= :accepted (:kind %)))) vec))
(defn reconcile [state {:keys [snapshot-id digest resources]}]
  (let [expected (set (map :legacy-id resources))
        actual (set (for [[[sid id] _] (:resources state) :when (= sid snapshot-id)] id))
        missing (set/difference expected actual)
        extra (set/difference actual expected)
        changed (for [resource resources
                      :let [stored (get-in state [:resources [snapshot-id (:legacy-id resource)]])]
                      :when (and stored (not= (dissoc stored :snapshot-id :status :quarantine-reason) resource))]
                  (:legacy-id resource))
        discrepancies (vec (concat (map #(hash-map :kind :missing :legacy-id %) (sort missing))
                                   (map #(hash-map :kind :extra :legacy-id %) (sort extra))
                                   (map #(hash-map :kind :changed :legacy-id %) (sort changed))
                                   (when (not= digest (get-in state [:snapshots snapshot-id :digest]))
                                     [{:kind :snapshot-digest}])))]
    {:snapshot-id snapshot-id :matched? (empty? discrepancies) :discrepancies discrepancies
     :source-count (count resources) :imported-count (count actual)}))
(defn select-import [state report]
  (if (:matched? report) (assoc state :read-source :import) state))
(defn rollback [state] (assoc state :read-source :source))

(defn owner-resources [system owner snapshot-id]
  (->> (get-in (models/owner-state system owner) [:legacy-resources snapshot-id]) vals
       (sort-by :legacy-id) vec))
(defn import-into-grain! [system owner {:keys [snapshot-id digest resources]}]
  (let [snapshot-result (api/legacy-command! system owner :legacy-snapshot
                                              {:snapshot-id snapshot-id :digest digest})]
    (if (:error snapshot-result) snapshot-result
      (let [results (mapv (fn [resource]
                            (let [mapped (assoc resource
                                           :status (if (and (= owner (:owner resource))
                                                            (complete-provenance? resource))
                                                     :imported :quarantined)
                                           :quarantine-reason (when-not (and (= owner (:owner resource))
                                                                             (complete-provenance? resource))
                                                                :provenance-unverified))]
                              (api/legacy-command! system owner :legacy-resource
                                                   {:snapshot-id snapshot-id :legacy-id (:legacy-id resource)
                                                    :resource mapped}))) resources)]
        (if-let [failure (first (filter :error results))] failure
          {:status (if (and (= :reused (:status snapshot-result))
                            (every? #(= :reused (:status %)) results)) :reused :accepted)
           :snapshot-id snapshot-id :resources (count resources)})))))
(defn reconcile-grain [system owner {:keys [snapshot-id digest resources] :as snapshot}]
  (let [state (models/owner-state system owner)
        imported (get-in state [:legacy-resources snapshot-id])
        expected (set (map :legacy-id resources))
        actual (set (keys imported))
        changed (for [resource resources
                      :let [mapped (get imported (:legacy-id resource))]
                      :when (and mapped (not= (dissoc mapped :status :quarantine-reason) resource))]
                  (:legacy-id resource))
        discrepancies (vec (concat (map #(hash-map :kind :missing :legacy-id %) (sort (set/difference expected actual)))
                                   (map #(hash-map :kind :extra :legacy-id %) (sort (set/difference actual expected)))
                                   (map #(hash-map :kind :changed :legacy-id %) (sort changed))
                                   (when (not= digest (get-in state [:legacy-snapshots snapshot-id :digest]))
                                     [{:kind :snapshot-digest}])))]
    {:snapshot-id snapshot-id :matched? (empty? discrepancies) :discrepancies discrepancies
     :source-count (count resources) :imported-count (count actual)}))
(defn select-grain-import! [system owner {:keys [snapshot-id] :as snapshot}]
  (let [report (reconcile-grain system owner snapshot)]
    (if (:matched? report)
      (api/legacy-command! system owner :legacy-switch
                           {:snapshot-id snapshot-id :expected-ids (set (map :legacy-id (:resources snapshot)))})
      {:error :conflict :report report})))
(defn rollback-grain-import! [system owner snapshot-id]
  (api/legacy-command! system owner :legacy-rollback {:snapshot-id snapshot-id}))
