(ns awp-v2.song.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.song.commands]
            [awp-v2.song.event-model]
            [awp-v2.song.queries]
            [awp-v2.song.read-models :as models]
            [awp-v2.song.schemas]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.security MessageDigest]
           [java.time OffsetDateTime]
           [java.util UUID]))

(defn- stable-id [text] (str (UUID/nameUUIDFromBytes (.getBytes text "UTF-8"))))
(defn- digest [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (pr-str value) "UTF-8"))))
(defn- manifest-digest [manifest]
  (digest (mapv manifest [:title :style :lyrics :duration-seconds :bpm :key-scale :time-signature])))
(defn- text? [value max-length]
  (and (string? value) (<= 1 (count (str/trim value)) max-length)))
(defn- owner? [owner] (text? owner 256))
(declare issue!)
(defn open-system [{:keys [storage-dir] :as options}]
  (validator/verify-or-throw!)
  (let [owned? (nil? storage-dir)
        dir (if storage-dir (io/file storage-dir)
                (.toFile (Files/createTempDirectory "awp-v2-song-"
                                                  (make-array java.nio.file.attribute.FileAttribute 0))))
        _ (.mkdirs dir)
        store (es/start {:conn {:type :sqlite :database-file (str (io/file dir "song-events.sqlite"))}})
        cache (kv/start (lmdb/->KV-Store-LMDB {:storage-dir (.getPath dir) :db-name "song"}))]
    (let [system (assoc options :event-store store :cache cache :cache-dir dir :owned-dir? owned?
                        :closed? (atom false)
                        :tenant-id (UUID/nameUUIDFromBytes (.getBytes (.getCanonicalPath dir) "UTF-8")))]
      (doseq [[owner state] (models/owners system)
              [id row] (:songs state)
              :when (and (= :generating-music (:status row))
                         (= :submitted (:render-submission row)))]
        (issue! system owner :stored {:generation-id id :render-task-id (:render-task-id row)
                                       :artifact-id nil}))
      system)))
(defn close-system! [{:keys [event-store cache cache-dir owned-dir? closed?]}]
  (when (compare-and-set! closed? false true)
    (kv/stop cache) (es/stop event-store) (rmp/l1-clear!)
    (when owned-dir? (doseq [f (reverse (file-seq cache-dir))] (io/delete-file f true)))))
(defn- response [result kind]
  (if-let [value (get result kind)] value
    {:error (keyword (or (::anom/message result) "incorrect"))}))
(defn- issue! [sys owner op data]
  (loop [attempt 0]
    (let [command {:command/name (keyword "song" (name op)) :command/id (random-uuid)
                   :command/timestamp (OffsetDateTime/now) :owner owner :data data}
          result (cp/process-command (assoc sys :actor-id owner :command command))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result)) (< attempt 12))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (response result :command/result)))))
(defn- raw-song [sys owner id]
  (get-in (models/owner-state sys owner) [:songs id]))
(defn- view [row]
  (when row
    (cond-> (select-keys row [:status :manifest :manifest-digest :lyrics-task-id :lyric-result-digest
                              :lyric-attempt :render-task-id :failure])
      true (assoc :id (:generation-id row)
                  :approval (get-in row [:decision :choice])
                  :decision-id (get-in row [:decision :decision-id]))
      (= :complete (:status row)) (assoc :audio-path (str "/v1/music/generations/" (:generation-id row) "/audio")
                                           :artifact-id (:artifact-id row)))))
(declare lyrics-result!)
(defn- source-manifest [result]
  (let [candidate (or (:manifest result)
                      (get-in result [:response :presentation :songManifest]))]
    (when (map? candidate)
      {:title (:title candidate) :style (:style candidate)
       :lyrics (or (:lyrics candidate)
                   (when (vector? (:lyricLines candidate)) (str/join "\n" (:lyricLines candidate))))
       :duration-seconds (or (:duration-seconds candidate) (:duration_seconds candidate)
                             (:durationSeconds candidate))
       :bpm (:bpm candidate) :key-scale (or (:key-scale candidate) (:key_scale candidate)
                                           (:keyScale candidate))
       :time-signature (or (:time-signature candidate) (:time_signature candidate)
                           (:timeSignature candidate))})))
(defn refresh! [sys owner id]
  (let [row (raw-song sys owner id)]
    (when (and row (= :writing-lyrics (:status row)) (:get-task! sys))
      (try
        (let [snapshot ((:get-task! sys) (:lyrics-task-id row))
              state (keyword (str (:state snapshot)))
              result (:result snapshot)
              matches? (and (= (:lyrics-task-id row) (:task_id snapshot) (:task_id result))
                            (= id (:generation_id result))
                            (= owner (:identity result))
                            (pos-int? (:attempt snapshot)))]
          (case state
            :completed (lyrics-result! sys owner id (:lyrics-task-id row)
                                       (when matches? (source-manifest result))
                                       {:lyric-result-digest (when matches? (digest result))
                                        :lyric-attempt (:attempt snapshot)})
            :failed (lyrics-result! sys owner id (:lyrics-task-id row) nil)
            :cancelled (issue! sys owner :cancel {:generation-id id})
            nil)
          snapshot)
        (catch Throwable _ nil)))))
(defn get-song [sys owner id]
  (if (and (owner? owner) (text? id 128))
    (let [snapshot (refresh! sys owner id)
          result (qp/process-query (assoc sys :actor-id owner
                                          :query {:query/name :song/get :query/id (random-uuid)
                                                  :query/timestamp (OffsetDateTime/now)
                                                  :owner owner :generation-id id}))]
      (if-let [row (:query/result result)]
        (cond-> (view row)
          (and (= :writing-lyrics (:status row)) snapshot)
          (assoc :lyric-task-state (keyword (str (:state snapshot)))
                 :recovery-required (= "uncertain" (:state snapshot))))
        {:error :not-found}))
    {:error :not-found}))
