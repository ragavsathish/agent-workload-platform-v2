(ns awp-v2.deployment.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.deployment.commands]
            [awp-v2.deployment.event-model]
            [awp-v2.deployment.queries]
            [awp-v2.deployment.schemas]
            [awp-v2.deployment.read-models :as models]
            [clojure.java.io :as io]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.time OffsetDateTime]))
(defn open-system [{:keys [storage-dir owner] :as options}]
  (validator/verify-or-throw!)
  (let [owned? (nil? storage-dir)
        dir (if storage-dir (io/file storage-dir)
                (.toFile (Files/createTempDirectory "awp-v2-deployment-" (make-array java.nio.file.attribute.FileAttribute 0))))
        _ (.mkdirs dir)
        store (es/start {:conn {:type :sqlite :database-file (str (io/file dir "deployment-events.sqlite"))}})
        cache (kv/start (lmdb/->KV-Store-LMDB {:storage-dir (.getPath dir) :db-name "deployment"}))]
    (assoc options :event-store store :cache cache :cache-dir dir :owned-dir? owned?
           :tenant-id (java.util.UUID/nameUUIDFromBytes (.getBytes (.getCanonicalPath dir) "UTF-8"))
           :owner (or owner "local-owner") :closed? (atom false))))
(defn close-system! [{:keys [event-store cache cache-dir owned-dir? closed?]}]
  (when (compare-and-set! closed? false true)
    (kv/stop cache) (es/stop event-store) (rmp/l1-clear!)
    (when owned-dir? (doseq [file (reverse (file-seq cache-dir))] (io/delete-file file true)))))
(defn- command! [sys op data]
  (let [owner (:owner sys)
        result (cp/process-command
                 (assoc sys :actor-id owner
                        :command {:command/name (keyword "deployment" (name op))
                                   :command/id (random-uuid)
                                   :command/timestamp (OffsetDateTime/now)
                                   :owner owner :data (dissoc data :command-id)}))]
    (or (:command/result result)
        {:error (keyword (or (::anom/message result) "incorrect"))})))
(defn select! [sys selection-id level]
  (if-not (and (string? selection-id) (<= 1 (count selection-id) 128)
               (contains? #{"voice" "durable" "full"} level))
    {:error :invalid-level}
    (command! sys :select {:selection-id selection-id :level level})))
(defn- service-key [key]
  (cond
    (string? key) key
    (keyword? key) (name key)
    :else nil))
(defn- healthy-service? [services service]
  (let [matches (keep (fn [[key value]]
                        (when (= service (service-key key)) value))
                      services)]
    ;; Duplicate spellings such as "grain" and :grain make the observation
    ;; ambiguous; fail closed even when one of the values is true.
    (and (= 1 (count matches)) (true? (first matches)))))
(declare status)
(defn observe! [sys active-level services readiness]
  (if-not (and (contains? #{"voice" "durable" "full"} active-level)
               (map? services) (contains? #{"ready" "degraded" "not_ready" "starting"} readiness))
    {:error :invalid-observation}
    (let [requested (:requested-level (status sys))
          active-level-key (keyword active-level)
          required (case active-level
                     "voice" #{"grain" "pi" "broker" "gateway"}
                     "durable" #{"grain" "pi" "broker" "gateway" "scheduler"}
                     "full" #{"grain" "pi" "broker" "gateway" "scheduler"})
          healthy? (every? #(healthy-service? services %) required)
          observed (cond
                     (= "starting" readiness) :starting
                     (= "not_ready" readiness) :not_ready
                     healthy? :ready
                     :else :degraded)]
      (if (and requested (not= requested active-level-key))
        {:error :level-mismatch}
        (command! sys :observe {:active-level active-level-key :services services
                                :readiness observed :reported-readiness readiness})))))
(defn status [sys]
  (let [owner (:owner sys)
        result (qp/process-query (assoc sys :actor-id owner
          :query {:query/name :deployment/status :query/id (random-uuid)
                  :query/timestamp (OffsetDateTime/now) :owner owner}))]
    (or (:query/result result) {:error :not-found})))
