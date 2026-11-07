(ns awp-v2.tasking.controlled-worker
  (:require [awp-v2.tasking.api :as tasking]
            [awp-v2.tasking.lifecycle :as lifecycle]
            [clojure.java.io :as io]
            [cognitect.anomalies :as anom]))

(defn- forbidden []
  {::anom/category ::anom/forbidden ::anom/message "workspace capability denied"})

(defn start! [system task-id workspace]
  (let [result (tasking/dispatch! system task-id workspace)]
    (if (= :running (:status result))
      {:system system :task-id task-id :workspace workspace :status :running}
      result)))

(defn write! [{:keys [system task-id workspace]} requested path content]
  (let [root (get (:workspace-roots system) workspace)]
    (if (or (not= requested workspace) (nil? root)
            (not (string? path)) (.isAbsolute (io/file path)))
      (forbidden)
      (let [base (.getCanonicalFile (io/file root))
            target (.getCanonicalFile (io/file base path))
            prefix (str (.getPath base) java.io.File/separator)]
        (if (or (not (.startsWith (.getPath target) prefix))
                (not= :running (:status (lifecycle/task-state system task-id))))
          (forbidden)
          (do (.mkdirs (.getParentFile target))
              (spit target content)
              {:status :written}))))))

(defn progress! [{:keys [system task-id]} summary]
  (tasking/report-progress! system task-id summary))
(defn complete! [{:keys [system task-id]} result]
  (tasking/complete! system task-id result))
(defn fail! [{:keys [system task-id]} failure]
  (tasking/fail! system task-id failure))
(defn interrupt! [{:keys [system task-id]}]
  (tasking/interrupt! system task-id))
