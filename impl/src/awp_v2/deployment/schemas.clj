(ns awp-v2.deployment.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))
(defschemas commands {:deployment/select [:map [:owner :string] [:data :map]]
                      :deployment/observe [:map [:owner :string] [:data :map]]})
(defevent :deployment/fact "Requested level or observed deployment health."
  {:schema [:map [:owner :string] [:kind :keyword] [:data :map]]})
(defschemas queries {:deployment/status [:map [:owner :string]]})
