(ns awp-v2.knowledge.probe
  (:require [awp-v2.knowledge.api :as knowledge]
            [clojure.java.io :as io]))

(def owner "probe-owner@example.test")
(def turn {:source-id "probe-conversation:probe-message" :conversation-id "probe-conversation"
           :message-id "probe-message" :transcript "I learned a new song."
           :captured-at "2026-11-08T10:00:00Z" :status :completed :origin :owner})
(def source {:source-ref "probe-conversation:probe-message" :speaker :owner
             :captured-at "2026-11-08T10:00:00Z" :transcript "I learned a new song."})
(defn -main [mode dir]
  (let [submitted (atom [])
        system (knowledge/open-system {:storage-dir dir
                                       :submit-task! (fn [request]
                                                       (swap! submitted conj request)
                                                       {:task-id (:request-id request)})})]
    (try
      (case mode
        "create"
        (do
          (assert (= :accepted (:status (knowledge/ingest-turn! system owner turn))))
          (assert (= 2 (count @submitted)))
          (let [task-id (:task-id (first (filter #(= :journal (:mode %))
                                                 (knowledge/tasks system owner (:source-id turn)))))
                outcome (knowledge/outcome! system owner (:source-id turn) :journal :completed
                                            {:task-id task-id
                                             :suggestions [{:item-id "probe-entry" :kind :journal
                                                            :text "Learned a song" :evidence "learned"}]})]
            (assert (= :completed (:status outcome))))
          (assert (= :corrected (:status (knowledge/review! system owner "probe-entry"
                                                          {:action-id "probe-review" :expected-revision 0
                                                           :decision :corrected :text "I learned a song"}))))
          (let [requested (knowledge/daily-request! system owner {:journal-date "2026-11-08"
                                                                    :time-zone "Europe/Helsinki"
                                                                    :sources [source]})]
            (spit (io/file dir "account-id") (:account-id requested))
            (assert (= :completed (:status (knowledge/daily-result! system owner (:account-id requested)
                                                              {:title "A day" :story "I learned a song."
                                                               :sources [source]}))))))
        "verify"
        (do
          (assert (= "I learned a song" (:current-text (knowledge/item system owner "probe-entry"))))
          (assert (= 1 (count (:reviews (knowledge/item system owner "probe-entry")))))
          (assert (= :completed (:status (knowledge/account system owner (slurp (io/file dir "account-id"))))))
          (assert (= :not-found (:error (knowledge/item system "foreign@example.test" "probe-entry"))))
          (assert (empty? @submitted))
          (println "public knowledge restart probe OK"))
        (throw (ex-info "unknown probe mode" {:mode mode})))
      (finally (knowledge/close-system! system)))))
