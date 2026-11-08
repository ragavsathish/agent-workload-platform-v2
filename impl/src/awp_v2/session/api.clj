(ns awp-v2.session.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.session.commands]
            [awp-v2.session.event-model]
            [awp-v2.session.queries]
            [awp-v2.session.read-models :as models]
            [awp-v2.session.schemas]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.security MessageDigest]
           [java.time OffsetDateTime]))

(declare issue!)

(defn open-system [{:keys [storage-dir] :as options}]
  (validator/verify-or-throw!)
  (let [owned? (nil? storage-dir)
        dir (if storage-dir
              (io/file storage-dir)
              (.toFile (Files/createTempDirectory "awp-v2-session-"
                                                  (make-array java.nio.file.attribute.FileAttribute 0))))
        _ (.mkdirs dir)
        store (es/start {:conn {:type :sqlite
                                :database-file (str (io/file dir "session-events.sqlite"))}})
        cache (kv/start (lmdb/->KV-Store-LMDB
                          {:storage-dir (.getPath dir) :db-name "session"}))
        system (assoc options
           :event-store store :cache cache :cache-dir dir :owned-dir? owned?
           :closed? (atom false)
           :tenant-id (java.util.UUID/nameUUIDFromBytes
                        (.getBytes (.getCanonicalPath dir) "UTF-8")))]
    ;; A process restart cannot recover a live task socket or an in-flight
    ;; external effect. Record their safe recovery state before admitting turns.
    (doseq [[owner state] (models/owners system)]
      (doseq [[turn-id turn] (:turns state)
              :when (= :accepted (:status turn))]
        (issue! system owner :settle {:turn-id turn-id :status :uncertain
                                      :at (str (OffsetDateTime/now))}))
      (when (:attachment state)
        (issue! system owner :detach {:at (str (OffsetDateTime/now))})))
    system))

(defn close-system! [{:keys [event-store cache cache-dir owned-dir? closed?]}]
  (when (compare-and-set! closed? false true)
    (kv/stop cache)
    (es/stop event-store)
    (rmp/l1-clear!)
    (when owned-dir?
      (doseq [f (reverse (file-seq cache-dir))] (io/delete-file f true)))))

(defn- response [result kind]
  (if-let [value (get result kind)]
    value
    (if-let [category (::anom/category result)]
      {:error (if (= "busy" (::anom/message result))
                :busy (keyword (name category)))}
      {:error :incorrect})))

(defn- issue! [system actor op data]
  (loop [attempt 0]
    (let [command {:command/name (keyword "session" (name op))
                   :command/id (random-uuid)
                   :command/timestamp (OffsetDateTime/now)
                   :owner actor :data data}
          result (cp/process-command (assoc system :actor-id actor :command command))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result))
               (< attempt 8))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (response result :command/result)))))

(defn- read! [system actor op data]
  (let [query (merge {:query/name (keyword "session" (name op))
                      :query/id (random-uuid)
                      :query/timestamp (OffsetDateTime/now)
                      :owner actor} data)]
    (response (qp/process-query (assoc system :actor-id actor :query query))
              :query/result)))

(defn- now [] (str (OffsetDateTime/now)))

(defn- valid-command-id? [value]
  (and (string? value) (<= 1 (count value) 128)))

