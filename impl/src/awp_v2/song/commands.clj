(ns awp-v2.song.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.song.read-models :as models]
            [awp-v2.song.schemas]
            [cognitect.anomalies :as anom])
  (:import [java.util UUID]))

(defn- refuse [error]
  {::anom/category (case error :not-found ::anom/not-found
                     :conflict ::anom/conflict ::anom/incorrect)
   ::anom/message (name error)})
(defn- tag-id [text] (UUID/nameUUIDFromBytes (.getBytes text "UTF-8")))
(defn- emit [owner kind data result tags expected]
  {:command-result/events [(es/->event {:type :song/fact :tags tags
                                         :body {:owner owner :kind kind :data data}})]
   :command-result/cas {:tags tags :predicate-fn (fn [events] (= expected (count (into [] events))))}
   :command/result result})
(defn- handle [{:keys [actor-id] {:keys [owner data]} :command :as ctx} op]
  (let [state (models/owner-state ctx owner)
        id (:generation-id data)
        song (get-in state [:songs id])
        request (get-in state [:requests (:request-id data)])
        tags #{[:song (tag-id (str owner ":" id))]}
        expected (or (:event-count song) 0)
        update! (fn [kind result] (emit owner kind data result tags expected))]
    (cond
      (not= actor-id owner) (refuse :not-found)
      (= op :create)
      (cond
        request (if (= (:input-digest request) (:input-digest data))
                  {:command/result {:generation-id (:generation-id request) :status :reused}}
                  (refuse :conflict))
        :else (emit owner :created data {:generation-id id :status :accepted}
                    #{[:song-request (tag-id (str owner ":" (:request-id data)))]
                      [:song (tag-id (str owner ":" id))]} 0))
      (nil? song) (refuse :not-found)
      (= op :submitted)
      (if (= :pending (if (= (:stage data) :render) (:render-submission song) (:submission song)))
        (update! (if (= (:stage data) :render) :render-submitted :submitted) {:status :submitted})
        {:command/result {:status :reused}})
      (= op :lyrics)
      (cond
        (not= (:lyrics-task-id song) (:lyrics-task-id data)) (refuse :conflict)
        (= :cancelled (:status song)) {:command/result {:status :cancelled}}
        (not= :writing-lyrics (:status song)) (refuse :conflict)
        (:manifest data) (update! :lyrics {:status :lyrics-ready})
        :else (update! :lyric-failed {:status :failed}))
      (= op :decide)
      (cond
        (and (get-in state [:decisions (:decision-id data)])
             (not= (get-in state [:decisions (:decision-id data)])
                   (select-keys data [:generation-id :manifest-digest :choice]))) (refuse :conflict)
        (:decision song) (if (= (select-keys (:decision song) [:decision-id :manifest-digest :choice])
                                (select-keys data [:decision-id :manifest-digest :choice]))
                           {:command/result {:status (:choice data)}} (refuse :conflict))
        (not= :lyrics-ready (:status song)) (refuse :not-ready)
        (not= (:manifest-digest song) (:manifest-digest data)) (refuse :conflict)
        :else (update! :decision {:status (:choice data)}))
      (= op :render)
      (cond
        (:render-task-id song) {:command/result {:status :reused :render-task-id (:render-task-id song)}}
        (not= :lyrics-ready (:status song)) (refuse :not-ready)
        (= :rejected (get-in song [:decision :choice])) (refuse :rejected)
        (not= :approved (get-in song [:decision :choice])) (refuse :approval-required)
        (not= (:manifest-digest song) (:manifest-digest data)) (refuse :conflict)
        :else (update! :render {:status :generating-music :render-task-id (:render-task-id data)}))
      (= op :stored)
      (cond
        (not= (:render-task-id song) (:render-task-id data)) (refuse :conflict)
        (= :cancelled (:status song)) {:command/result {:status :cancelled}}
        (not= :generating-music (:status song)) (refuse :conflict)
        (:artifact-id data) (update! :stored {:status :complete})
        :else (update! :render-failed {:status :failed}))
      (= op :cancel)
      (if (= :cancelled (:status song)) {:command/result {:status :cancelled}}
        (if (= :complete (:status song)) (refuse :conflict)
          (update! :cancelled {:status :cancelled})))
      :else (refuse :incorrect))))

(defcommand :song create {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Admit or reuse a song request." [ctx] (handle ctx :create))
(defcommand :song submitted {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Record task submission." [ctx] (handle ctx :submitted))
(defcommand :song lyrics {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Record lyric outcome." [ctx] (handle ctx :lyrics))
(defcommand :song decide {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Record manifest decision." [ctx] (handle ctx :decide))
(defcommand :song render {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Admit guarded render." [ctx] (handle ctx :render))
(defcommand :song stored {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Record render outcome." [ctx] (handle ctx :stored))
(defcommand :song cancel {:grain.event-model/reads #{:song/state} :grain.event-model/produces #{:song/fact}}
  "Cancel active song." [ctx] (handle ctx :cancel))
