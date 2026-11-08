(ns awp-v2.knowledge.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.knowledge.read-models :as models]
            [awp-v2.knowledge.taxonomy :as taxonomy]
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

(defn- broader-reaches? [state start target]
  (loop [frontier [start] seen #{}]
    (if-let [node (first frontier)]
      (if (= node target)
        true
        (if (contains? seen node)
          (recur (subvec frontier 1) seen)
          (let [next-nodes (->> (:taxonomy-relations state) vals
                                (filter #(and (= :accepted (:status %))
                                              (= :broader (:relation-kind %))
                                              (= node (:left-id %))))
                                (map :right-id))]
            (recur (into (subvec frontier 1) next-nodes) (conj seen node)))))
      false)))

(defn- decide [op {:keys [actor-id] {:keys [owner data]} :command :as context}]
  (let [state (models/owner-state context owner)
        source (get-in state [:sources (:source-id data)])
        work (get-in state [:work (:source-id data) (:mode data)])
        item (get-in state [:items (:item-id data)])
        account (get-in state [:accounts (:account-id data)])
        prior-action (get-in state [:actions (:action-id data)])
        legacy-snapshot (get-in state [:legacy-snapshots (:snapshot-id data)])
        legacy-resource (get-in state [:legacy-resources (:snapshot-id data) (:legacy-id data)])
        proposal (get-in state [:taxonomy-proposals (:proposal-id data)])
        relation (get-in state [:taxonomy-relations (:relation-id data)])
        prior-taxonomy-action (get-in state [:taxonomy-actions (:action-id data)])
        prior-result (get-in state [:taxonomy-result-ids (:result-id data)])
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
          (= op :taxonomy-proposal)
          (cond
            (nil? source) (refuse :not-found)
            (not (taxonomy/publishable? (:taxonomy-version data) (:category-id data))) (refuse :incorrect)
            (or (nil? (:evidence data))
                (not (.contains ^String (:transcript source) ^String (:evidence data)))) (refuse :incorrect)
            prior-result (if (= prior-result (:digest data))
                           {:value {:status :reused :proposal-id (:proposal-id data)}} (refuse :conflict))
            proposal (refuse :conflict)
            :else {:fact data :value (assoc data :status :pending :revision 0)})
          (= op :taxonomy-review)
          (cond
            (nil? proposal) (refuse :not-found)
            prior-taxonomy-action (if (= (select-keys prior-taxonomy-action [:proposal-id :expected-revision :decision])
                                       (select-keys data [:proposal-id :expected-revision :decision]))
                                    {:value {:status :reused :proposal-id (:proposal-id data)}} (refuse :conflict))
            (not= (:revision proposal) (:expected-revision data)) (refuse :conflict)
            (not (or (and (= :pending (:status proposal)) (#{:accepted :rejected} (:decision data)))
                     (and (= :accepted (:status proposal)) (= :revoked (:decision data))))) (refuse :incorrect)
            :else {:fact data :value (assoc proposal :status (:decision data) :revision (inc (:revision proposal)))})
          (= op :taxonomy-map)
          (cond
            (nil? proposal) (refuse :not-found)
            (not (taxonomy/publishable? (:to-version data) (:category-id data))) (refuse :incorrect)
            (not= :accepted (:status proposal)) (refuse :conflict)
            (get-in proposal [:mappings (:to-version data)])
            (if (= (select-keys (get-in proposal [:mappings (:to-version data)]) [:category-id :decision-id])
                   (select-keys data [:category-id :decision-id]))
              {:value {:status :reused :proposal-id (:proposal-id data)}} (refuse :conflict))
            :else {:fact data :value {:status :accepted :proposal-id (:proposal-id data)}})
          (= op :taxonomy-model-outcome)
          (cond
            (nil? source) (refuse :not-found)
            prior-result (if (= prior-result (:digest data))
                           {:value {:status :reused :result-id (:result-id data)}} (refuse :conflict))
            :else {:fact data :value {:status (:status data) :result-id (:result-id data)}})
          (= op :taxonomy-relation)
          (let [left (get-in state [:taxonomy-proposals (:left-id data)])
                right (get-in state [:taxonomy-proposals (:right-id data)])]
            (cond
              (nil? source) (refuse :not-found)
              (or (nil? left) (nil? right)) (refuse :not-found)
              (or (not= :accepted (:status left)) (not= :accepted (:status right))) (refuse :conflict)
              (= (:left-id data) (:right-id data)) (refuse :incorrect)
              (or (not (string? (:evidence data)))
                  (not (.contains ^String (:transcript source) ^String (:evidence data)))) (refuse :incorrect)
              relation (if (= (dissoc relation :status :revision :reviews) (dissoc data :status :revision :reviews))
                         {:value {:status :reused :relation-id (:relation-id data)}} (refuse :conflict))
              :else {:fact data :value (assoc data :status :pending :revision 0)}))
          (= op :taxonomy-relation-review)
          (cond
            (nil? relation) (refuse :not-found)
            prior-taxonomy-action (if (= (select-keys prior-taxonomy-action [:relation-id :expected-revision :decision])
                                       (select-keys data [:relation-id :expected-revision :decision]))
                                    {:value {:status :reused :relation-id (:relation-id data)}} (refuse :conflict))
            (not= (:revision relation) (:expected-revision data)) (refuse :conflict)
            (not (or (and (= :pending (:status relation)) (#{:accepted :rejected} (:decision data)))
                     (and (= :accepted (:status relation)) (= :revoked (:decision data))))) (refuse :incorrect)
            (and (= :accepted (:decision data))
                 (not-every? #(= :accepted (get-in state [:taxonomy-proposals % :status]))
                             [(:left-id relation) (:right-id relation)])) (refuse :conflict)
            (and (= :accepted (:decision data)) (= :broader (:relation-kind relation))
                 (broader-reaches? state (:right-id relation) (:left-id relation))) (refuse :conflict)
            :else {:fact data :value (assoc relation :status (:decision data) :revision (inc (:revision relation)))})
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
          (= op :legacy-snapshot)
          (if legacy-snapshot
            (if (= (:digest legacy-snapshot) (:digest data))
              {:value {:status :reused :snapshot-id (:snapshot-id data)}} (refuse :conflict))
            {:fact data :value {:status :accepted :snapshot-id (:snapshot-id data)}})
          (= op :legacy-resource)
          (cond
            (nil? legacy-snapshot) (refuse :not-found)
            legacy-resource (if (= legacy-resource (:resource data))
                              {:value {:status :reused :legacy-id (:legacy-id data)}} (refuse :conflict))
            :else {:fact data :value {:status :accepted :legacy-id (:legacy-id data)}})
          (= op :legacy-switch)
          (cond
            (nil? legacy-snapshot) (refuse :not-found)
            (not= (:expected-ids data) (set (keys (get-in state [:legacy-resources (:snapshot-id data)]))))
            (refuse :conflict)
            :else {:fact data :value {:read-source :import :snapshot-id (:snapshot-id data)}})
          (= op :legacy-rollback)
          (if legacy-snapshot
            {:fact data :value {:read-source :source :snapshot-id (:snapshot-id data)}}
            (refuse :not-found))
          (= op :daily-result)
          (cond
            (nil? account) (refuse :not-found)
            (not= (:source-digest account) (:source-digest data)) (refuse :conflict)
            (#{:completed :failed} (:status account))
            (if (and (= (:status account) (:status data))
                     (= (:output-digest account) (:output-digest data)))
              {:value account} (refuse :conflict))
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

(defcommand :knowledge legacy-snapshot {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :legacy-snapshot ctx))
(defcommand :knowledge legacy-resource {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :legacy-resource ctx))
(defcommand :knowledge legacy-switch {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :legacy-switch ctx))
(defcommand :knowledge legacy-rollback {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :legacy-rollback ctx))
(defcommand :knowledge taxonomy-proposal {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-proposal ctx))
(defcommand :knowledge taxonomy-review {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-review ctx))
(defcommand :knowledge taxonomy-map {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-map ctx))
(defcommand :knowledge taxonomy-model-outcome {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-model-outcome ctx))
(defcommand :knowledge taxonomy-relation {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-relation ctx))
(defcommand :knowledge taxonomy-relation-review {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-relation-review ctx))
