(ns awp-v2.knowledge.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.knowledge.read-models :as models]
            [awp-v2.knowledge.schemas]
            [cognitect.anomalies :as anom]))

(defn- refuse [kind]
  {::anom/category (case kind :not-found ::anom/not-found :incorrect ::anom/incorrect ::anom/conflict)
   ::anom/message (name kind)})
(defn- owner-tag [owner]
  [:knowledge-owner (java.util.UUID/nameUUIDFromBytes (.getBytes owner "UTF-8"))])
(defn- event [owner kind data]
  (es/->event {:type :knowledge/fact :tags #{(owner-tag owner)}
               :body {:owner owner :kind kind :data data}}))

(defn- decide [op {:keys [actor-id] {:keys [owner data]} :command :as context}]
  (let [state (models/owner-state context owner)
        source (get-in state [:sources (:source-id data)])
        work (get-in state [:work (:source-id data) (:mode data)])
        item (get-in state [:items (:item-id data)])
        account (get-in state [:accounts (:account-id data)])
        prior-action (get-in state [:actions (:action-id data)])
        result
        (cond
          (not= actor-id owner) (refuse :not-found)
          (= op :intake)
          (if source
            (if (= (select-keys source [:conversation-id :message-id :transcript :captured-at])
                   (select-keys data [:conversation-id :message-id :transcript :captured-at]))
              {:value {:status :reused :source-id (:source-id data)}}
              (refuse :conflict))
            {:fact data :value {:status :accepted :source-id (:source-id data)}})
          (= op :submission)
          (cond
            (nil? work) (refuse :not-found)
            (:task-id work) (if (= (:task-id work) (:task-id data))
                              {:value work} (refuse :conflict))
            :else {:fact data :value (assoc work :task-id (:task-id data))})
          (= op :submission-uncertain)
          (if work {:fact data :value (assoc work :status :uncertain)} (refuse :not-found))
          (= op :outcome)
          (cond
            (nil? work) (refuse :not-found)
            (#{:completed :failed} (:status work))
            (if (and (= (:status work) (:status data))
                     (= (:output-digest work) (:output-digest data)))
              {:value work} (refuse :conflict))
            (some #(contains? (:items state) (:item-id %)) (:suggestions data)) (refuse :conflict)
            :else {:fact data :value (assoc work :status (:status data))})
          (= op :review)
          (cond
            (nil? item) (refuse :not-found)
            prior-action (if (= (select-keys prior-action [:item-id :decision :text :expected-revision])
                                (select-keys data [:item-id :decision :text :expected-revision]))
                           {:value {:item-id (:item-id data) :status (:decision prior-action)
                                    :revision (inc (:expected-revision prior-action)) :reused? true}}
                           (refuse :conflict))
            (not= (:revision item) (:expected-revision data)) (refuse :conflict)
            :else {:fact data :value (cond-> (-> item (assoc :status (:decision data)) (update :revision inc))
                                      (:text data) (assoc :current-text (:text data)))})
          (= op :daily-request)
          (if account
            (if (= (select-keys account [:journal-date :time-zone :sources :source-digest])
                   (select-keys data [:journal-date :time-zone :sources :source-digest]))
              {:value {:account-id (:account-id data) :status :reused}}
              (refuse :conflict))
            {:fact data :value {:account-id (:account-id data) :status :accepted}})
          (= op :daily-submission)
          (cond
            (nil? account) (refuse :not-found)
            (:task-id account) (if (= (:task-id account) (:task-id data))
                                 {:value {:account-id (:account-id data) :task-id (:task-id data)}}
                                 (refuse :conflict))
            :else {:fact data :value {:account-id (:account-id data) :task-id (:task-id data)}})
          (= op :daily-uncertain)
          (if account {:fact data :value {:account-id (:account-id data) :status :uncertain}}
              (refuse :not-found))
          (= op :daily-result)
          (cond
            (nil? account) (refuse :not-found)
            (not= (:source-digest account) (:source-digest data)) (refuse :conflict)
            (#{:completed :failed} (:status account))
            (if (= (:status account) (:status data)) {:value account} (refuse :conflict))
            :else {:fact data :value (merge account data)})
          :else (refuse :incorrect))]
    (if-not (:fact result)
      (if (:value result) {:command/result (:value result)} result)
      {:command-result/events [(event owner op (:fact result))]
       :command-result/cas {:tags #{(owner-tag owner)}
                            :predicate-fn (fn [events]
                                            (= (:revision state) (:event/id (last (into [] events)))))}
       :command/result (:value result)})))

(defcommand :knowledge intake {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :intake ctx))
(defcommand :knowledge submission {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :submission ctx))
(defcommand :knowledge submission-uncertain {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :submission-uncertain ctx))
(defcommand :knowledge outcome {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :outcome ctx))
(defcommand :knowledge review {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :review ctx))
(defcommand :knowledge daily-request {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :daily-request ctx))
(defcommand :knowledge daily-submission {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :daily-submission ctx))
(defcommand :knowledge daily-uncertain {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :daily-uncertain ctx))
(defcommand :knowledge daily-result {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :daily-result ctx))
