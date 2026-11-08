(ns awp-v2.knowledge.quality-ledger-gate
  (:gen-class)
  (:require [clojure.data.json :as json]
            [awp-v2.knowledge.api :as knowledge]))

(defn verify-one [sys request]
  (let [owner (get request "owner")
        run-id (get request "runId")
        run (knowledge/quality-run sys owner run-id)
        cases (into {} (map (juxt :case-id identity) (knowledge/quality-cases sys owner)))
        selected (get request "cases")
        expected-ids (set (map #(get % "id") selected))
        ledger-ids (set (:case-ids run))
        complete? (and (= :prepared (:status run))
                       (= expected-ids ledger-ids)
                       (= (get request "partition") (name (:split run)))
                       (= (get request "policyDigest") (:policy-digest run))
                       (= (get request "corpusDigest") (:corpus-digest run))
                       (= (get request "configDigest") (:config-digest run))
                       (= (get request "baselineId") (:baseline-id run))
                       (= (get request "candidateId") (:candidate-id run))
                       (= (get request "rollbackId") (:rollback-id run))
                       (every? (fn [item]
                                 (let [record (get cases (get item "id"))]
                                   (and record
                                        (#{:approved :adjudicated} (:review-status record))
                                        (= :passed (:privacy-status record))
                                        (= (get item "sourceDigest") (:source-digest record))
                                        (= (get item "expectedDigest") (:expected-digest record))
                                        (= (get request "partition") (name (:split record)))
                                        (seq (:review-history record))))) selected))]
    (if-not complete?
      {:status "open" :reason "grain-review-ledger-unverified"}
      (if (= "locked_holdout" (get request "partition"))
        (if (= :consumed (:status (knowledge/quality-consume-holdout! sys owner run-id)))
          {:status "ready" :holdout "consumed"}
          {:status "open" :reason "holdout-already-consumed"})
        {:status "ready" :holdout "not-applicable"}))))

(defn -main [& _]
  (try
    (let [dir (System/getenv "AWP_QUALITY_GRAIN_STORAGE_DIR")
          request (json/read-str (slurp *in*))]
      (when-not (and (string? dir) (not (empty? dir)))
        (throw (ex-info "missing private store" {})))
      (let [sys (knowledge/open-system {:storage-dir dir})]
        (try
          (println (str "AWP_LEDGER_RESULT:" (json/write-str (verify-one sys request))))
          (finally (knowledge/close-system! sys)))))
    (catch Throwable _
      (println "AWP_LEDGER_RESULT:{\"status\":\"open\",\"reason\":\"grain-review-ledger-unverified\"}"))))