(defn- valid-conversation-id? [value]
  (and (string? value) (boolean (re-matches #"[A-Za-z0-9_-]{1,128}" value))))

(defn fresh! [system actor command-id]
  (if (valid-command-id? command-id)
    (issue! system actor :fresh {:command-id command-id
                                 :conversation-id (str (random-uuid)) :at (now)})
    {:error :incorrect}))

(defn resume! [system actor command-id conversation-id]
  (if (and (valid-command-id? command-id)
           (valid-conversation-id? conversation-id))
    (issue! system actor :resume {:command-id command-id
                                  :conversation-id conversation-id :at (now)})
    {:error :incorrect}))

(defn selection [system actor] (read! system actor :selection {}))
(defn history [system actor conversation-id]
  (if (valid-conversation-id? conversation-id)
    (read! system actor :history {:conversation-id conversation-id})
    {:error :incorrect}))
(defn transcript [system actor] (read! system actor :transcript {}))
(defn catalog [system actor] (read! system actor :catalog {}))
(defn context [system actor] (read! system actor :context {}))
(defn routing [system actor] (read! system actor :routing {}))
(defn routing-command [system actor command-id]
  (if (valid-command-id? command-id)
    (read! system actor :routing-command {:command-id command-id})
    {:error :incorrect}))
(defn turn [system actor turn-id] (read! system actor :turn {:turn-id turn-id}))

(defn- parsed-turn [messages origin]
  (when (and (vector? messages) (<= 1 (count messages) 64)
             (#{:voice :typed :bootstrap} origin))
    (let [valid? (every? (fn [m]
                           (and (map? m)
                                (contains? #{"system" "developer" "user" "assistant"}
                                           (:role m))
                                (string? (:content m)))) messages)
          total (reduce + (map #(count (:content %)) messages))
          system-prompt (str/join "\n\n" (map :content (filter #(= "system" (:role %)) messages)))
          final-message (last (remove #(= "system" (:role %)) messages))]
      (when (and valid? (<= total 64000)
                 (contains? #{"user" "developer"} (:role final-message))
                 (not (str/blank? (:content final-message)))
                 (or (not= origin :typed) (<= (count (:content final-message)) 8000)))
        {:prompt (:content final-message) :system-prompt system-prompt}))))

(defn- fingerprint [origin prompt system-prompt]
  (let [bytes (.digest (MessageDigest/getInstance "SHA-256")
                       (.getBytes (pr-str [origin prompt system-prompt]) "UTF-8"))]
    (.formatHex (java.util.HexFormat/of) bytes)))

(defn submit! [system actor {:keys [turn-id origin messages]}]
  (if-let [{:keys [prompt system-prompt]}
           (when (valid-command-id? turn-id) (parsed-turn messages origin))]
    (let [fingerprint (fingerprint origin prompt system-prompt)
          decision (issue! system actor :submit
                           {:turn-id turn-id :origin origin :prompt prompt
                            :system-prompt system-prompt :fingerprint fingerprint
                            :at (now)})]
      (when (and (= :accepted (:status decision)) (not (:reused? decision)))
        (try
          (when-let [driver (:turn-driver system)]
            (driver {:turn-id turn-id :owner actor :origin origin
                     :prompt prompt :system-prompt system-prompt
                     :conversation-id (:conversation-id decision)
                     :task-id (:task-id decision)}))
          (catch Throwable _
            (issue! system actor :settle {:turn-id turn-id :status :uncertain :at (now)}))))
      decision)
    {:error :incorrect}))

(defn- bounded [value]
  (when (string? value) (subs value 0 (min 24000 (count value)))))

(defn settle! [system actor turn-id status answer]
  (if (#{:completed :failed :interrupted :uncertain} status)
    (issue! system actor :settle {:turn-id turn-id :status status
                                  :answer (bounded answer) :at (now)})
    {:error :incorrect}))

(defn observe! [system actor turn-id observation]
  (let [safe (case (:kind observation)
               :assistant-delta {:kind :assistant-delta :text (bounded (:text observation))}
               :tool-start {:kind :tool-start
                            :tool-id (bounded (:tool-id observation))
                            :name (let [value (str/replace (str (:name observation))
                                                           #"[^a-zA-Z0-9_.:-]" "")]
                                    (if (seq value) (subs value 0 (min 80 (count value))) "tool"))}
               :tool-end {:kind :tool-end :tool-id (bounded (:tool-id observation))
                          :failed? (boolean (:failed? observation))}
               :context-usage
               (let [tokens (:tokens observation)
                     window (:contextWindow observation)]
                 (when (and (or (nil? tokens) (and (integer? tokens) (<= 0 tokens)))
                            (or (nil? window) (and (integer? window) (pos? window))))
                   {:kind :context-usage :tokens tokens :contextWindow window
                    :estimated (boolean (:estimated observation))
                    :canCompact (boolean (:canCompact observation))}))
               nil)]
    (if safe
      (issue! system actor :observe {:turn-id turn-id :observation safe :at (now)})
      {:error :incorrect})))

(defn stop! [system actor turn-id]
  (let [result (issue! system actor :stop {:turn-id turn-id :at (now)})]
    (when (= :requested (:status result))
      (when-let [abort-driver (:abort-driver system)] (abort-driver turn-id)))
    result))

(defn compact! [system actor command-id & [{:keys [tokens contextWindow]}]]
  (if (valid-command-id? command-id)
    (issue! system actor :compact (cond-> {:command-id command-id :at (now)}
                                (some? tokens) (assoc :tokens tokens
                                                      :contextWindow contextWindow)))
    {:error :incorrect}))
(defn attach! [system actor task-id & [command-id]]
  (if (or (nil? command-id) (valid-command-id? command-id))
    (issue! system actor :attach (cond-> {:task-id task-id :at (now)}
                                   command-id (assoc :command-id command-id)))
    {:error :incorrect}))
(defn detach! [system actor & [command-id]]
  (if (or (nil? command-id) (valid-command-id? command-id))
    (issue! system actor :detach (cond-> {:at (now)}
                                  command-id (assoc :command-id command-id)))
    {:error :incorrect}))
(defn voice-ended! [system actor]
  (issue! system actor :voice-ended {:at (now)}))
