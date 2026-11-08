(ns awp-v2.incident.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [awp-v2.incident.read-models :as models]
            [awp-v2.incident.schemas]
            [cognitect.anomalies :as anom]))
(defquery :incident get {:grain.event-model/reads #{:incident/state}} [ctx]
  (let [owner (get-in ctx [:query :owner])
        id (get-in ctx [:query :epoch-id])
        row (get-in (models/owner-state ctx owner) [:epochs id])]
    (if (and (= owner (:actor-id ctx)) row)
      {:query/result row}
      {::anom/category ::anom/not-found ::anom/message "not-found"})))
(defquery :incident list {:grain.event-model/reads #{:incident/state}} [ctx]
  (let [owner (get-in ctx [:query :owner])]
    (if (= owner (:actor-id ctx))
      {:query/result (vec (vals (get-in (models/owner-state ctx owner) [:epochs] {})))}
      {::anom/category ::anom/not-found ::anom/message "not-found"})))
