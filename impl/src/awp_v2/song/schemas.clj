(ns awp-v2.song.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas commands
  (into {} (for [op [:create :submitted :lyrics :decide :render :stored :cancel]]
             [(keyword "song" (name op)) [:map [:owner :string] [:data :map]]])))
(defevent :song/fact "One owner song decision or work outcome."
  {:schema [:map [:owner :string] [:kind :keyword] [:data :map]]})
(defschemas queries
  {:song/get [:map [:owner :string] [:generation-id :string]]})
