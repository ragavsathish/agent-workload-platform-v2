(ns awp-v2.incident.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas commands
  (into {} (for [op [:observe :resolve :failure :success :submitted]]
             [(keyword "incident" (name op)) [:map [:owner :string] [:data :map]]])))
(defevent :incident/fact "One owner incident decision or source observation."
  {:schema [:map [:owner :string] [:kind :keyword] [:data :map]]})
(defschemas queries
  {:incident/get [:map [:owner :string] [:epoch-id :string]]
   :incident/list [:map [:owner :string]]})
