(ns awp-v2.incident.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.incident.read-models :as models]
            [awp-v2.incident.schemas]
            [cognitect.anomalies :as anom])
  (:import [java.util UUID]))

(defn- refuse [kind] {::anom/category (if (= kind :not-found) ::anom/not-found ::anom/conflict)
                       ::anom/message (name kind)})
(defn- owner-tag [owner]
  [:incident-owner (UUID/nameUUIDFromBytes (.getBytes owner "UTF-8"))])
(defn- emit [owner kind data result revision]
  (let [tag (owner-tag owner)]
    {:command-result/events [(es/->event {:type :incident/fact :tags #{tag}
                                           :body {:owner owner :kind kind :data data}})]
     :command-result/cas {:tags #{tag}
                          :predicate-fn (fn [events] (= revision (count (into [] events))))}
     :command/result result}))
(defn- handle [{:keys [actor-id] {:keys [owner data]} :command :as ctx} op]
  (let [state (models/owner-state ctx owner)
        revision (or (:revision state) 0)
        epoch (get-in state [:epochs (:epoch-id data)])
        latest-id (get-in state [:latest (:key data)])
        latest (get-in state [:epochs latest-id])
        record! (fn [kind data result] (emit owner kind data result revision))]
    (cond
      (not= owner actor-id) (refuse :not-found)
      (= op :observe)
      (cond
        (and latest (:active? latest))
        (record! :repeated {:epoch-id latest-id :at (:at data)} {:epoch-id latest-id :status :reused})
        (and latest (:last-submitted latest)
             (< (- (:at data) (:last-submitted latest)) 1800))
        (record! :reactivated {:epoch-id latest-id :at (:at data)} {:epoch-id latest-id :status :reused})
        :else
        (record! :opened data {:epoch-id (:epoch-id data) :status :pending}))
      (= op :resolve)
      (if (and epoch (:active? epoch))
        (record! :resolved {:epoch-id (:epoch-id data)} {:status :resolved})
        {:command/result {:status :reused}})
      (= op :submitted)
      (cond
        (nil? epoch) (refuse :not-found)
        (= :accepted (:submission-status epoch))
        (if (= (:task-id epoch) (:task-id data))
          {:command/result {:status :reused}}
          (refuse :conflict))
        (not= (:request-id epoch) (:task-id data)) (refuse :conflict)
        :else (record! :submitted data {:status :accepted}))
      (= op :failure)
      (record! :failure data {:failures (inc (or (:failures state) 0))})
      (= op :success)
      (record! :success data {:failures 0})
      :else (refuse :incorrect))))

(defcommand :incident observe {:grain.event-model/reads #{:incident/state} :grain.event-model/produces #{:incident/fact}}
  [ctx] (handle ctx :observe))
(defcommand :incident resolve {:grain.event-model/reads #{:incident/state} :grain.event-model/produces #{:incident/fact}}
  [ctx] (handle ctx :resolve))
(defcommand :incident failure {:grain.event-model/reads #{:incident/state} :grain.event-model/produces #{:incident/fact}}
  [ctx] (handle ctx :failure))
(defcommand :incident success {:grain.event-model/reads #{:incident/state} :grain.event-model/produces #{:incident/fact}}
  [ctx] (handle ctx :success))
(defcommand :incident submitted {:grain.event-model/reads #{:incident/state} :grain.event-model/produces #{:incident/fact}}
  [ctx] (handle ctx :submitted))
