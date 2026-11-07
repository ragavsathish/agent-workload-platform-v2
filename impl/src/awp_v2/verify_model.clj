(ns awp-v2.verify-model
  (:require [ai.obney.grain.code-agent-tools.interface :as tools]
            [ai.obney.grain.event-model.interface :as model]
            [awp-v2.tasking.api]
            [awp-v2.session.api]
            [awp-v2.knowledge.api]
            [awp-v2.song.api]))

(defn -main [& _]
  (let [verdict (tools/validate-spec-composition
                  (model/registered-model)
                  {:project-root ".." :event-model-opts {:strict true}})]
    (when-not (:valid? verdict)
      (prn verdict)
      (System/exit 1))
    (println "Grain and Allium composition OK")
    (System/exit 0)))
