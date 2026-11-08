(ns awp-v2.knowledge.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.knowledge.read-models :as models]
            [awp-v2.knowledge.taxonomy :as taxonomy]
            [awp-v2.knowledge.schemas]
            [cognitect.anomalies :as anom]))

(defn- digest [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (java.security.MessageDigest/getInstance "SHA-256")
                       (.getBytes (pr-str value) "UTF-8"))))

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
        relation-attempt (get-in state [:taxonomy-relation-attempts (:result-id data)])
        result
        (cond
          (and (= op :quality-review)
               (not (and (not= actor-id owner)
                         (fn? (:quality-review-authorized? context))
                         ((:quality-review-authorized? context) actor-id owner)))) (refuse :not-found)
          (and (not= op :quality-review) (not= actor-id owner)) (refuse :not-found)
          (= op :quality-case)
          (if-let [old (get-in state [:quality-cases (:case-id data)])]
            (if (= (select-keys old [:case-id :source-id :branch :family-id :split :source-digest])
                   (select-keys data [:case-id :source-id :branch :family-id :split :source-digest]))
              {:value {:status :reused :case-id (:case-id data)}} (refuse :conflict))
            (if (some #(and (= (:family-id %) (:family-id data)) (not= (:split %) (:split data)))
                      (vals (:quality-cases state)))
              (refuse :conflict)
              {:fact data :value {:status :registered :case-id (:case-id data)}}))
          (= op :quality-review)
          (let [quality-case (get-in state [:quality-cases (:case-id data)])
                prior (get-in state [:quality-actions (:action-id data)])]
            (cond
              (nil? quality-case) (refuse :not-found)
              prior (if (= (dissoc prior :at) (dissoc data :at))
                      {:value {:status :reused :case-id (:case-id data)}} (refuse :conflict))
              (not (or (and (= :pending (:review-status quality-case))
                            (= :independent (:reviewer-role data))
                            (#{:approved :rejected :disputed} (:decision data)))
                       (and (= :disputed (:review-status quality-case))
                            (= :adjudicated (:decision data))
                            (= :adjudicator (:reviewer-role data))
                            (not= (:reviewer-id (last (:review-history quality-case))) actor-id))))
              (refuse :conflict)
              :else {:fact data :value {:status (:decision data) :case-id (:case-id data)}}))
          (= op :quality-run)
          (let [old (get-in state [:quality-runs (:run-id data)])
                cases (mapv #(get-in state [:quality-cases %]) (:case-ids data))]
            (cond
              old (if (= (dissoc old :status :metrics-digest) data)
                    {:value {:status :reused :run-id (:run-id data)}} (refuse :conflict))
              (or (empty? cases) (some nil? cases)
                  (not-every? #(and (#{:approved :adjudicated} (:review-status %))
                                    (= :passed (:privacy-status %))
                                    (= (:split %) (:split data))) cases)) (refuse :incorrect)
              :else {:fact data :value {:status :prepared :run-id (:run-id data)}}))
          (= op :quality-holdout)
          (let [run (get-in state [:quality-runs (:run-id data)])]
            (cond
              (nil? run) (refuse :not-found)
              (not= :locked_holdout (:split run)) (refuse :incorrect)
              (not= :prepared (:status run)) (refuse :conflict)
              (get-in state [:quality-holdouts (:corpus-digest run)]) (refuse :conflict)
              :else {:fact {:run-id (:run-id data) :corpus-digest (:corpus-digest run)}
                     :value {:status :consumed :run-id (:run-id data)}}))
          (= op :quality-verdict)
          (let [run (get-in state [:quality-runs (:run-id data)])]
            (cond
              (nil? run) (refuse :not-found)
              (not (#{:prepared :running} (:status run))) (refuse :conflict)
              (and (= :locked_holdout (:split run)) (not= :running (:status run))) (refuse :conflict)
              (and (= :passed (:outcome data))
                   (not (and (fn? (:quality-gate-authorized? context))
                             ((:quality-gate-authorized? context) owner (:run-id data))))) (refuse :incorrect)
              :else {:fact data :value {:status (:outcome data) :run-id (:run-id data)}}))
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
          (= op :taxonomy-relation-attempt)
          (cond
            (not= owner (:owner data)) (refuse :not-found)
            (nil? source) (refuse :not-found)
            (not= (:source-digest data) (digest (:transcript source))) (refuse :conflict)
            relation-attempt (if (= (:digest relation-attempt) (:digest data))
                               {:value {:status :reused :result-id (:result-id data)}}
                               (refuse :conflict))
            (and (= :completed (:status data))
                 (or (not= (count (:candidates data))
                           (count (set (map :relation-id (:candidates data))))))
                     (some (fn [candidate]
                             (let [left (get-in state [:taxonomy-proposals (:left-id candidate)])
                                   right (get-in state [:taxonomy-proposals (:right-id candidate)])]
                               (or (not (#{:broader :related} (:relation-kind candidate)))
                                   (not (string? (:relation-id candidate)))
                                   (nil? left) (nil? right)
                                   (not= :accepted (:status left))
                                   (not= :accepted (:status right))
                                   (= (:left-id candidate) (:right-id candidate))
                                   (not= (:revision left) (:left-revision candidate))
                                   (not= (:revision right) (:right-revision candidate))
                                   (not (and (string? (:evidence candidate))
                                             (.contains ^String (:transcript source)
                                                        ^String (:evidence candidate))))))
                           (:candidates data)))) (refuse :incorrect)
            :else {:fact data :value {:status (:status data) :result-id (:result-id data)}})
          (= op :taxonomy-relation)
          (let [left (get-in state [:taxonomy-proposals (:left-id data)])
                right (get-in state [:taxonomy-proposals (:right-id data)])
                attempt (get-in state [:taxonomy-relation-attempts (:result-id data)])
                candidate (some #(when (= (:relation-id %) (:relation-id data)) %)
                                (:candidates attempt))]
            (cond
              (nil? source) (refuse :not-found)
              (and (:owner data) (not= owner (:owner data))) (refuse :not-found)
              (or (nil? left) (nil? right)) (refuse :not-found)
              (or (not= :accepted (:status left)) (not= :accepted (:status right))) (refuse :conflict)
              (= (:left-id data) (:right-id data)) (refuse :incorrect)
              (and (:result-id data)
                   (or (nil? attempt) (not= :completed (:status attempt))
                       (not= (select-keys data [:relation-id :left-id :right-id :relation-kind
                                                :evidence :left-revision :right-revision])
                             candidate)
                       (not= (:source-id data) (:source-id attempt))
                       (not= (:source-digest data) (:source-digest attempt))
                       (not= (:model-version data) (:model-version attempt))
                       (not= (:prompt-version data) (:prompt-version attempt))
                       (not= (:policy-version data) (:policy-version attempt))
                       (not= (:trace-id data) (:trace-id attempt)))) (refuse :conflict)
              (or (not (string? (:evidence data)))
                  (not (.contains ^String (:transcript source) ^String (:evidence data)))) (refuse :incorrect)
              relation (if (= (dissoc relation :status :revision :reviews :corrected-by)
                              (dissoc data :status :revision :reviews :corrected-by))
                         {:value {:status :reused :relation-id (:relation-id data)}} (refuse :conflict))
              :else {:fact data :value (assoc data :status :pending :revision 0)}))
          (= op :taxonomy-relation-correct)
          (let [original (get-in state [:taxonomy-relations (:relation-id data)])
                correction-source (get-in state [:sources (:source-id original)])
                left (get-in state [:taxonomy-proposals (:left-id data)])
                right (get-in state [:taxonomy-proposals (:right-id data)])]
            (cond
              (nil? original) (refuse :not-found)
              prior-taxonomy-action (if (= (select-keys prior-taxonomy-action
                                                        [:relation-id :replacement-id :expected-revision
                                                         :left-id :right-id :relation-kind :evidence])
                                         (select-keys data [:relation-id :replacement-id :expected-revision
                                                            :left-id :right-id :relation-kind :evidence]))
                                      {:value (get-in state [:taxonomy-relations (:replacement-id data)])}
                                      (refuse :conflict))
              (not= :pending (:status original)) (refuse :conflict)
              (not= (:revision original) (:expected-revision data)) (refuse :conflict)
              (get-in state [:taxonomy-relations (:replacement-id data)]) (refuse :conflict)
              (or (nil? left) (nil? right)
                  (not= :accepted (:status left)) (not= :accepted (:status right))) (refuse :conflict)
              (= (:left-id data) (:right-id data)) (refuse :incorrect)
              (or (nil? correction-source)
                  (not (.contains ^String (:transcript correction-source)
                                  ^String (:evidence data)))) (refuse :incorrect)
              :else {:fact (assoc data :decision :rejected :owner owner)
                     :value {:relation-id (:replacement-id data) :status :pending
                             :replaces-id (:relation-id data) :revision 0}}))
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
            (and (= :accepted (:decision data))
                 (some #(and (not= (:relation-id %) (:relation-id relation))
                             (= :accepted (:status %))
                             (= (:relation-kind %) (:relation-kind relation))
                             (or (and (= (:left-id %) (:left-id relation))
                                      (= (:right-id %) (:right-id relation)))
                                 (and (= :related (:relation-kind relation))
                                      (= (:left-id %) (:right-id relation))
                                      (= (:right-id %) (:left-id relation)))))
                       (vals (:taxonomy-relations state)))) (refuse :conflict)
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
(defcommand :knowledge taxonomy-relation-attempt {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-relation-attempt ctx))
(defcommand :knowledge taxonomy-relation-correct {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :taxonomy-relation-correct ctx))
(defcommand :knowledge quality-case {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :quality-case ctx))
(defcommand :knowledge quality-review {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :quality-review ctx))
(defcommand :knowledge quality-run {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :quality-run ctx))
(defcommand :knowledge quality-holdout {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :quality-holdout ctx))
(defcommand :knowledge quality-verdict {:grain.event-model/reads #{:knowledge/state} :grain.event-model/produces #{:knowledge/fact}} [ctx] (decide :quality-verdict ctx))
