(ns awp-v2.knowledge.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.knowledge.commands]
            [awp-v2.knowledge.event-model]
            [awp-v2.knowledge.queries]
            [awp-v2.knowledge.read-models :as models]
            [awp-v2.knowledge.schemas]
            [awp-v2.knowledge.taxonomy :as taxonomy]
            [awp-v2.session.read-models :as session-models]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.security MessageDigest]
           [java.time OffsetDateTime LocalDate ZoneId]
           [java.util UUID]))

(defn- digest [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (pr-str value) "UTF-8"))))
(defn- stable-id [value] (str (UUID/nameUUIDFromBytes (.getBytes value "UTF-8"))))
(declare recover! daily-result! ingest-worker-result! ingest-daily-worker-result! schedule-worker!
         taxonomy-propose! taxonomy-review! taxonomy-map! taxonomy-item taxonomy-current
         taxonomy-relation! taxonomy-relations taxonomy-relation-review! taxonomy-model-outcome!
         taxonomy-proposals nonblank-bounded?)
(defn open-system [{:keys [storage-dir] :as options}]
  (validator/verify-or-throw!)
  (let [owned? (nil? storage-dir)
        dir (if storage-dir (io/file storage-dir)
                (.toFile (Files/createTempDirectory "awp-v2-knowledge-"
                                                  (make-array java.nio.file.attribute.FileAttribute 0))))
        _ (.mkdirs dir)
        store (es/start {:conn {:type :sqlite :database-file (str (io/file dir "knowledge-events.sqlite"))}})
        cache (kv/start (lmdb/->KV-Store-LMDB {:storage-dir (.getPath dir) :db-name "knowledge"}))]
    (let [system (assoc options :event-store store :cache cache :cache-dir dir :owned-dir? owned?
                        :closed? (atom false) :inflight (atom #{})
                        :tenant-id (UUID/nameUUIDFromBytes (.getBytes (.getCanonicalPath dir) "UTF-8")))]
      (when (or (:submit-task! system) (:run-worker! system)) (recover! system))
      system)))
(defn close-system! [{:keys [event-store cache cache-dir owned-dir? closed?]}]
  (when (compare-and-set! closed? false true)
    (kv/stop cache) (es/stop event-store) (rmp/l1-clear!)
    (when owned-dir? (doseq [f (reverse (file-seq cache-dir))] (io/delete-file f true)))))
(defn- response [result kind]
  (if-let [value (get result kind)] value
    (if-let [category (::anom/category result)]
      {:error (keyword (::anom/message result)) :category category}
      {:error :incorrect})))
(defn- issue-as! [system actor owner op data]
  (loop [attempt 0]
    (let [command {:command/name (keyword "knowledge" (name op)) :command/id (random-uuid)
                   :command/timestamp (OffsetDateTime/now) :owner owner :data data}
          result (cp/process-command (assoc system :actor-id actor :command command))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result)) (< attempt 12))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (let [value (response result :command/result)]
          (when (and (:after-write! system) (not (:error value)))
            (try ((:after-write! system) system owner)
                 (catch Throwable _ nil)))
          value)))))
(defn- issue! [system actor op data] (issue-as! system actor actor op data))
(defn- read! [system actor op data]
  (response (qp/process-query (assoc system :actor-id actor
                                     :query (merge {:query/name (keyword "knowledge" (name op))
                                                    :query/id (random-uuid) :query/timestamp (OffsetDateTime/now)
                                                    :owner actor} data))) :query/result))
(defn tasks [system actor source-id] (read! system actor :tasks {:source-id source-id}))
(defn suggestions [system actor] (read! system actor :suggestions {}))
(defn item [system actor item-id] (read! system actor :item {:item-id item-id}))
(defn profile [system actor] (read! system actor :profile {}))
(defn account [system actor account-id] (read! system actor :account {:account-id account-id}))
(defn accounts [system actor] (read! system actor :accounts {}))
(defn taxonomy-item [system actor proposal-id]
  (read! system actor :taxonomy-item {:proposal-id proposal-id}))
