(ns awp-v2.tasking.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.tasking.commands]
            [awp-v2.tasking.event-model]
            [awp-v2.tasking.queries]
            [awp-v2.tasking.read-models :as read-models]
            [clojure.java.io :as io]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.time OffsetDateTime]))

(defn open-system [{:keys [eligible-workspaces]}]
  (validator/verify-or-throw!)
  (let [dir (.toFile (Files/createTempDirectory "awp-v2-tasking-" (make-array java.nio.file.attribute.FileAttribute 0)))
        store (es/start {:conn {:type :in-memory}})
        cache (kv/start (lmdb/->KV-Store-LMDB {:storage-dir (.getPath dir) :db-name "tasking"}))]
    {:event-store store :cache cache :cache-dir dir
     :tenant-id (random-uuid)
     :eligible-workspaces eligible-workspaces}))

(defn close-system! [{:keys [event-store cache cache-dir]}]
  (kv/stop cache)
  (es/stop event-store)
  (doseq [f (reverse (file-seq cache-dir))]
    (io/delete-file f true))
  (rmp/l1-clear!))

(defn- context [system actor-id]
  (assoc system :actor-id actor-id))

(defn submit! [system actor-id request]
  (loop [attempt 0]
    (let [command (assoc request
                         :command/name :tasking/submit-task
                         :command/id (random-uuid)
                         :command/timestamp (OffsetDateTime/now))
          result (cp/process-command
                   (assoc (context system actor-id) :command command))]
      (if (and (= ::anom/conflict (::anom/category result))
               (= "CAS failed" (::anom/message result))
               (< attempt 4))
        (do (rmp/l1-clear!) (recur (inc attempt)))
        (if-let [value (:command/result result)] value result)))))

(defn task [system actor-id request-id]
  (let [result (qp/process-query
                 (assoc (context system actor-id)
                        :query {:query/name :tasking/task
                                :query/id (random-uuid)
                                :query/timestamp (OffsetDateTime/now)
                                :request-id request-id}))]
    (or (:query/result result) result)))

(defn accepted-count [system]
  (count (read-models/requests system)))
