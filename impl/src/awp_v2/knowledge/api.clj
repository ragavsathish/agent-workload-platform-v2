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
(declare recover!)
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
                        :closed? (atom false)
                        :tenant-id (UUID/nameUUIDFromBytes (.getBytes (.getCanonicalPath dir) "UTF-8")))]
      (when (:submit-task! system) (recover! system))
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
(defn- issue! [system actor op data]
  (loop [attempt 0]
    (let [command {:command/name (keyword "knowledge" (name op)) :command/id (random-uuid)
                   :command/timestamp (OffsetDateTime/now) :owner actor :data data}
          result (cp/process-command (assoc system :actor-id actor :command command))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result)) (< attempt 12))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (response result :command/result)))))
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
                (issue! system actor :submission {:source-id source-id :mode mode :task-id (str task-id)})
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
                      (str/includes? (str/lower-case transcript) (str/lower-case evidence)))) suggestions)
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
                                     :output-digest (digest [status suggestions (:reason payload)])
                                     :reason (:reason payload)})
      {:error :incorrect})))
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
              (issue! system actor :daily-submission {:account-id account-id :task-id (str task-id)})
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
(defn daily-result! [system actor account-id {:keys [title story reason sources]}]
  (if (and (string? account-id)
           (and (vector? sources) (or (and (string? title) (not (str/blank? title)) (<= (count title) 200)
                    (string? story) (not (str/blank? story)) (<= (count story) 8000))
               (and (string? reason) (not (str/blank? reason))))))
    (issue! system actor :daily-result {:account-id account-id
                                         :source-digest (digest (canonical-sources sources))
                                         :status (if story :completed :failed)
                                         :title title :story story :reason reason})
    {:error :incorrect}))

(defn recover! [system]
  (doseq [[owner state] (models/owners system)]
    (doseq [source-id (keys (:sources state))]
      (submit-extractions! system owner source-id))
    (doseq [account-id (keys (:accounts state))]
      (submit-daily! system owner account-id)))
  {:status :reconciled})
