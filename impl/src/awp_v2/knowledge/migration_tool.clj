(ns awp-v2.knowledge.migration-tool
  (:require [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.migration :as migration]
            [awp-v2.knowledge.qualification :as qualification]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.security MessageDigest]))

(defn- owner-digest [owner]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes owner "UTF-8"))))

(defn- package [path]
  (with-open [reader (io/reader path)]
    (json/read reader :key-fn keyword)))
(defn -main [& [operation storage-dir package-file quality-report-file]]
  (when-not (and (#{"preview" "apply" "reconcile" "select" "rollback"} operation)
                 storage-dir package-file)
    (throw (ex-info "usage: migration-tool <preview|apply|reconcile|select|rollback> <storage-dir> <owner-package.json> [quality-report.json]" {})))
  (let [data (package package-file)
        owner (:owner data)
        _ (when-not (and (string? owner) (seq owner))
            (throw (ex-info "owner package missing owner" {})))
        system (knowledge/open-system {:storage-dir storage-dir})]
    (try
      (let [report (case operation
                     "preview" {:snapshot-id (:snapshot-id data) :source-count (count (:resources data))
                                :quarantine-count (:quarantine-count data) :owner-digest (owner-digest owner)}
                     "apply" (migration/import-into-grain! system owner data)
                     "reconcile" (migration/reconcile-grain system owner data)
                     "rollback" (migration/rollback-grain-import! system owner (:snapshot-id data))
                     "select" (let [match (migration/reconcile-grain system owner data)
                                    quality (when quality-report-file (package quality-report-file))]
                                (if (and (:matched? match)
                                         (zero? (:quarantine-count data))
                                         (qualification/cutover-qualified?
                                           {:quality-gate (keyword (:status quality)) :migration-gate :passed}))
                                  (migration/select-grain-import! system owner data)
                                  {:error :qualification-open :reconciliation match
                                   :quarantine-count (:quarantine-count data)})))]
        (println (json/write-str report))
        (when (:error report) (System/exit 1)))
      (finally (knowledge/close-system! system)))))
