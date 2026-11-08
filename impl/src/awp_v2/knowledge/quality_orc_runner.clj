(ns awp-v2.knowledge.quality-orc-runner
  (:gen-class)
  (:require [clojure.data.json :as json]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.orc-adapter :as orc]))

(defn run-one [{:strs [id owner sourceText modelId branch endpointConcepts promptId policyId]}]
  (let [sys (knowledge/open-system {})
        source-id (str "quality:" id)]
    (try
      (knowledge/ingest-turn! sys owner {:source-id source-id :conversation-id "quality"
                                         :message-id id :transcript sourceText
                                         :captured-at "2026-11-08T00:00:00Z"
                                         :status :completed :origin :owner})
      (if (= "relation" branch)
        (do
          (when-not (and (vector? endpointConcepts) (= 2 (count endpointConcepts))
                         (= 2 (count (set (map #(get % "id") endpointConcepts)))))
            (throw (ex-info "invalid relation endpoints" {})))
          (doseq [endpoint endpointConcepts]
            (let [concept-id (get endpoint "id")
                  evidence (get endpoint "evidence")]
              (when-not (and (string? concept-id) (string? evidence)
                             (.contains ^String sourceText ^String evidence))
                (throw (ex-info "ungrounded relation endpoint" {})))
              (knowledge/taxonomy-propose! sys owner
                {:proposal-id concept-id :result-id (str "quality-concept:" id ":" concept-id)
                 :source-id source-id :category-id "conversation-topic"
                 :taxonomy-version "personal-v1" :text (get endpoint "label")
                 :evidence evidence :model-version "reviewed-endpoint"
                 :prompt-version "reviewed-endpoint"})
              (when-not (= :accepted (:status (knowledge/taxonomy-review!
                                                sys owner concept-id (str "accept:" concept-id) 0 :accepted)))
                (throw (ex-info "relation endpoint not accepted" {})))))
          (let [result (orc/propose-relations! sys owner source-id modelId promptId policyId
                                               (mapv #(get % "id") endpointConcepts))]
            {:status (if (#{:staged :empty :reused} (:status result)) "completed" "failed")
             :predictions
             (mapv (fn [relation]
                     {:kind (name (:relation-kind relation)) :leftId (:left-id relation)
                      :rightId (:right-id relation) :evidence (:evidence relation)
                      :owner owner :published false})
                   (:relations result))}))
        (let [result (orc/explore-local-source-bounded! sys owner source-id modelId)]
          {:status "completed" :predictions
           (mapv (fn [candidate] {:kind "concept" :text (:label candidate)
                                  :evidence (:evidence candidate) :owner owner})
                 (:candidates result))}))
      (finally (knowledge/close-system! sys)))))

(defn -main [& _]
  (try
    (let [request (json/read-str (slurp *in*))]
      (when-not (and (string? (get request "id"))
                     (string? (get request "owner"))
                     (string? (get request "sourceText"))
                     (string? (get request "modelId"))
                     (or (not= "relation" (get request "branch"))
                         (and (string? (get request "promptId"))
                              (string? (get request "policyId"))
                              (vector? (get request "endpointConcepts")))))
        (throw (ex-info "invalid quality request" {})))
      (println (str "AWP_QUALITY_RESULT:" (json/write-str (run-one request)))))
    (catch Throwable _
      ;; No source, path, or model error detail is printed to a shared console.
      (println "AWP_QUALITY_RESULT:{\"status\":\"failed\",\"predictions\":[]}"))))
