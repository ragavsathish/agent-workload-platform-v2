(ns awp-v2.incident.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.incident.commands]
            [awp-v2.incident.event-model]
            [awp-v2.incident.queries]
            [awp-v2.incident.read-models :as models]
            [awp-v2.incident.schemas]
            [awp-v2.tasking.api :as tasking]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.security MessageDigest]
           [java.time OffsetDateTime]
           [java.util UUID]))

(def ^:private safe-label #"^[A-Za-z0-9_.:/-]{1,100}$")
(def ^:private allowed-labels #{"service_name" "app_operation" "job" "instance"
                                "owner_command" "owner_failure_kind"})
(def outage-rule {"awp_grafana_api_unavailable"
                  {:title "Grafana alert API unavailable"
                   :promql "up"
                   :summary "The alert service could not be read for three consecutive polls."}})
(defn- stable-uuid [value] (UUID/nameUUIDFromBytes (.getBytes (str value) "UTF-8")))
(defn- digest [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str value) "UTF-8"))))
(defn- field [m k]
  (or (get m k) (get m (name k)) (get m (keyword (name k)))))
(defn- safe-sample [item]
  (let [raw (second (field item :value))
        number (try (Double/parseDouble (str raw)) (catch Throwable _ ##NaN))]
    (when (Double/isFinite number)
      {:labels (into {} (for [[k v] (field item :metric)
                              :let [name (name k)]
                              :when (and (contains? allowed-labels name)
                                         (string? v) (re-matches safe-label v))]
                          [name v]))
       :value (/ (Math/round (* number 1000.0)) 1000.0)})))
(defn bounded-evidence [results]
  (let [queries (into {} (for [[name response] results]
                           [name (if (= "success" (str (field response :status)))
                                   (->> (field (field response :data) :result)
                                        (take 12) (keep safe-sample) vec)
                                   {:unavailable "MetricSourceUnavailable"})]))
        snapshot {:queries queries}
        rendered (json/write-str snapshot)]
    (if (<= (count rendered) 4000)
      snapshot
      {:queries (into {} (for [[name value] queries]
                           [name (if (vector? value) (vec (take 2 value)) value)]))
       :truncated true})))
(defn- evidence-for [sys rule-id rule]
  (let [fetch! (:metric-fetch! sys)
        diagnostics (get (:diagnostic-queries sys) rule-id {})
        queries (merge {"alert_value" (:promql rule)} diagnostics)
        responses (into {} (for [[name expression] queries]
                             [name (try
                                     (if fetch! (fetch! expression)
                                         {:status "unavailable"})
                                     (catch Throwable _ {:status "unavailable"}))]))]
    (bounded-evidence responses)))
(defn- instruction-for [rule-id rule evidence]
  (str "Investigate the firing stack health alert " (:title rule) " (" rule-id "). "
       "Meaning: " (:summary rule) " Host monitor PromQL: " (:promql rule) " "
       "Host-collected, payload-free evidence: " (json/write-str evidence) " "
       "Use supplied evidence to report observed facts, probable cause, impact, uncertainties, "
       "and a recommended next action. Do not claim checks you could not run. "
       "Do not change files, restart services, or perform remediation."))
(defn- managed-alert [rules alert]
  (let [labels (field alert :labels)
        uid (field labels :rule_uid)
        uid (if (contains? rules uid) uid
                (let [matches (for [[id rule] rules :when (= (:title rule) (field labels :alertname))] id)]
                  (when (= 1 (count matches)) (first matches))))
        state (some-> (field alert :state) str str/lower-case)
        fingerprint (field alert :fingerprint)]
    (when (and (map? labels) uid
               (= "true" (field labels :auto_investigate))
               (= "agent-workload-platform" (field labels :managed_by))
               (#{"firing" "alerting"} state))
      {:rule-id uid
       :fingerprint (if (and (string? fingerprint) (<= 1 (count fingerprint) 256))
                      fingerprint
                      (digest (pr-str (sort-by (comp str key) labels))))})))
(defn open-system [{:keys [storage-dir] :as options}]
  (validator/verify-or-throw!)
  (let [owned? (nil? storage-dir)
        dir (if storage-dir (io/file storage-dir)
                (.toFile (Files/createTempDirectory "awp-v2-incident-"
                                                  (make-array java.nio.file.attribute.FileAttribute 0))))
        _ (.mkdirs dir)
        store (es/start {:conn {:type :sqlite :database-file (str (io/file dir "incident-events.sqlite"))}})
        cache (kv/start (lmdb/->KV-Store-LMDB {:storage-dir (.getPath dir) :db-name "incident"}))]
    (assoc options :event-store store :cache cache :cache-dir dir :owned-dir? owned?
           :closed? (atom false) :tenant-id (stable-uuid (.getCanonicalPath dir))
           :rules (merge (:rules options) outage-rule))))
(defn close-system! [{:keys [event-store cache cache-dir owned-dir? closed?]}]
  (when (compare-and-set! closed? false true)
    (kv/stop cache) (es/stop event-store) (rmp/l1-clear!)
    (when owned-dir? (doseq [file (reverse (file-seq cache-dir))] (io/delete-file file true)))))
(defn- response [result kind]
  (if-let [value (get result kind)] value
    {:error (keyword (or (::anom/message result) "incorrect"))}))
(defn- issue! [sys owner op data]
  (loop [attempt 0]
    (let [result (cp/process-command
                   (assoc sys :actor-id owner
                          :command {:command/name (keyword "incident" (name op))
                                    :command/id (random-uuid)
                                    :command/timestamp (OffsetDateTime/now)
                                    :owner owner :data data}))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result)) (< attempt 12))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (response result :command/result)))))
(defn- raw-owner [sys owner] (models/owner-state sys owner))
(defn- submit-pending! [sys owner epoch]
  (when (= :pending (:submission-status epoch))
    (let [request {:request-id (:request-id epoch) :owner owner
                   :instruction (:instruction epoch) :workspace (:workspace sys)}]
      (try
        (let [accepted (if-let [submit (:submit-task! sys)]
                         (submit request)
                         (tasking/submit! (:task-system sys) owner request))]
          (when (= (:request-id epoch) (:task-id accepted))
            (issue! sys owner :submitted {:epoch-id (:epoch-id epoch)
                                          :task-id (:task-id accepted) :at (:first-seen epoch)})))
        (catch Throwable _ nil)))))
(defn recover! [sys]
  (doseq [[owner state] (models/owners sys)
          [_ epoch] (:epochs state)
          :when (= :pending (:submission-status epoch))]
    (submit-pending! sys owner epoch))
  true)
(defn- observe-one! [sys owner at rule-id fingerprint]
  (let [key (str rule-id ":" fingerprint)
        state (raw-owner sys owner)
        latest (get-in state [:epochs (get-in state [:latest key])])
        new? (or (nil? latest)
                 (and (not (:active? latest)) (:last-submitted latest)
                      (>= (- at (:last-submitted latest)) 1800)))
        rule (get (:rules sys) rule-id)
        data (if new?
               (let [epoch-id (str (stable-uuid (str owner ":" key ":" at)))
                     request-id (stable-uuid (str "awp-stack-alert:" owner ":" key ":" at))
                     instruction (instruction-for rule-id rule (evidence-for sys rule-id rule))]
                 {:epoch-id epoch-id :owner owner :key key :rule-id rule-id :at at
                  :fingerprint fingerprint :first-seen at :last-seen at
                  :request-id request-id :instruction instruction
                  :instruction-digest (digest instruction) :evidence-status
                  (if (str/includes? instruction "MetricSourceUnavailable") :partial :captured)})
               {:key key :epoch-id (:epoch-id latest) :at at})
        result (issue! sys owner :observe data)
        epoch (get-in (raw-owner sys owner) [:epochs (:epoch-id result)])]
    (when epoch (submit-pending! sys owner epoch))
    result))
(declare incidents)
(defn observe! [sys owner at alerts]
  (if-not (and (string? owner) (integer? at) (sequential? alerts))
    {:error :incorrect}
    (do
      (issue! sys owner :success {:at at})
      (let [eligible (->> alerts (keep #(managed-alert (:rules sys) %)) distinct vec)
            active-keys (set (map (fn [{:keys [rule-id fingerprint]}]
                                    (str rule-id ":" fingerprint)) eligible))]
        (doseq [{:keys [rule-id fingerprint]} eligible]
          (observe-one! sys owner at rule-id fingerprint))
        (doseq [[_ epoch] (:epochs (raw-owner sys owner))
                :when (and (:active? epoch) (not (contains? active-keys (:key epoch))))]
          (issue! sys owner :resolve {:epoch-id (:epoch-id epoch)}))
        (incidents sys owner)))))
(defn unavailable! [sys owner at]
  (let [result (issue! sys owner :failure {:at at})]
    (when (>= (:failures result 0) 3)
      (observe-one! sys owner at "awp_grafana_api_unavailable"
                    (digest "grafana-alert-api-unavailable")))
    result))
(defn- read! [sys owner op data]
  (response (qp/process-query
              (assoc sys :actor-id owner
                     :query (merge {:query/name (keyword "incident" (name op))
                                    :query/id (random-uuid)
                                    :query/timestamp (OffsetDateTime/now)
                                    :owner owner} data))) :query/result))
(defn- present [sys row]
  (let [owner (:owner row)
        task (when-let [request-id (:request-id row)]
               (when (= :accepted (:submission-status row))
                 (tasking/task (:task-system sys) owner request-id)))
        status (:status task)
        finding-status (case status
                         :completed (if (some? (:result task)) :finding :unjudged)
                         :failed :failed :cancelled :failed :unjudged)]
    (-> row
        (dissoc :instruction)
        (assoc :observation-freshness (get-in (raw-owner sys owner) [:freshness] :unknown)
               :task-status (or status :unjudged)
               :finding-status finding-status
               :finding (when (= :finding finding-status) (:result task))
               :failure (when (= :failed finding-status) (:failure task))))))
(defn incident [sys owner epoch-id]
  (let [row (read! sys owner :get {:epoch-id epoch-id})]
    (if (:error row) row (present sys row))))
(defn incidents [sys owner]
  (let [rows (read! sys owner :list {})]
    (if (:error rows) rows
        (->> rows (sort-by (juxt :first-seen :epoch-id) #(compare %2 %1))
             (mapv #(present sys %))))))
