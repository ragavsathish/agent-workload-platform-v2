(ns awp-v2.deployment.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [awp-v2.deployment.read-models :as models]
            [awp-v2.deployment.schemas]
            [cognitect.anomalies :as anom]))
(defquery :deployment status {:grain.event-model/reads #{:deployment/state}} [ctx]
  (let [owner (get-in ctx [:query :owner])]
    (if (= owner (:actor-id ctx))
      {:query/result (models/owner-state ctx owner)}
      {::anom/category ::anom/not-found ::anom/message "not-found"})))
