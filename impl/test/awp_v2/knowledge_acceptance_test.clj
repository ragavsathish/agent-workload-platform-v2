(ns awp-v2.knowledge-acceptance-test
  (:require [clojure.test :refer :all]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.session.api :as session]
            [awp-v2.live.http :as live]
            [clojure.data.json :as json])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]))

(defn with-system [f]
  (let [submitted (atom [])
        sys (knowledge/open-system {:submit-task! (fn [request] (swap! submitted conj request)
                                                       {:task-id (:request-id request)})})]
    (try (f sys submitted) (finally (knowledge/close-system! sys)))))

(def owner "owner@example.test")
(def other "other@example.test")
(def turn {:source-id "conversation-1:message-1" :conversation-id "conversation-1"
           :message-id "message-1" :transcript "I learned a new song." :captured-at "2026-11-08T10:00:00Z"
           :status :completed :origin :owner})
(defn report! [sys owner source-id mode status payload]
  (let [task (first (filter #(= mode (:mode %)) (knowledge/tasks sys owner source-id)))]
    (knowledge/outcome! sys owner source-id mode status (assoc payload :task-id (:task-id task)))))

(def source {:source-ref "conversation-1:message-1" :speaker :owner
             :captured-at "2026-11-08T10:00:00Z" :transcript "I learned a new song."})

(deftest knowledge-001-settled-turn-starts-independent-extraction-once
  (with-system
    (fn [sys calls]
      (is (= :accepted (:status (knowledge/ingest-turn! sys owner turn))))
      (is (= :reused (:status (knowledge/ingest-turn! sys owner turn))))
      (is (= #{:journal :classification} (set (map :mode @calls))))
      (is (= 2 (count @calls)))
      (is (= 2 (count (knowledge/tasks sys owner (:source-id turn))))))))

(deftest knowledge-002-incomplete-or-non-owner-turn-is-ignored
  (with-system
    (fn [sys calls]
      (doseq [candidate [(assoc turn :status :accepted)
                         (assoc turn :status :interrupted)
                         (assoc turn :origin :developer)
                         (assoc turn :transcript "")]]
        (is (= :ignored (:status (knowledge/ingest-turn! sys owner candidate)))))
      (is (empty? @calls)))))

(deftest knowledge-003-independent-failure-and-suggestions
  (with-system
    (fn [sys _]
      (knowledge/ingest-turn! sys owner turn)
      (is (= :failed (:status (report! sys owner (:source-id turn) :journal :failed {:reason "model unavailable"}))))
      (is (= :completed (:status (report! sys owner (:source-id turn) :classification :completed
                                                   {:suggestions [{:item-id "candidate-1" :kind :assertion :text "Learns music" :evidence "learned"}]}))))
      (is (= #{:failed :completed} (set (map :status (knowledge/tasks sys owner (:source-id turn))))))
      (is (= (:source-id turn) (:source-id (first (knowledge/suggestions sys owner))))))))

(deftest knowledge-004-correction-preserves-history
  (with-system
    (fn [sys _]
      (knowledge/ingest-turn! sys owner turn)
      (report! sys owner (:source-id turn) :journal :completed
                          {:suggestions [{:item-id "journal-1" :kind :journal :text "I learned song" :evidence "learned"}]})
      (is (= :corrected (:status (knowledge/review! sys owner "journal-1" {:action-id "action-1" :expected-revision 0
                                                                       :decision :corrected :text "I learned a song"}))))
      (let [item (knowledge/item sys owner "journal-1")]
        (is (= "I learned a song" (:current-text item)))
        (is (= "I learned song" (:original-text item)))
        (is (= 1 (count (:reviews item))))
        (is (= (:source-id turn) (:source-id item)))))))

(deftest knowledge-005-profile-facts-require-acceptance
  (with-system
    (fn [sys _]
      (knowledge/ingest-turn! sys owner turn)
      (report! sys owner (:source-id turn) :classification :completed
                          {:suggestions [{:item-id "candidate-1" :kind :assertion :text "Music learner" :evidence "learned"}
                                         {:item-id "candidate-2" :kind :assertion :text "Singer" :evidence "song"}]})
      (is (empty? (knowledge/profile sys owner)))
      (knowledge/review! sys owner "candidate-1" {:action-id "accept-1" :expected-revision 0 :decision :accepted})
      (knowledge/review! sys owner "candidate-2" {:action-id "reject-2" :expected-revision 0 :decision :rejected})
      (is (= ["candidate-1"] (mapv :item-id (knowledge/profile sys owner)))))))

(deftest knowledge-006-review-replay-and-stale-conflict
  (with-system
    (fn [sys _]
      (knowledge/ingest-turn! sys owner turn)
      (report! sys owner (:source-id turn) :journal :completed
                          {:suggestions [{:item-id "journal-1" :kind :journal :text "Learned" :evidence "learned"}]})
      (let [command {:action-id "action-1" :expected-revision 0 :decision :accepted}]
        (is (= :accepted (:status (knowledge/review! sys owner "journal-1" command))))
        (is (= :accepted (:status (knowledge/review! sys owner "journal-1" command))))
        (is (= :conflict (:error (knowledge/review! sys owner "journal-1"
                                                   {:action-id "action-2" :expected-revision 0 :decision :rejected}))))
        (is (= 1 (count (:reviews (knowledge/item sys owner "journal-1")))))))))

(deftest knowledge-007-foreign-read-and-review-refused
  (with-system
    (fn [sys _]
      (knowledge/ingest-turn! sys owner turn)
      (report! sys owner (:source-id turn) :journal :completed
                          {:suggestions [{:item-id "journal-1" :kind :journal :text "Learned" :evidence "learned"}]})
      (is (= :not-found (:error (knowledge/item sys other "journal-1"))))
      (is (empty? (knowledge/suggestions sys other)))
      (is (= :not-found (:error (knowledge/review! sys other "journal-1"
                                                    {:action-id "x" :expected-revision 0 :decision :accepted})))))))

(deftest knowledge-008-explicit-daily-bundle-is-idempotent
  (with-system
    (fn [sys calls]
      (knowledge/ingest-turn! sys owner turn)
      (is (= 2 (count @calls)))
      (let [request {:journal-date "2026-11-08" :time-zone "Europe/Helsinki" :sources [source]}
            first-result (knowledge/daily-request! sys owner request)
            again (knowledge/daily-request! sys owner request)]
        (is (= (:account-id first-result) (:account-id again)))
        (is (= 3 (count @calls)))
        (is (= :completed (:status (knowledge/daily-result! sys owner (:account-id first-result)
                                                          {:title "Day" :story "I learned a new song." :sources [source]}))))
        (is (= [(:source-ref source)] (:source-refs (knowledge/account sys owner (:account-id first-result)))))))))

(deftest knowledge-009-changed-bundle-keeps-prior-version
  (with-system
    (fn [sys _]
      (let [base {:journal-date "2026-11-08" :time-zone "Europe/Helsinki" :sources [source]}
            a (knowledge/daily-request! sys owner base)
            b (knowledge/daily-request! sys owner (assoc base :sources [(assoc source :transcript "Changed source")]))]
        (is (not= (:account-id a) (:account-id b)))
        (is (= 2 (count (knowledge/accounts sys owner))))
        (is (= "I learned a new song." (get-in (knowledge/account sys owner (:account-id a)) [:sources 0 :transcript])))))))

(deftest knowledge-010-invalid-daily-bundle-is-refused
  (with-system
    (fn [sys calls]
      (let [base {:journal-date "2026-11-08" :time-zone "Europe/Helsinki" :sources [source]}]
        (doseq [bad [(assoc base :sources [])
                     (assoc base :sources [source source])
                     (assoc base :sources [(assoc source :captured-at "2026-11-07T10:00:00Z")])
                     (assoc base :sources [(assoc source :transcript (apply str (repeat 200001 "x")))])]]
          (is (= :incorrect (:error (knowledge/daily-request! sys owner bad)))))
        (is (empty? @calls))))))

(deftest knowledge-001-session-boundary-and-submission-recovery
  (let [session-system (session/open-system {})
        attempts (atom [])
        fail-journal? (atom true)
        sys (knowledge/open-system {:submit-task! (fn [request]
                                                     (swap! attempts conj request)
                                                     (when (and (= :journal (:mode request))
                                                                (compare-and-set! fail-journal? true false))
                                                       (throw (ex-info "uncertain gateway response" {})))
                                                     {:task-id (:request-id request)})})]
    (try
      (session/fresh! session-system owner "knowledge-fresh")
      (session/submit! session-system owner {:turn-id "real-turn" :origin :voice
                                             :messages [{:role "user" :content "I learned a new song."}]})
      (is (= :ignored (:status (knowledge/ingest-session-turn! sys session-system owner "real-turn"))))
      (session/settle! session-system owner "real-turn" :completed "Good to hear.")
      (is (= :accepted (:status (knowledge/ingest-session-turn! sys session-system owner "real-turn"))))
      (let [source-id (str (:conversation-id (session/turn session-system owner "real-turn")) ":real-turn")]
        (is (= #{:uncertain :pending} (set (map :status (knowledge/tasks sys owner source-id)))))
        (is (= :reused (:status (knowledge/ingest-session-turn! sys session-system owner "real-turn"))))
        (is (= 3 (count @attempts)))
        (is (= 1 (count (set (map :request-id (filter #(= :journal (:mode %)) @attempts))))))
        (is (every? :task-id (knowledge/tasks sys owner source-id))))
      (finally (knowledge/close-system! sys) (session/close-system! session-system)))))

(deftest knowledge-006-simultaneous-review-has-one-winner
  (with-system
    (fn [sys _]
      (knowledge/ingest-turn! sys owner turn)
      (report! sys owner (:source-id turn) :journal :completed
               {:suggestions [{:item-id "race-item" :kind :journal :text "Learned" :evidence "learned"}]})
      (let [start (promise)
            actions (doall (for [i (range 8)]
                             (future @start
                                     (knowledge/review! sys owner "race-item"
                                                        {:action-id (str "race-" i)
                                                         :expected-revision 0
                                                         :decision (if (even? i) :accepted :rejected)}))))]
        (deliver start true)
        (let [outcomes (mapv deref actions)]
          (is (= 1 (count (remove :error outcomes))))
          (is (= 7 (count (filter #(= :conflict (:error %)) outcomes))))
          (is (= 1 (count (:reviews (knowledge/item sys owner "race-item"))))))))))

(deftest knowledge-009-restart-retains-review-and-source-version
  (let [dir (.toFile (java.nio.file.Files/createTempDirectory "knowledge-restart-"
                                                      (make-array java.nio.file.attribute.FileAttribute 0)))
        submit (fn [request] {:task-id (:request-id request)})]
    (try
      (let [sys (knowledge/open-system {:storage-dir (.getPath dir) :submit-task! submit})]
        (knowledge/ingest-turn! sys owner turn)
        (report! sys owner (:source-id turn) :journal :completed
                 {:suggestions [{:item-id "persistent-item" :kind :journal :text "Learned" :evidence "learned"}]})
        (knowledge/review! sys owner "persistent-item" {:action-id "persistent-action"
                                                           :expected-revision 0 :decision :accepted})
        (knowledge/daily-request! sys owner {:journal-date "2026-11-08"
                                              :time-zone "Europe/Helsinki" :sources [source]})
        (knowledge/close-system! sys))
      (let [sys (knowledge/open-system {:storage-dir (.getPath dir) :submit-task! submit})]
        (try
          (is (= :accepted (:status (knowledge/item sys owner "persistent-item"))))
          (is (= 1 (count (:reviews (knowledge/item sys owner "persistent-item")))))
          (is (= [(:source-ref source)] (:source-refs (first (knowledge/accounts sys owner)))))
          (finally (knowledge/close-system! sys))))
      (finally (doseq [f (reverse (file-seq dir))] (clojure.java.io/delete-file f true))))))

(deftest knowledge-009-writer-must-match-source-bundle
  (with-system
    (fn [sys _]
      (let [request (knowledge/daily-request! sys owner {:journal-date "2026-11-08"
                                                          :time-zone "Europe/Helsinki" :sources [source]})]
        (is (= :conflict (:error (knowledge/daily-result! sys owner (:account-id request)
                                                          {:title "Day" :story "Wrong source."
                                                           :sources [(assoc source :transcript "Wrong source")]}))))
        (is (= :pending (:status (knowledge/account sys owner (:account-id request)))))))))

(deftest knowledge-001-restart-reconciles-uncertain-submission
  (let [dir (.toFile (java.nio.file.Files/createTempDirectory "knowledge-reconcile-"
                                                      (make-array java.nio.file.attribute.FileAttribute 0)))
        attempts (atom [])]
    (try
      (let [sys (knowledge/open-system {:storage-dir (.getPath dir)
                                        :submit-task! (fn [request]
                                                        (swap! attempts conj request)
                                                        (when (= :journal (:mode request))
                                                          (throw (ex-info "lost reply" {})))
                                                        {:task-id (:request-id request)})})]
        (knowledge/ingest-turn! sys owner turn)
        (is (= #{:uncertain :pending} (set (map :status (knowledge/tasks sys owner (:source-id turn))))))
        (knowledge/close-system! sys))
      (let [sys (knowledge/open-system {:storage-dir (.getPath dir)
                                        :submit-task! (fn [request]
                                                        (swap! attempts conj request)
                                                        {:task-id (:request-id request)})})]
        (try
          (is (every? :task-id (knowledge/tasks sys owner (:source-id turn))))
          (is (= 3 (count @attempts)))
          (is (= 1 (count (set (map :request-id (filter #(= :journal (:mode %)) @attempts))))))
          (finally (knowledge/close-system! sys))))
      (finally (doseq [f (reverse (file-seq dir))] (clojure.java.io/delete-file f true))))))

(defn- internal-post [server actor route value]
  (let [req (-> (HttpRequest/newBuilder
                  (URI/create (str "http://127.0.0.1:" (live/port server) route)))
                (.header "Authorization" "Bearer test-secret")
                (.header "X-AWP-Owner" actor)
                (.header "Content-Type" "application/json")
                (.POST (HttpRequest$BodyPublishers/ofString (json/write-str value)))
                .build)
        response (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (json/read-str (.body response) :key-fn keyword)}))

(deftest knowledge-001-live-settlement-reaches-owner-knowledge-route
  (let [sessions (session/open-system {})
        knowledge-system (knowledge/open-system {:submit-task! (fn [request] {:task-id (:request-id request)})})
        server (live/start! {:system sessions :knowledge-system knowledge-system :token "test-secret"})]
    (try
      (internal-post server owner "/internal/session/fresh" {:command-id "knowledge-live-fresh"})
      (internal-post server owner "/internal/session/submit"
                     {:turn-id "knowledge-live-turn" :origin "voice"
                      :messages [{:role "user" :content "I learned a song."}]})
      (is (= "completed" (get-in (internal-post server owner "/internal/session/settle"
                                                {:turn-id "knowledge-live-turn" :status "completed"
                                                 :answer "Good."}) [:body :status])))
      (let [conversation-id (get-in (internal-post server owner "/internal/session/turn"
                                                   {:turn-id "knowledge-live-turn"}) [:body :conversation-id])
            source-id (str conversation-id ":knowledge-live-turn")
            work (internal-post server owner "/internal/knowledge/tasks" {:source-id source-id})]
        (is (= 2 (count (:body work))))
        (is (= 200 (:status (internal-post server other "/internal/knowledge/suggestions" {}))))
        (is (empty? (:body (internal-post server other "/internal/knowledge/suggestions" {})))))
      (finally (live/stop! server) (knowledge/close-system! knowledge-system)
               (session/close-system! sessions)))))
