(ns awp-v2.session.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [awp-v2.session.read-models :as models]
            [awp-v2.session.schemas]
            [cognitect.anomalies :as anom]))

(defn- missing [] {::anom/category ::anom/not-found ::anom/message "not-found"})
(defn- authorized-state [{:keys [actor-id] {:keys [owner]} :query :as context}]
  (when (= actor-id owner) (models/owner-state context owner)))

(defquery :session selection
  {:grain.event-model/reads #{:session/state}}
  [context]
  (let [state (authorized-state context)]
    (if-let [id (:selection state)]
      {:query/result {:id id}}
      (missing))))

(defquery :session history
  {:grain.event-model/reads #{:session/state}}
  [{:keys [query] :as context}]
  (let [state (authorized-state context)
        conversation (get-in state [:conversations (:conversation-id query)])]
    (if conversation
      {:query/result {:revision (:revision conversation)
                      :entries (vec (take-last 200 (:saved conversation)))}}
      (missing))))

(defquery :session transcript
  {:grain.event-model/reads #{:session/state}}
  [context]
  (let [state (authorized-state context)
        conversation (get-in state [:conversations (:selection state)])]
    (if conversation
      {:query/result {:revision (:public-revision state)
                      :entries (vec (take-last 200 (:live conversation)))}}
      (missing))))

(defquery :session catalog
  {:grain.event-model/reads #{:session/state}}
  [context]
  (let [state (authorized-state context)
        active (:selection state)
        all (vals (:conversations state))
        items (->> all
                   (sort-by (juxt #(if (= active (:id %)) 1 0) :activity)
                            #(compare %2 %1))
                   (take 30)
                   (mapv (fn [c]
                           {:id (:id c) :title (:title c)
                            :updatedAt (:updatedAt c)
                            :messageCount (:message-count c)
                            :active (= active (:id c))})))]
    {:query/result {:sessions items}}))

(defquery :session context
  {:grain.event-model/reads #{:session/state}}
  [context]
  (let [state (authorized-state context)]
    (if-let [id (:selection state)]
      (let [command-id (:last-compaction-id state)
            compaction (get-in state [:compactions command-id])
            pending? (contains? #{:queued :pending :uncertain} (:status compaction))]
        {:query/result (assoc (get-in state [:contexts id])
                              :conversation-id id
                              :compactionCommandId (when (or pending? (= :completed (:status compaction))) command-id)
                              :compactionStatus (name (or (:status compaction) :idle))
                              :compactionReason (when (= :uncertain (:status compaction))
                                                  (some-> (:uncertainty-reason compaction) name))
                              :compactionBaselineLeafId (when (#{:pending :uncertain} (:status compaction))
                                                          (:baseline-leaf-id compaction))
                              :compactionBaselineEntryCount (when (#{:pending :uncertain} (:status compaction))
                                                              (:baseline-entry-count compaction)))})
      (missing))))

(defquery :session routing
  {:grain.event-model/reads #{:session/state}}
  [context]
  (let [state (authorized-state context)]
    (if-let [id (:selection state)]
      {:query/result {:conversation-id id :task-id (:attachment state)}}
      (missing))))

(defquery :session routing-command
  {:grain.event-model/reads #{:session/state}}
  [{:keys [query] :as context}]
  (let [state (authorized-state context)]
    (if state
      {:query/result (or (get-in state [:routing-commands (:command-id query)])
                         {:operation nil})}
      (missing))))

(defquery :session turn
  {:grain.event-model/reads #{:session/state}}
  [{:keys [query] :as context}]
  (let [state (authorized-state context)
        turn (get-in state [:turns (:turn-id query)])]
    (if turn
      {:query/result (select-keys turn [:turn-id :status :conversation-id :task-id :answer :stop-requested?])}
      (missing))))
