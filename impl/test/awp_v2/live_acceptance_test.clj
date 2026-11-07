(ns awp-v2.live-acceptance-test
  (:require [awp-v2.live.http :as live]
            [awp-v2.session.api :as session]
            [clojure.data.json :as json]
            [clojure.test :refer [deftest is]]
            [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.nio.file Files]))

(def owner "owner@example.test")
(def other "other@example.test")
(def client (HttpClient/newHttpClient))

(defn- room []
  (str (.toFile (Files/createTempDirectory "awp-live-test-"
                    (make-array java.nio.file.attribute.FileAttribute 0)))))

(defn- request [server actor operation data & [token]]
  (let [body (json/write-str data)
        req (-> (HttpRequest/newBuilder
                  (URI/create (str "http://127.0.0.1:" (live/port server)
                                   "/internal/session/" operation)))
                (.header "Authorization" (str "Bearer " (or token "test-secret")))
                (.header "X-AWP-Owner" actor)
                (.header "Content-Type" "application/json")
                (.POST (HttpRequest$BodyPublishers/ofString body))
                (.build))
        response (.send client req (HttpResponse$BodyHandlers/ofString))]
    {:status (.statusCode response)
     :body (json/read-str (.body response) :key-fn keyword)}))

(defn- with-service [f]
  (let [dir (room)
        system (session/open-system {:storage-dir dir
                 :owned-task? (fn [actor task]
                                (and (= actor owner) (= task "task-1")))})
        server (live/start! {:system system :token "test-secret"})]
    (try (f server system dir)
         (finally (live/stop! server) (session/close-system! system)))))

(defn- post [server operation data]
  (request server owner operation data))
(defn- fresh [server id]
  (get-in (post server "fresh" {:command-id id}) [:body :id]))
(defn- submit [server id text origin]
  (post server "submit" {:turn-id id :origin origin
                          :messages [{:role "user" :content text}]}))
(defn- settle [server id answer]
  (post server "settle" {:turn-id id :status "completed" :answer answer}))
(defn- texts [response]
  (mapv :text (get-in response [:body :entries])))

(deftest live-001-public-selection-and-history
  (with-service (fn [server _ _]
    (let [old (fresh server "first")]
      (submit server "turn-1" "first" "typed") (settle server "turn-1" "answer")
      (fresh server "second")
      (is (= old (get-in (post server "resume" {:command-id "resume" :conversation-id old}) [:body :id])))
      (is (= old (get-in (post server "catalog" {}) [:body :sessions 0 :id])))
      (is (= ["first" "answer"] (texts (post server "history" {:conversation-id old}))))
      (is (= old (get-in (post server "selection" {}) [:body :id])))))))

(deftest live-002-typed-and-spoken-turns-share-selection
  (with-service (fn [server _ _]
    (let [id (fresh server "first")]
      (is (= id (get-in (submit server "typed" "typed prompt" "typed") [:body :conversation-id])))
      (settle server "typed" "typed answer")
      (is (= id (get-in (submit server "voice" "spoken prompt" "voice") [:body :conversation-id])))
      (settle server "voice" "spoken answer")
      (is (= ["typed prompt" "typed answer" "spoken prompt" "spoken answer"]
             (texts (post server "transcript" {}))))))))

(deftest live-003-stream-interruption-keeps-selection
  (with-service (fn [server _ _]
    (let [id (fresh server "first")]
      (submit server "stream" "speak" "voice")
      (is (= "requested" (get-in (post server "stop" {:turn-id "stream"}) [:body :status])))
      (post server "settle" {:turn-id "stream" :status "interrupted"})
      (is (= id (get-in (post server "selection" {}) [:body :id])))
      (is (= "accepted" (get-in (submit server "later" "next" "typed") [:body :status])))))))

(deftest live-004-task-attachment-keeps-saved-history-separate
  (with-service (fn [server _ _]
    (let [id (fresh server "first")]
      (is (= "task-1" (get-in (post server "attach" {:task-id "task-1"}) [:body :task-id])))
      (is (= "task-1" (get-in (submit server "task-turn" "task prompt" "voice") [:body :task-id])))
      (settle server "task-turn" "task answer")
      (is (some #{"task answer"} (texts (post server "transcript" {}))))
      (is (not (some #{"task prompt"} (texts (post server "history" {:conversation-id id})))))))))

(deftest live-004-task-tool-can-attach-during-orchestrator-turn
  (with-service (fn [server _ _]
    (fresh server "first")
    (submit server "tool-turn" "attach this task" "voice")
    (is (= "task-1" (get-in (post server "attach" {:task-id "task-1"}) [:body :task-id])))
    (settle server "tool-turn" "attached")
    (is (= "task-1" (get-in (submit server "following-turn" "continue" "voice") [:body :task-id]))))))

(deftest live-005-selection-and-disconnect-release-attachment
  (with-service (fn [server _ _]
    (fresh server "first")
    (post server "attach" {:task-id "task-1"})
    (fresh server "second")
    (is (nil? (get-in (post server "routing" {}) [:body :task-id])))
    (post server "attach" {:task-id "task-1"})
    (post server "voice-ended" {})
    (is (nil? (get-in (post server "routing" {}) [:body :task-id]))))))

(deftest live-006-restart-recovers-selected-history
  (let [dir (room) options {:storage-dir dir}
        first-system (session/open-system options)
        first-server (live/start! {:system first-system :token "test-secret"})
        old (fresh first-server "first")]
    (submit first-server "old-turn" "saved" "voice")
    (settle first-server "old-turn" "result")
    (fresh first-server "second")
    (post first-server "resume" {:command-id "resume" :conversation-id old})
    (live/stop! first-server) (session/close-system! first-system)
    (let [again-system (session/open-system options)
          again-server (live/start! {:system again-system :token "test-secret"})]
      (try
        (is (= old (get-in (post again-server "selection" {}) [:body :id])))
        (is (= ["saved" "result"] (texts (post again-server "transcript" {}))))
        (finally (live/stop! again-server) (session/close-system! again-system))))))

(deftest live-007-retry-does-not-redispatch
  (with-service (fn [server _ _]
    (fresh server "first")
    (let [first (submit server "same" "effect" "voice")]
      (post server "settle" {:turn-id "same" :status "uncertain"})
      (is (= "uncertain" (get-in (submit server "same" "effect" "voice") [:body :status])))
      (is (= 409 (:status (submit server "same" "different" "voice"))))
      (is (= "accepted" (get-in first [:body :status])))))))

(deftest live-008-credentials-and-invalid-input
  (with-service (fn [server _ _]
    (let [id (fresh server "first")]
      (is (= 401 (:status (request server owner "catalog" {} "wrong"))))
      (is (= 404 (:status (request server other "history" {:conversation-id id}))))
      (is (= 400 (:status (post server "submit" {:turn-id "bad" :origin "typed"
                                     :messages [{:role "user" :content (apply str (repeat 8001 "x"))}]}))))))))

(deftest live-009-context-follows-selected-conversation
  (with-service (fn [server _ _]
    (let [id (fresh server "first")]
      (submit server "turn" "usage" "voice")
      (post server "observe" {:turn-id "turn" :observation
                    {:kind "context-usage" :tokens 8000 :contextWindow 16000
                     :estimated false :canCompact true}})
      (is (= 50.0 (get-in (post server "context" {}) [:body :percent])))
      (is (= "queued" (get-in (post server "compact" {:command-id "compact"}) [:body :status])))
      (settle server "turn" "done")
      (is (= id (get-in (post server "context" {}) [:body :conversation-id])))))))