(defn taxonomy-proposals [system actor] (read! system actor :taxonomy-proposals {}))
(defn taxonomy-current [system actor] (read! system actor :taxonomy-current {}))
(defn taxonomy-relations [system actor] (read! system actor :taxonomy-relations {}))
(defn taxonomy-relation-item [system actor relation-id]
  (read! system actor :taxonomy-relation-item {:relation-id relation-id}))
(defn taxonomy-relation-proposals [system actor]
  (read! system actor :taxonomy-relation-proposals {}))
(defn taxonomy-relation-outcomes [system actor]
  (read! system actor :taxonomy-relation-outcomes {}))
(defn taxonomy-outcomes [system actor] (read! system actor :taxonomy-outcomes {}))
(defn quality-cases [system actor] (read! system actor :quality-cases {}))
(defn quality-run [system actor run-id] (read! system actor :quality-run-status {:run-id run-id}))

(defn quality-register-case! [system owner {:keys [case-id source-id branch family-id split source-digest] :as data}]
  (if (and (every? #(nonblank-bounded? % 256) [case-id source-id family-id source-digest])
           (#{:journal :assertion :concept :relation} branch)
           (#{:development :challenge :locked_holdout :production_shadow} split)
           (= (set (keys data)) #{:case-id :source-id :branch :family-id :split :source-digest}))
    (issue! system owner :quality-case data)
    {:error :incorrect}))

(defn quality-review-case! [system reviewer owner {:keys [case-id action-id decision privacy-status expected-digest reviewer-role] :as data}]
  (if (and (every? #(nonblank-bounded? % 256) [case-id action-id expected-digest reviewer])
           (#{:approved :rejected :disputed :adjudicated} decision)
           (#{:independent :adjudicator} (or reviewer-role :independent))
           (#{:passed :failed} privacy-status))
    (issue-as! system reviewer owner :quality-review
               (assoc (select-keys data [:case-id :action-id :decision :privacy-status :expected-digest])
                      :reviewer-id reviewer :reviewer-role (or reviewer-role :independent)
                      :at (str (OffsetDateTime/now))))
    {:error :incorrect}))

(defn quality-start-run! [system owner {:keys [run-id policy-digest corpus-digest baseline-id candidate-id
                                              rollback-id config-digest split case-ids] :as data}]
  (if (and (every? #(nonblank-bounded? % 256) [run-id policy-digest corpus-digest baseline-id
                                               candidate-id rollback-id config-digest])
           (#{:development :challenge :locked_holdout :production_shadow} split)
           (vector? case-ids) (seq case-ids) (every? #(nonblank-bounded? % 256) case-ids)
           (= (set (keys data)) #{:run-id :policy-digest :corpus-digest :baseline-id :candidate-id
                                  :rollback-id :config-digest :split :case-ids}))
    (issue! system owner :quality-run data)
    {:error :incorrect}))

(defn quality-consume-holdout! [system owner run-id]
  (if (nonblank-bounded? run-id 256)
    (issue! system owner :quality-holdout {:run-id run-id})
    {:error :incorrect}))

(defn quality-record-verdict! [system owner run-id outcome metrics-digest]
  (if (and (nonblank-bounded? run-id 256) (nonblank-bounded? metrics-digest 256)
           (#{:open :failed :passed} outcome))
    (issue! system owner :quality-verdict {:run-id run-id :outcome outcome :metrics-digest metrics-digest})
    {:error :incorrect}))

(defn- nonblank-bounded? [value max-length]
  (and (string? value) (<= 1 (count value) max-length) (not (str/blank? value))))
(defn taxonomy-propose!
  [system actor {:keys [proposal-id result-id source-id category-id taxonomy-version text evidence
                        model-version prompt-version] :as data}]
  (if (and (every? #(nonblank-bounded? % 256)
                   [proposal-id result-id source-id category-id taxonomy-version model-version prompt-version])
           (nonblank-bounded? text 1000) (nonblank-bounded? evidence 1000)
           (taxonomy/publishable? taxonomy-version category-id))
    (issue! system actor :taxonomy-proposal
            (assoc (select-keys data [:proposal-id :result-id :source-id :category-id :taxonomy-version
                                      :text :evidence :model-version :prompt-version :candidate-uri :trace-id])
                   :digest (digest (select-keys data [:proposal-id :result-id :source-id :category-id
                                                       :taxonomy-version :text :evidence :model-version
                                                       :prompt-version :candidate-uri :trace-id]))))
    {:error :incorrect}))

(defn taxonomy-review! [system actor proposal-id action-id expected-revision decision]
  (if (and (nonblank-bounded? proposal-id 256) (nonblank-bounded? action-id 256)
           (integer? expected-revision) (<= 0 expected-revision)
           (#{:accepted :rejected :revoked} decision))
    (issue! system actor :taxonomy-review {:proposal-id proposal-id :action-id action-id
                                           :expected-revision expected-revision :decision decision
                                           :at (str (OffsetDateTime/now))})
    {:error :incorrect}))

(defn taxonomy-map! [system actor proposal-id to-version category-id decision-id]
  (if (and (every? #(nonblank-bounded? % 256) [proposal-id to-version category-id decision-id])
           (taxonomy/publishable? to-version category-id))
    (issue! system actor :taxonomy-map {:proposal-id proposal-id :to-version to-version
                                        :category-id category-id :decision-id decision-id})
    {:error :incorrect}))

(defn taxonomy-model-outcome! [system actor result-id source-id status model-version prompt-version]
  (if (and (every? #(nonblank-bounded? % 256) [result-id source-id model-version prompt-version])
           (#{:empty :timeout :invalid :failed} status))
    (issue! system actor :taxonomy-model-outcome
            {:result-id result-id :source-id source-id :status status
             :model-version model-version :prompt-version prompt-version
             :digest (digest [result-id source-id status model-version prompt-version])})
    {:error :incorrect}))

(defn taxonomy-relation-attempt! [system actor {:keys [owner result-id source-id source-digest
                                                        model-version prompt-version policy-version
                                                        status trace-id candidates] :as data}]
  (if (and (every? #(nonblank-bounded? % 256)
                   [owner result-id source-id source-digest model-version prompt-version policy-version])
           (= owner actor)
           (#{:completed :empty :invalid :timeout :failed} status)
           (vector? candidates) (<= (count candidates) 2)
           (or (= :completed status) (empty? candidates))
           (or (not= :completed status) (seq candidates))
           (or (and (= :completed status) (nonblank-bounded? trace-id 256))
               (and (not= :completed status)
                    (or (nil? trace-id) (nonblank-bounded? trace-id 256))))
           (= (set (keys data)) #{:owner :result-id :source-id :source-digest :model-version
                                  :prompt-version :policy-version :status :trace-id :candidates}))
    (issue! system actor :taxonomy-relation-attempt (assoc data :digest (digest data)))
    {:error :incorrect}))

(defn taxonomy-relation! [system actor {:keys [relation-id left-id right-id relation-kind source-id evidence] :as data}]
  (if (and (every? #(nonblank-bounded? % 256) [relation-id left-id right-id source-id])
           (nonblank-bounded? evidence 1000) (#{:broader :related} relation-kind))
    (issue! system actor :taxonomy-relation (select-keys data [:relation-id :left-id :right-id
                                                               :relation-kind :source-id :evidence
                                                               :result-id :source-digest :model-version
                                                               :prompt-version :policy-version :trace-id
                                                               :left-revision :right-revision :origin
                                                               :replaces-id :owner]))
    {:error :incorrect}))

(defn taxonomy-relation-review! [system actor relation-id action-id expected-revision decision]
  (if (and (every? #(nonblank-bounded? % 256) [relation-id action-id])
           (integer? expected-revision) (<= 0 expected-revision)
           (#{:accepted :rejected :revoked} decision))
    (issue! system actor :taxonomy-relation-review
            {:relation-id relation-id :action-id action-id :expected-revision expected-revision
             :decision decision :at (str (OffsetDateTime/now))})
    {:error :incorrect}))

(defn taxonomy-relation-correct!
  [system actor relation-id action-id expected-revision replacement-id left-id right-id relation-kind evidence]
  (if (and (every? #(nonblank-bounded? % 256)
                   [relation-id action-id replacement-id left-id right-id])
           (integer? expected-revision) (<= 0 expected-revision)
           (#{:broader :related} relation-kind) (nonblank-bounded? evidence 1000))
    (issue! system actor :taxonomy-relation-correct
            {:relation-id relation-id :action-id action-id :expected-revision expected-revision
             :replacement-id replacement-id :left-id left-id :right-id right-id
             :relation-kind relation-kind :evidence evidence :at (str (OffsetDateTime/now))})
    {:error :incorrect}))

(defn- valid-turn? [{:keys [source-id conversation-id message-id transcript captured-at status origin]}]
  (and (= :completed status) (= :owner origin)
       (every? #(and (string? %) (not (str/blank? %))) [source-id conversation-id message-id transcript captured-at])
       (<= (count transcript) 20000)
       (= source-id (str conversation-id ":" message-id))
       (try (OffsetDateTime/parse captured-at) true (catch Exception _ false))))
(defn- submit-extractions! [system actor source-id]
  (let [source (get-in (models/owner-state system actor) [:sources source-id])]
    (doseq [mode [:journal :classification]
            :let [work (get-in (models/owner-state system actor) [:work source-id mode])]
            :when (and work (nil? (:task-id work)))]
      (let [request {:owner actor :source-id source-id :mode mode
                     :request-id (:request-id work)
                     :conversation-id (:conversation-id source) :message-id (:message-id source)
                     :transcript (:transcript source)
                     :captured-at (:captured-at source)}]
        (try
          (if-let [submit (:submit-task! system)]
            (let [accepted (submit request)]
              (if-let [task-id (:task-id accepted)]
                (do (issue! system actor :submission {:source-id source-id :mode mode :task-id (str task-id)})
                    (schedule-worker! system (assoc request :task-id (str task-id))))
                (issue! system actor :submission-uncertain {:source-id source-id :mode mode})))
            (issue! system actor :submission-uncertain {:source-id source-id :mode mode}))
          (catch Throwable _
            (issue! system actor :submission-uncertain {:source-id source-id :mode mode})))))))

(defn ingest-turn! [system actor turn]
  (if-not (valid-turn? turn) {:status :ignored}
    (let [source-id (:source-id turn)
          request-id (fn [mode] (stable-id (str "knowledge:" actor ":" source-id ":" (name mode))))
          result (issue! system actor :intake
                         (assoc (select-keys turn [:source-id :conversation-id :message-id :transcript :captured-at])
                                :owner actor :journal-request-id (request-id :journal)
                                :classification-request-id (request-id :classification)))]
      (when (#{:accepted :reused} (:status result))
        (submit-extractions! system actor source-id))
      result)))
(defn ingest-session-turn! [system session-system actor turn-id]
  (let [turn (get-in (session-models/owner-state session-system actor) [:turns turn-id])]
    (if (and turn (nil? (:task-id turn)))
      (ingest-turn! system actor {:source-id (str (:conversation-id turn) ":" turn-id)
                                  :conversation-id (:conversation-id turn) :message-id turn-id
                                  :transcript (:prompt turn) :captured-at (:at turn)
                                  :status (:status turn)
                                  :origin (if (= :bootstrap (:origin turn)) :developer :owner)})
      {:status :ignored})))
(defn- valid-suggestions? [transcript suggestions]
  (and (vector? suggestions) (<= (count suggestions) 12)
       (every? (fn [{:keys [item-id kind text evidence]}]
                 (and (string? item-id) (<= 1 (count item-id) 128)
                      (#{:journal :assertion} kind)
                      (string? text) (<= 1 (count text) 1000)
                      (string? evidence) (not (str/blank? evidence))
                      (str/includes? transcript evidence))) suggestions)
       (= (count suggestions) (count (set (map :item-id suggestions))))))
(defn outcome! [system actor source-id mode status payload]
  (let [state (models/owner-state system actor)
        source (get-in state [:sources source-id])
        work (get-in state [:work source-id mode])
        suggestions (vec (:suggestions payload []))]
    (if (and source work (= (str (:task-id payload)) (:task-id work))
             (#{:journal :classification} mode) (#{:completed :failed} status)
             (or (= status :failed) (valid-suggestions? (:transcript source) suggestions))
             (every? #(= mode (case (:kind %) :journal :journal :assertion :classification nil)) suggestions))
      (issue! system actor :outcome {:owner actor :source-id source-id :mode mode :status status
                                     :suggestions (if (= status :completed) suggestions [])
                                     :task-id (:task-id work)
                                     :output-digest (digest [status suggestions (:reason payload) (:result-id payload) (:model payload) (:processor-version payload)])
                                     :result-id (:result-id payload) :model (:model payload)
                                     :processor-version (:processor-version payload)
                                     :classification (:classification payload)
                                     :reason (:reason payload)})
      {:error :incorrect})))
(defn ingest-worker-result! [system actor {:keys [result-id task-id source-id mode status suggestions
                                                  model processor-version reason classification]}]
  (let [mode (if (keyword? mode) mode (keyword mode))
        status (if (keyword? status) status (keyword status))]
    (if (and (string? result-id) (not (str/blank? result-id))
             (string? task-id) (string? source-id)
             (string? model) (not (str/blank? model))
             (string? processor-version) (not (str/blank? processor-version))
             (#{:journal :classification} mode) (#{:completed :failed} status))
      (outcome! system actor source-id mode status
                {:task-id task-id :suggestions (mapv #(update % :kind (fn [k] (if (keyword? k) k (keyword k)))) suggestions)
                 :reason reason :result-id result-id :model model :processor-version processor-version
                 :classification classification})
      {:error :incorrect})))

(defn ingest-daily-worker-result! [system actor {:keys [account-id task-id result-id title story reason sources model processor-version]}]
  (let [account (get-in (models/owner-state system actor) [:accounts account-id])]
    (if (and account (= task-id (:task-id account))
             (string? result-id) (not (str/blank? result-id))
             (string? model) (not (str/blank? model))
             (string? processor-version) (not (str/blank? processor-version)))
      (daily-result! system actor account-id {:title title :story story :reason reason :sources sources
                                               :result-id result-id :model model
                                               :processor-version processor-version})
      {:error :conflict})))

(defn review! [system actor item-id {:keys [action-id expected-revision decision text] :as command}]
  (if (and (string? action-id) (<= 1 (count action-id) 128)
           (integer? expected-revision) (<= 0 expected-revision)
           (#{:accepted :corrected :rejected} decision)
           (or (not= decision :corrected) (and (string? text) (<= 1 (count text) 1000)))
           (or (= decision :corrected) (nil? text)))
    (issue! system actor :review (assoc (select-keys command [:action-id :expected-revision :decision :text])
                                        :item-id item-id :at (str (OffsetDateTime/now))))
    {:error :incorrect}))
(defn- valid-source? [date zone {:keys [source-ref speaker captured-at transcript]}]
  (and (string? source-ref) (<= 1 (count source-ref) 512)
       (= :owner speaker) (string? transcript) (<= 1 (count transcript) 20000)
       (try (= (LocalDate/parse date)
               (.toLocalDate (.atZoneSameInstant (OffsetDateTime/parse captured-at) (ZoneId/of zone))))
            (catch Exception _ false))))
(defn- canonical-sources [sources] (vec (sort-by :source-ref sources)))
(defn- submit-daily! [system actor account-id]
  (let [account (get-in (models/owner-state system actor) [:accounts account-id])]
    (when (and account (nil? (:task-id account)))
      (try
        (if-let [submit (:submit-task! system)]
          (let [accepted (submit (assoc (select-keys account [:account-id :owner :journal-date :time-zone
                                                              :sources :source-refs :source-digest])
                                        :mode :daily-account :request-id account-id))]
            (if-let [task-id (:task-id accepted)]
              (do (issue! system actor :daily-submission {:account-id account-id :task-id (str task-id)})
                  (schedule-worker! system (assoc account :mode :daily-account :task-id (str task-id))))
              (issue! system actor :daily-uncertain {:account-id account-id})))
          (issue! system actor :daily-uncertain {:account-id account-id}))
        (catch Throwable _ (issue! system actor :daily-uncertain {:account-id account-id}))))))
(defn daily-request! [system actor {:keys [journal-date time-zone sources]}]
  (if (and (string? actor) (not (str/blank? actor))
           (vector? sources) (<= 1 (count sources) 300)
           (try (ZoneId/of time-zone) (LocalDate/parse journal-date) true (catch Exception _ false))
           (every? #(valid-source? journal-date time-zone %) sources)
           (= (count sources) (count (set (map :source-ref sources))))
           (<= (reduce + (map #(count (:transcript %)) sources)) 200000))
    (let [sources (canonical-sources sources)
          source-digest (digest sources)
          account-id (stable-id (pr-str [actor journal-date time-zone source-digest]))
          request {:account-id account-id :owner actor :journal-date journal-date
                   :time-zone time-zone :sources sources :source-refs (mapv :source-ref sources)
                   :source-digest source-digest}
          result (issue! system actor :daily-request request)]
      (when (#{:accepted :reused} (:status result))
        (submit-daily! system actor account-id))
      result)
    {:error :incorrect}))
(defn daily-result! [system actor account-id {:keys [title story reason sources result-id model processor-version]}]
  (if (and (string? account-id)
           (and (vector? sources) (or (and (string? title) (not (str/blank? title)) (<= (count title) 200)
                    (string? story) (not (str/blank? story)) (<= (count story) 8000))
               (and (string? reason) (not (str/blank? reason))))))
    (issue! system actor :daily-result {:account-id account-id
                                         :source-digest (digest (canonical-sources sources))
                                         :status (if story :completed :failed)
                                         :title title :story story :reason reason
                                         :result-id result-id :model model :processor-version processor-version
                                         :output-digest (digest [title story reason result-id model processor-version])})
    {:error :incorrect}))

(defn schedule-worker! [system {:keys [owner source-id mode account-id task-id request-id] :as request}]
  (when-let [run (:run-worker! system)]
    (let [key [owner (or source-id account-id) mode task-id]
          [before _] (swap-vals! (:inflight system) conj key)]
      (when-not (contains? before key)
        (future
          (try
            (let [result (run request)]
              (if (= mode :daily-account)
                (ingest-daily-worker-result! system owner
                  {:account-id account-id :task-id task-id :result-id (:result-id result)
                   :title (:title result) :story (:story result) :reason (:reason result)
                   :sources (mapv #(update % :speaker keyword) (:sources result))
                   :model (:model result) :processor-version (:processor-version result)})
                (ingest-worker-result! system owner
                  {:source-id source-id :task-id task-id :result-id (:result-id result)
                   :mode mode :status (:status result) :reason (:reason result)
                   :suggestions (:suggestions result) :classification (:classification result)
                   :model (:model result) :processor-version (:processor-version result)})))
            (catch Throwable error
              (when (not= mode :daily-account)
                (ingest-worker-result! system owner
                  {:source-id source-id :task-id task-id :result-id (str task-id ":failed")
                   :mode mode :status :failed :reason (or (.getMessage error) "worker failed")
                   :suggestions [] :model "worker-error" :processor-version "knowledge-v2-1"})))
            (finally (swap! (:inflight system) disj key))))))))

(defn recover! [system]
  (doseq [[owner state] (models/owners system)]
    (doseq [source-id (keys (:sources state))]
      (submit-extractions! system owner source-id)
      (when (:run-worker! system)
        (doseq [[mode work] (get-in (models/owner-state system owner) [:work source-id])
                :when (and (:task-id work) (#{:pending :uncertain} (:status work)))]
          (schedule-worker! system (assoc (get-in (models/owner-state system owner) [:sources source-id])
                                          :mode mode :task-id (:task-id work) :request-id (:request-id work))))))
    (doseq [account-id (keys (:accounts state))]
      (submit-daily! system owner account-id)
      (let [account (get-in (models/owner-state system owner) [:accounts account-id])]
        (when (and (:run-worker! system) (:task-id account) (#{:pending :uncertain} (:status account)))
          (schedule-worker! system (assoc account :mode :daily-account :request-id account-id))))))
  {:status :reconciled})

(defn legacy-command! [system actor op data]
  (if (#{:legacy-snapshot :legacy-resource :legacy-switch :legacy-rollback} op)
    (issue! system actor op (assoc data :owner actor))
    {:error :incorrect}))