(defn- submit-pending! [sys owner id stage]
  (let [row (raw-song sys owner id)
        pending? (= :pending (if (= stage :render) (:render-submission row) (:submission row)))
        task-id (if (= stage :render) (:render-task-id row) (:lyrics-task-id row))]
    (when (and pending? task-id (:submit-task! sys))
      (try
        (let [result ((:submit-task! sys) {:owner owner :generation-id id :stage stage
                                            :task-id task-id :manifest (when (= stage :render) (:manifest row))
                                            :brief (:brief row) :style (:style row)
                                            :duration-seconds (:duration-seconds row)
                                            :workspace (:workspace row)})]
          (when (= task-id (str (:task-id result)))
            (issue! sys owner :submitted {:generation-id id :stage stage})
            (when (and (= stage :render) (:run-render! sys))
              ((:run-render! sys) sys {:owner owner :generation-id id :task-id task-id
                                        :manifest (:manifest row)}))))
        (catch Throwable _ nil)))))
(defn create! [sys owner {:keys [request-id brief style duration-seconds workspace]}]
  (let [brief (when (string? brief) (str/trim brief))
        style (when (string? style) (str/trim style))
        workspace (when (string? workspace) (str/trim workspace))]
    (if-not (and (owner? owner) (text? request-id 128) (text? brief 20000)
                 (text? style 1000) (integer? duration-seconds)
                 (<= 30 duration-seconds 300)
                 (contains? (:eligible-workspaces sys) workspace))
      {:error :incorrect}
      (let [id (stable-id (pr-str [owner request-id]))
            input-digest (digest [owner brief style duration-seconds workspace])
            data {:generation-id id :request-id request-id :input-digest input-digest
                  :lyrics-task-id (stable-id (str "song-lyrics:" id))
                  :brief brief :style style :duration-seconds duration-seconds :workspace workspace}
            result (issue! sys owner :create data)]
        (if (:error result) result
          (do (submit-pending! sys owner id :lyrics) (get-song sys owner id)))))))
(defn- valid-manifest? [{:keys [title style lyrics duration-seconds bpm key-scale time-signature]}]
  (and (text? title 200) (text? style 1000) (text? lyrics 20000)
       (re-find #"(?i)\[(?:Verse(?: [1-9][0-9]*)?|Pre-Chorus|Chorus|Bridge|Outro)\]" lyrics)
       (integer? duration-seconds) (<= 30 duration-seconds 300)
       (integer? bpm) (<= 40 bpm 200)
       (text? key-scale 32) (re-matches #"[A-G](?:#|b)? (?:Major|Minor)" key-scale)
       (#{"3" "4" "6"} time-signature)))
(defn lyrics-result!
  ([sys owner id task-id manifest]
   (lyrics-result! sys owner id task-id manifest nil))
  ([sys owner id task-id manifest provenance]
   (if-not (raw-song sys owner id) {:error :not-found}
     (let [valid? (and (map? manifest) (valid-manifest? manifest))
           result (issue! sys owner :lyrics {:generation-id id :lyrics-task-id task-id
                                              :manifest (when valid? manifest)
                                              :manifest-digest (when valid? (manifest-digest manifest))
                                              :lyric-result-digest (when valid? (:lyric-result-digest provenance))
                                              :lyric-attempt (when valid? (:lyric-attempt provenance))})]
       (if (:error result) result (get-song sys owner id))))))
(defn decide! [sys owner id {:keys [decision-id manifest-digest choice]}]
  (if-not (raw-song sys owner id) {:error :not-found}
    (if-not (and (text? decision-id 128) (text? manifest-digest 128)
                 (#{:approved :rejected} choice))
      {:error :incorrect}
      (let [result (issue! sys owner :decide {:generation-id id :decision-id decision-id
                                               :manifest-digest manifest-digest :choice choice})]
        (if (:error result) result (get-song sys owner id))))))
(defn render! [sys owner id]
  (if-let [row (raw-song sys owner id)]
    (let [result (issue! sys owner :render {:generation-id id
                                            :manifest-digest (:manifest-digest row)
                                            :render-task-id (stable-id (str "song-render:" id))})]
      (if (:error result) result
        (do (submit-pending! sys owner id :render) (get-song sys owner id))))
    {:error :not-found}))
(defn render-result! [sys owner id task-id artifact-id]
  (if-not (raw-song sys owner id) {:error :not-found}
    (let [result (issue! sys owner :stored {:generation-id id :render-task-id task-id
                                             :artifact-id (when (text? artifact-id 512) artifact-id)})]
      (if (:error result) result (get-song sys owner id)))))
(defn cancel! [sys owner id]
  (if-not (raw-song sys owner id) {:error :not-found}
    (let [result (issue! sys owner :cancel {:generation-id id})]
      (if (:error result) result
        (do (when-let [abort (:abort-task! sys)]
              (let [row (raw-song sys owner id)]
                (abort {:owner owner :task-id (or (:render-task-id row) (:lyrics-task-id row))})))
            (get-song sys owner id))))))
(defn audio [sys owner id]
  (let [row (raw-song sys owner id)]
    (if-not row {:error :not-found}
      (if (and (= :complete (:status row)) (:artifact-id row) (:get-audio! sys))
        ((:get-audio! sys) {:owner owner :artifact-id (:artifact-id row)})
        {:error :not-ready}))))
