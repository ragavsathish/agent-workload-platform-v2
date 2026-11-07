(ns awp-v2.song.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [awp-v2.song.read-models :as models]
            [awp-v2.song.schemas]
            [cognitect.anomalies :as anom]))

(defquery :song get {:grain.event-model/reads #{:song/state}} [ctx]
  (let [owner (get-in ctx [:query :owner])
        id (get-in ctx [:query :generation-id])]
    (if (and (= owner (:actor-id ctx))
             (get-in (models/owner-state ctx owner) [:songs id]))
      {:query/result (get-in (models/owner-state ctx owner) [:songs id])}
      {::anom/category ::anom/not-found ::anom/message "not-found"})))
