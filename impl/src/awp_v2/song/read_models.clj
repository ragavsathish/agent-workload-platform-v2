(ns awp-v2.song.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(defn- apply-fact [state {:keys [kind data]}]
  (let [id (:generation-id data)]
    (case kind
      :created (-> state
                   (assoc-in [:requests (:request-id data)] {:generation-id id :input-digest (:input-digest data)})
                   (assoc-in [:songs id] (-> data (dissoc :input-digest)
                                              (assoc :status :writing-lyrics :submission :pending))))
      :submitted (assoc-in state [:songs id :submission] :submitted)
      :lyrics (-> state (assoc-in [:songs id :manifest] (:manifest data))
                  (assoc-in [:songs id :manifest-digest] (:manifest-digest data))
                  (assoc-in [:songs id :lyric-result-digest] (:lyric-result-digest data))
                  (assoc-in [:songs id :lyric-attempt] (:lyric-attempt data))
                  (assoc-in [:songs id :status] :lyrics-ready))
      :lyric-failed (-> state (assoc-in [:songs id :status] :failed)
                        (assoc-in [:songs id :failure] "lyric generation failed"))
      :decision (-> state
                    (assoc-in [:songs id :decision] (select-keys data [:decision-id :manifest-digest :choice]))
                    (assoc-in [:decisions (:decision-id data)]
                              (select-keys data [:generation-id :manifest-digest :choice])))
      :render (-> state (assoc-in [:songs id :render-task-id] (:render-task-id data))
                  (assoc-in [:songs id :render-submission] :pending)
                  (assoc-in [:songs id :status] :generating-music))
      :render-submitted (assoc-in state [:songs id :render-submission] :submitted)
      :stored (-> state (assoc-in [:songs id :artifact-id] (:artifact-id data))
                  (assoc-in [:songs id :status] :complete))
      :render-failed (-> state (assoc-in [:songs id :status] :failed)
                        (assoc-in [:songs id :failure] "song rendering failed"))
      :cancelled (assoc-in state [:songs id :status] :cancelled)
      state)))

(defreadmodel :song state
  {:events #{:song/fact} :version 1 :schema [:map-of :string :map]}
  "Owner song requests and generations from immutable facts."
  [state event]
  (let [owner (:owner event) id (get-in event [:data :generation-id])]
    (-> (update state owner (fnil apply-fact {}) event)
        (update-in [owner :songs id :event-count] (fnil inc 0)))))

(defn owners [context] (rmp/project context :song/state))
(defn owner-state [context owner]
  (get (owners context) owner))
