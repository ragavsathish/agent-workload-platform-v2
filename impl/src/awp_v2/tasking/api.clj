(ns awp-v2.tasking.api
  (:require [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-model-validator.interface :as validator]
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp]
            [awp-v2.tasking.commands]
            [awp-v2.tasking.artifacts :as artifacts]
            [awp-v2.tasking.event-model]
            [awp-v2.tasking.lifecycle :as lifecycle]
            [awp-v2.tasking.queries]
            [awp-v2.tasking.read-models :as read-models]
            [clojure.java.io :as io]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.time OffsetDateTime]))

(defn open-system [{:keys [eligible-workspaces storage-dir tenant-id workspace-roots task-workspace-dir]}]
  (validator/verify-or-throw!)
  (let [owned? (nil? storage-dir)
        dir (if storage-dir
              (io/file storage-dir)
              (.toFile (Files/createTempDirectory "awp-v2-tasking-"
                                                  (make-array java.nio.file.attribute.FileAttribute 0))))
        _ (.mkdirs dir)
        store (es/start {:conn {:type :sqlite
                                :database-file (str (io/file dir "events.sqlite"))}})
        cache (kv/start (lmdb/->KV-Store-LMDB {:storage-dir (.getPath dir) :db-name "tasking"}))]
    {:event-store store :cache cache :cache-dir dir :owned-dir? owned?
     :artifact-dir (io/file dir "artifacts")
     :task-workspace-dir (or task-workspace-dir (io/file dir "workspaces"))
     :closed? (atom false)
     :tenant-id (or tenant-id
                    (java.util.UUID/nameUUIDFromBytes
                      (.getBytes (.getCanonicalPath dir) "UTF-8")))
     :eligible-workspaces eligible-workspaces
     :workspace-roots workspace-roots}))

(defn close-system! [{:keys [event-store cache cache-dir owned-dir? closed?]}]
  (when (compare-and-set! closed? false true)
    (kv/stop cache)
    (es/stop event-store)
    (rmp/l1-clear!)
    (when owned-dir?
      (doseq [f (reverse (file-seq cache-dir))]
        (io/delete-file f true)))))

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

(defn all-tasks [system]
  (mapv (fn [[_ accepted]]
          (lifecycle/task-state system (:task-id accepted)))
        (read-models/requests system)))

(defn accepted-count [system]
  (count (read-models/requests system)))

(defn dispatch!
  ([system task-id workspace] (dispatch! system task-id workspace nil))
  ([system task-id workspace source-revision]
   (lifecycle/issue! system :tasking/dispatch-task
                     (cond-> {:task-id task-id :workspace workspace}
                       source-revision (assoc :source-revision source-revision)))))

(defn report-progress! [system task-id summary]
  (lifecycle/issue! system :tasking/record-progress {:task-id task-id :summary summary}))

(defn complete! [system task-id result]
  (lifecycle/issue! system :tasking/complete-task {:task-id task-id :result result}))

(declare fail!)
(defn capture-complete! [system task-id {:keys [text spoken presentation resourceUri]}]
  (let [task (lifecycle/task-state system task-id)]
    (if (not= :running (:status task))
      {::anom/category ::anom/conflict ::anom/message "task is not running"}
      (try
        (let [captured (artifacts/capture! system task-id (:workspace task) (:source-revision task))
              answer (subs (or text "Task completed.") 0 (min 12000 (count (or text "Task completed."))))
              response {:text answer
                        :spoken (subs (or spoken answer) 0 (min 2000 (count (or spoken answer))))
                        :presentation (or presentation "text")
                        :resourceUri resourceUri}]
          (complete! system task-id {:response response :artifacts captured}))
        (catch Exception _
          (fail! system task-id "Task artifact capture failed."))))))

(defn artifact [system actor-id task-id kind]
  (let [task (task system actor-id task-id)
        expected (if (= "scratch" (:workspace task)) "workspace" "patch")
        descriptor (get-in task [:result :artifacts kind])]
    (if (or (::anom/category task)
            (not= :completed (:status task))
            (not= expected kind)
            (nil? descriptor))
      {::anom/category ::anom/not-found ::anom/message "task artifact is unavailable"}
      (try
        (artifacts/read! system task-id kind descriptor)
        (catch Exception _
          {::anom/category ::anom/not-found ::anom/message "task artifact is unavailable"})))))

(defn fail! [system task-id failure]
  (lifecycle/issue! system :tasking/fail-task {:task-id task-id :failure failure}))

(defn interrupt! [system task-id]
  (lifecycle/issue! system :tasking/interrupt-task {:task-id task-id}))

(defn abort! [system actor-id task-id]
  (lifecycle/issue! (context system actor-id) :tasking/abort-task {:task-id task-id}))

(defn retry! [system actor-id task-id]
  (lifecycle/issue! (context system actor-id) :tasking/retry-task {:task-id task-id}))

(defn progress [system actor-id task-id after limit]
  (lifecycle/progress-query (context system actor-id) task-id after limit))
