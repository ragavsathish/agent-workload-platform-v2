(ns awp-v2.song.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :song
  {:description "Owner-reviewed lyric and render lifecycle."
   :commands (into {} (for [[op rule] {:create "RequestSong" :submitted "RequestSong"
                                         :lyrics "PrepareLyrics" :decide "DecideLyrics"
                                         :render "RequestRender" :stored "StoreSong"
                                         :cancel "CancelSong"}]
                        [(keyword "song" (name op))
                         {:description (name op) :schema [:map [:owner :string] [:data :map]]
                          :reads #{:song/state} :produces #{:song/fact}
                          :grain/allium [{:spec "specs/personal-song.allium" :kind :rule :name rule}]}]))
   :events {:song/fact {:description "One owner song fact."
                        :schema [:map [:owner :string] [:kind :keyword] [:data :map]]}}
   :read-models {:song/state {:description "Owner song state and request ledger."
                              :consumes #{:song/fact} :schema [:map-of :string :map] :version 1}}
   :queries {:song/get {:description "Read an owned generation."
                        :schema [:map [:owner :string] [:generation-id :string]]
                        :reads #{:song/state}}}
   :flows {:song/creation {:description "A request creates one owner-visible generation."
                           :steps [{:from nil :to [:command :song/create]}
                                   {:from [:command :song/create] :to [:event :song/fact]}
                                   {:from [:event :song/fact] :to [:read-model :song/state]}
                                   {:from [:read-model :song/state] :to [:query :song/get]}
                                   {:from [:query :song/get] :to nil}]}}})
