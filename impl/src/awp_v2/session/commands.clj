(ns awp-v2.session.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.session.read-models :as models]
            [awp-v2.session.schemas]
            [cognitect.anomalies :as anom]))

(defn- refuse [kind]
  {::anom/category (case kind
                     :not-found ::anom/not-found
                     :forbidden ::anom/forbidden
                     :incorrect ::anom/incorrect
                     ::anom/conflict)
   ::anom/message (name kind)})

(defn- owner-tag [owner]
  [:session-owner (java.util.UUID/nameUUIDFromBytes
                    (.getBytes owner "UTF-8"))])

(defn- event [owner kind data]
  (es/->event {:type :session/fact
               :tags #{(owner-tag owner)}
               :body {:owner owner :kind kind :data data}}))

(defn- busy? [state]
  (some #(= :accepted (:status %)) (vals (:turns state))))

(defn- valid-command-id? [value]
  (and (string? value) (<= 1 (count value) 128)))

(defn- decision [op {:keys [actor-id owned-task?]
                      {:keys [owner data]} :command :as context}]
  (let [state (models/owner-state context owner)
        prior-selection (get-in state [:selection-commands (:command-id data)])
        prior-routing (get-in state [:routing-commands (:command-id data)])
        prior-turn (get-in state [:turns (:turn-id data)])
        prior-compaction (get-in state [:compactions (:command-id data)])
        pending-compaction (when-let [id (:pending-compaction state)]
                             (get-in state [:compactions id]))
        selected (:selection state)
        same-selection? (= prior-selection
                           {:operation op
                            :input (when (= op :resume) (:conversation-id data))
                            :id (:id prior-selection)})
        result
        (cond
          (or (nil? actor-id) (not= actor-id owner)) (refuse :not-found)

          (#{:fresh :resume} op)
          (cond
            prior-selection
            (if same-selection?
              {:value {:id (:id prior-selection)}}
              (refuse :conflict))
            pending-compaction (refuse :busy)
            (busy? state) (refuse :busy)
            (and (= op :resume)
                 (not (contains? (:conversations state) (:conversation-id data))))
            (refuse :not-found)
            :else
            (let [id (if (= op :fresh) (:conversation-id data)
                         (:conversation-id data))]
              {:facts [[op (assoc data :conversation-id id)]]
               :value {:id id}}))

          (= op :submit)
          (cond
            prior-turn
            (if (= (:fingerprint prior-turn) (:fingerprint data))
              {:value {:turn-id (:turn-id data) :status (:status prior-turn)
                       :conversation-id (:conversation-id prior-turn)
                       :task-id (:task-id prior-turn) :reused? true}}
              (refuse :conflict))
            pending-compaction (refuse :busy)
            (nil? selected) (refuse :not-found)
            (busy? state) (refuse :busy)
            :else
            (let [fact (assoc data :conversation-id selected :task-id (:attachment state))]
              {:facts [[:turn-accepted fact]]
               :value {:turn-id (:turn-id data) :status :accepted
                       :conversation-id selected :task-id (:attachment state)
                       :reused? false}}))

          (= op :settle)
          (cond
            (nil? prior-turn) (refuse :not-found)
            (not= :accepted (:status prior-turn))
            {:value {:turn-id (:turn-id data) :status (:status prior-turn)}}
            :else
            {:facts [[:turn-settled data]]
             :value {:turn-id (:turn-id data) :status (:status data)}})

          (= op :observe)
          (if (= :accepted (:status prior-turn))
            {:facts [[:turn-observed data]] :value {:status :observed}}
            (refuse :not-found))

          (= op :stop)
          (if (= :accepted (:status prior-turn))
            {:facts [[:stop-requested data]] :value {:status :requested}}
            (refuse :not-found))

          (= op :compact-start)
          (cond
            prior-compaction
            (cond
              (not= selected (:conversation-id prior-compaction)) (refuse :conflict)
              (= :completed (:status prior-compaction))
              (if (and (= (:baseline-leaf-id data) (:baseline-leaf-id prior-compaction))
                       (= (:baseline-entry-count data)
                          (:baseline-entry-count prior-compaction)))
                {:value {:status :completed :command-id (:command-id data)
                         :reused? true}}
                (refuse :conflict))
              (= :pending (:status prior-compaction))
              (if (and (= (:baseline-leaf-id data) (:baseline-leaf-id prior-compaction))
                       (= (:baseline-entry-count data)
                          (:baseline-entry-count prior-compaction)))
                {:value {:status :pending :command-id (:command-id data)
                         :conversation-id (:conversation-id prior-compaction)
                         :baseline-leaf-id (:baseline-leaf-id prior-compaction)
                         :baseline-entry-count (:baseline-entry-count prior-compaction)
                         :reused? true :effect-claim? false}}
                (refuse :conflict))
              (= :queued (:status prior-compaction))
              (cond
                (not= (:command-id data) (:pending-compaction state)) (refuse :conflict)
                (busy? state)
                {:value {:status :queued :command-id (:command-id data)
                         :conversation-id selected :reused? true :effect-claim? false}}
                (not (and (valid-command-id? (:baseline-leaf-id data))
                          (integer? (:baseline-entry-count data))
                          (pos? (:baseline-entry-count data)))) (refuse :incorrect)
                :else
                {:facts [[:context-compaction-started
                          (assoc data :conversation-id selected)]]
                 :value {:status :pending :command-id (:command-id data)
                         :conversation-id selected
                         :baseline-leaf-id (:baseline-leaf-id data)
                         :baseline-entry-count (:baseline-entry-count data)
                         :reused? true :effect-claim? true}})
              :else (refuse :conflict))
            pending-compaction (refuse :busy)
            (nil? selected) (refuse :not-found)
            (:attachment state) (refuse :busy)
            (not (get-in state [:contexts selected :canCompact])) (refuse :conflict)
            (busy? state)
            {:facts [[:context-compaction-queued
                      {:command-id (:command-id data) :conversation-id selected :at (:at data)}]]
             :value {:status :queued :command-id (:command-id data)
                     :conversation-id selected :reused? false :effect-claim? false}}
            (not (and (valid-command-id? (:baseline-leaf-id data))
                      (integer? (:baseline-entry-count data))
                      (pos? (:baseline-entry-count data))))
            (refuse :incorrect)
            :else
            (let [fact (assoc data :conversation-id selected)]
              {:facts [[:context-compaction-started fact]]
               :value {:status :pending :command-id (:command-id data)
                       :conversation-id selected
                       :baseline-leaf-id (:baseline-leaf-id data)
                       :baseline-entry-count (:baseline-entry-count data)
                       :reused? false :effect-claim? true}}))

          (= op :compact-complete)
          (cond
            (nil? prior-compaction) (refuse :not-found)
            (= :completed (:status prior-compaction))
            (if (= (:entry-id data) (:entry-id prior-compaction))
              {:value {:status :completed :command-id (:command-id data)}}
              (refuse :conflict))
            (not= (:command-id data) (:pending-compaction state)) (refuse :conflict)
            (not= (:parent-id data) (:baseline-leaf-id prior-compaction)) (refuse :conflict)
            (<= (:entry-count data) (:baseline-entry-count prior-compaction)) (refuse :conflict)
            (busy? state) (refuse :busy)
            :else
            (let [fact (assoc data :conversation-id (:conversation-id prior-compaction))]
              {:facts [[:context-compaction-completed fact]]
               :value {:status :completed :command-id (:command-id data)}}))

          (= op :attach)
          (cond
            prior-routing
            (if (and (= :attach (:operation prior-routing))
                     (= (:task-id data) (:task-id prior-routing))
                     (= (:task-id data) (:attachment state)))
              {:value {:task-id (:task-id data) :status :attached :reused? true}}
              (refuse :conflict))
            pending-compaction (refuse :busy)
            (nil? selected) (refuse :not-found)
            (busy? state) (refuse :busy)
            (not (and owned-task? (owned-task? owner (:task-id data))))
            (refuse :not-found)
            :else {:facts [[:task-attached data]] :value {:task-id (:task-id data)}})

          (= op :detach)
          (cond
            prior-routing
            (if (and (= :detach (:operation prior-routing))
                     (nil? (:attachment state)))
              {:value {:status :detached :reused? true}}
              (refuse :conflict))
            pending-compaction (refuse :busy)
            (busy? state) (refuse :busy)
            :else {:facts [[:task-detached data]] :value {:status :detached}})

          (= op :voice-ended)
          (if pending-compaction
            (if (nil? (:attachment state))
              {:value {:status :detached}}
              (refuse :busy))
            {:facts [[:task-detached data]] :value {:status :detached}})

          :else (refuse :incorrect))]
    (if-not (:facts result)
      (if (:value result) {:command/result (:value result)} result)
      {:command-result/events
       (mapv (fn [[kind data]] (event owner kind data)) (:facts result))
       :command-result/cas
       {:tags #{(owner-tag owner)}
        :predicate-fn (fn [events]
                        (= (:revision state)
                           (:event/id (last (into [] events)))))}
       :command/result (:value result)})))

(defcommand :session fresh
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :fresh context))
(defcommand :session resume
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :resume context))
(defcommand :session submit
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :submit context))
(defcommand :session settle
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :settle context))
(defcommand :session observe
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :observe context))
(defcommand :session stop
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :stop context))
(defcommand :session compact-start
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :compact-start context))
(defcommand :session compact-complete
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :compact-complete context))
(defcommand :session attach
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :attach context))
(defcommand :session detach
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :detach context))
(defcommand :session voice-ended
  {:grain.event-model/reads #{:session/state}
   :grain.event-model/produces #{:session/fact}}
  [context] (decision :voice-ended context))
