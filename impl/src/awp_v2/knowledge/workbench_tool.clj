(ns awp-v2.knowledge.workbench-tool
  (:gen-class)
  (:require [awp-v2.knowledge.api :as knowledge]
            [clojure.data.json :as json]
            [clojure.java.io :as io]))

(def enum-keys #{:branch :split :decision :disposition :reviewer-role})
(defn- enum-data [data]
  (into {} (map (fn [[key value]]
                  [key (if (and (enum-keys key) (string? value)) (keyword value) value)]) data)))
(defn execute [system request]
  (let [owner (:owner request)
        actor (:actor request)]
    (if (= "view" (:mode request))
      (if (= owner actor)
        (knowledge/workbench-view system owner)
        {:error :not-found})
      (let [operations (:operations request)]
        (if (and (vector? operations) (seq operations) (<= (count operations) 500))
          (loop [remaining operations results []]
            (if-let [operation (first remaining)]
              (let [result (knowledge/workbench-command! system actor owner
                             (keyword (:op operation)) (enum-data (:data operation)))]
                (if (:error result)
                  {:error (:error result) :completed (count results)}
                  (recur (rest remaining) (conj results result))))
              {:results results}))
          {:error :incorrect})))))

(defn -main [& [storage-dir reviewer-file]]
  (try
    (let [request (json/read-str (slurp *in*) :key-fn keyword)
          reviewers (if reviewer-file
                      (set (:reviewers (json/read-str (slurp (io/file reviewer-file)) :key-fn keyword)))
                      #{})
          owner (:owner request)
          system (knowledge/open-system
                   {:storage-dir storage-dir
                    :workbench-review-authorized?
                    (fn [actor case-owner]
                      (and (= owner case-owner) (contains? reviewers actor)))})]
      (try
        (println (str "AWP_WORKBENCH_RESULT:"
                      (json/write-str (execute system request))))
        (finally (knowledge/close-system! system))))
    (catch Throwable _
      (println "AWP_WORKBENCH_RESULT:{\"error\":\"failed\"}"))))
