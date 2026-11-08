(ns awp-v2.knowledge.quality-orc-runner
  (:gen-class)
  (:require [clojure.data.json :as json]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.orc-adapter :as orc]))

(defn- run-one [{:strs [id owner sourceText modelId]}]
  (let [sys (knowledge/open-system {})
        source-id (str "quality:" id)]
    (try
      (knowledge/ingest-turn! sys owner {:source-id source-id :conversation-id "quality"
                                         :message-id id :transcript sourceText
                                         :captured-at "2026-11-08T00:00:00Z"
                                         :status :completed :origin :owner})
      (let [result (orc/explore-local-source-bounded! sys owner source-id modelId)]
        {:status "completed" :predictions
         (mapv (fn [candidate] {:kind "concept" :text (:label candidate)
                                :evidence (:evidence candidate) :owner owner})
               (:candidates result))})
      (finally (knowledge/close-system! sys)))))

(defn -main [& _]
  (try
    (let [request (json/read-str (slurp *in*))]
      (when-not (and (string? (get request "id"))
                     (string? (get request "owner"))
                     (string? (get request "sourceText"))
                     (string? (get request "modelId")))
        (throw (ex-info "invalid quality request" {})))
      (println (str "AWP_QUALITY_RESULT:" (json/write-str (run-one request)))))
    (catch Throwable _
      ;; No source, path, or model error detail is printed to a shared console.
      (println "AWP_QUALITY_RESULT:{\"status\":\"failed\",\"predictions\":[]}"))))
