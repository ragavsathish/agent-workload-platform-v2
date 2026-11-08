(ns awp-v2.session.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(defn- timestamp [at]
  (.toEpochMilli (.toInstant (java.time.OffsetDateTime/parse at))))

(defn- entry [role text id at & [status]]
  (cond-> {:id id :timestamp (timestamp at) :role role
           :text (subs text 0 (min 24000 (count text)))}
    status (assoc :status status)))

(defn- append-entry [entries value]
  (->> (conj (vec entries) value) (take-last 200) vec))

(defn- update-entry [entries pred f]
  (mapv (fn [item] (if (pred item) (f item) item)) entries))

(defn- apply-fact [owner-state kind data]
  (let [id (:conversation-id data)
        selected (:selection owner-state)]
    (case kind
      :fresh
      (-> owner-state
          (assoc :selection id :attachment nil)
          (update :clock (fnil inc 0))
          (assoc-in [:contexts id]
                    {:revision 0 :tokens nil :contextWindow nil :percent nil
                     :estimated false :canCompact false})
          (assoc-in [:conversations id]
                    {:id id :title "Voice orchestrator" :message-count 0
                     :activity (inc (or (:clock owner-state) 0))
                     :updatedAt (:at data) :revision 0 :saved [] :live []})
          (assoc-in [:selection-commands (:command-id data)]
                    {:operation :fresh :input nil :id id}))

      :resume
      (-> owner-state
          (assoc :selection id :attachment nil)
          (assoc-in [:conversations id :live] (get-in owner-state [:conversations id :saved]))
          (assoc-in [:selection-commands (:command-id data)]
                    {:operation :resume :input id :id id}))

      :turn-accepted
      (let [task-id (:task-id data)
            user (entry :user (:prompt data) (str (:turn-id data) "-user") (:at data))]
        (-> owner-state
            (assoc-in [:turns (:turn-id data)]
                      (assoc data :status :accepted :stop-requested? false))
            (update-in [:conversations id :live] append-entry user)
            (cond-> (nil? task-id)
              (update-in [:conversations id :saved] append-entry user)
              (nil? task-id)
              (update-in [:conversations id :message-count] (fnil inc 0))
              (nil? task-id)
              (update-in [:conversations id :revision] (fnil inc 0))
              (nil? task-id)
              (assoc-in [:conversations id :title]
                        (if (zero? (get-in owner-state [:conversations id :message-count] 0))
                          (let [text (:prompt data)]
                            (if (> (count text) 72) (str (subs text 0 69) "…") text))
                          (get-in owner-state [:conversations id :title]))))))

      :turn-observed
      (let [observation (:observation data)
            turn (get-in owner-state [:turns (:turn-id data)])
            id (:conversation-id turn)
            task? (some? (:task-id turn))]
        (case (:kind observation)
          :assistant-delta
          (update-in owner-state [:conversations id :live]
                     (fn [items]
                       (let [last-entry (last items)]
                         (if (and (= :assistant (:role last-entry))
                                  (= :running (:status last-entry)))
                           (update items (dec (count items))
                                   #(update % :text (fn [text]
                                                      (subs (str text (:text observation)) 0
                                                            (min 24000 (count (str text (:text observation))))))))
                           (append-entry items (entry :assistant (:text observation)
                                                      (str (:turn-id data) "-assistant")
                                                      (:at data) :running))))))
          :tool-start
          (let [tool (entry :tool (:name observation) (:tool-id observation)
                            (:at data) :running)]
            (cond-> (update-in owner-state [:conversations id :live] append-entry tool)
              (not task?) (update-in [:conversations id :saved] append-entry tool)
              (not task?) (update-in [:conversations id :revision] (fnil inc 0))))
          :tool-end
          (let [finish #(update-entry %
                        (fn [item] (= (:id item) (:tool-id observation)))
                        (fn [item] (assoc item :status (if (:failed? observation)
                                                         :failed :completed))))]
            (cond-> (update-in owner-state [:conversations id :live] finish)
              (not task?) (update-in [:conversations id :saved] finish)
              (not task?) (update-in [:conversations id :revision] (fnil inc 0))))
          :context-usage
          (if task?
            owner-state
            (let [tokens (:tokens observation)
                  window (:contextWindow observation)]
              (assoc-in owner-state [:contexts id]
                        {:revision (inc (get-in owner-state [:contexts id :revision] 0))
                         :tokens tokens :contextWindow window
                         :percent (when (and tokens window (pos? window))
                                    (min 100.0 (* 100.0 (/ tokens window))))
                         :estimated (:estimated observation)
                         :canCompact (:canCompact observation)})))
          owner-state))

      :turn-settled
      (let [turn (get-in owner-state [:turns (:turn-id data)])
            id (:conversation-id turn)
            task? (some? (:task-id turn))
            answer (:answer data)
            assistant (when (and (string? answer) (seq answer))
                        (entry :assistant answer (str (:turn-id data) "-assistant")
                               (:at data) :completed))
            partial (when-not assistant
                      (some (fn [item]
                              (when (and (= (:id item) (str (:turn-id data) "-assistant"))
                                         (= :running (:status item)))
                                (assoc item :status (:status data))))
                            (get-in owner-state [:conversations id :live])))
            settled (-> owner-state
                        (assoc-in [:turns (:turn-id data) :status] (:status data))
                        (assoc-in [:turns (:turn-id data) :answer] answer))
            settled (if assistant
                      (update-in settled [:conversations id :live]
                                 (fn [entries]
                                   (if (= :running (:status (last entries)))
                                     (conj (pop entries) assistant)
                                     (append-entry entries assistant))))
                      settled)
            settled (if partial
                      (cond-> (update-in settled [:conversations id :live]
                                         update-entry
                                         (fn [item] (= (:id item) (:id partial)))
                                         (fn [_] partial))
                        (not task?)
                        (update-in [:conversations id :saved] append-entry partial))
                      settled)
            settled (if (and assistant (not task?))
                      (-> settled
                          (update-in [:conversations id :saved] append-entry assistant)
                          (update-in [:conversations id :message-count] (fnil inc 0)))
                      settled)]
        (if task? settled
            (-> settled
                (update :clock (fnil inc 0))
                (assoc-in [:conversations id :activity] (inc (or (:clock settled) 0)))
                (assoc-in [:conversations id :updatedAt] (:at data))
                (update-in [:conversations id :revision] (fnil inc 0)))))

      :stop-requested
      (assoc-in owner-state [:turns (:turn-id data) :stop-requested?] true)

      :compact-queued
      (update owner-state :queued-compactions (fnil conj []) (:command-id data))

      :context-compaction-queued
      (-> owner-state
          (assoc :pending-compaction (:command-id data)
                 :last-compaction-id (:command-id data))
          (assoc-in [:compactions (:command-id data)]
                    (assoc data :status :queued))
          (update-in [:contexts id :revision] (fnil inc 0))
          (assoc-in [:contexts id :canCompact] false))

      :context-compaction-started
      (-> owner-state
          (assoc :pending-compaction (:command-id data)
                 :last-compaction-id (:command-id data))
          (assoc-in [:compactions (:command-id data)]
                    (assoc data :status :pending))
          (update-in [:contexts id :revision] (fnil inc 0))
          (assoc-in [:contexts id :canCompact] false))

      :context-compaction-uncertain
      (-> owner-state
          ;; Keep pending-compaction set: uncertainty is a held owner lock.
          (assoc-in [:compactions (:command-id data) :status] :uncertain)
          (assoc-in [:compactions (:command-id data) :uncertainty-reason] (:reason data))
          (update-in [:contexts id :revision] (fnil inc 0))
          (assoc-in [:contexts id :canCompact] false))

      :context-compaction-rejected
      (-> owner-state
          (assoc :pending-compaction nil)
          (assoc-in [:compactions (:command-id data) :status] :rejected)
          (assoc-in [:compactions (:command-id data) :rejection-reason] (:reason data))
          (update-in [:contexts id :revision] (fnil inc 0))
          (assoc-in [:contexts id :canCompact] false))

      :context-compaction-completed
      (-> owner-state
          (assoc :pending-compaction nil)
          (assoc-in [:compactions (:command-id data) :status] :completed)
          (assoc-in [:compactions (:command-id data) :entry-id] (:entry-id data))
          (assoc-in [:contexts id :revision]
                    (inc (get-in owner-state [:contexts id :revision] 0)))
          (assoc-in [:contexts id :canCompact] false)
          (cond-> (contains? data :tokens)
            (assoc-in [:contexts id :tokens] (:tokens data)))
          (cond-> (contains? data :contextWindow)
            (assoc-in [:contexts id :contextWindow] (:contextWindow data)))
          (assoc-in [:contexts id :estimated] true))

      :context-compacted
      (-> owner-state
          (assoc :queued-compactions [])
          (assoc-in [:contexts id]
                    (-> (get-in owner-state [:contexts id])
                        (update :revision (fnil inc 0))
                        (assoc :canCompact false)
                        (cond-> (contains? data :tokens)
                          (assoc :tokens (:tokens data)
                                 :contextWindow (:contextWindow data)
                                 :estimated true)))))

      :task-attached
      (cond-> (assoc owner-state :attachment (:task-id data))
        (:command-id data)
        (assoc-in [:routing-commands (:command-id data)]
                  {:operation :attach :task-id (:task-id data)}))
      :task-detached
      (cond-> (assoc owner-state :attachment nil)
        (:command-id data)
        (assoc-in [:routing-commands (:command-id data)]
                  {:operation :detach}))
      owner-state)))

(defreadmodel :session state
  {:events #{:session/fact} :version 2 :schema [:map-of :string :map]}
  "Owner selections, turns, safe transcript entries, and context from facts."
  [state event]
  (let [owner (:owner event)
        current (get state owner {})
        next (apply-fact current (:kind event) (:data event))]
    (assoc state owner (-> next
                           (assoc :revision (:event/id event))
                           (update :public-revision (fnil inc 0))))))

(defn owners [context] (rmp/project context :session/state))
(defn owner-state [context owner] (get (owners context) owner))
