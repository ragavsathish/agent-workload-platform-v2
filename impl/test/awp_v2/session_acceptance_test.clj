(ns awp-v2.session-acceptance-test
  (:require [awp-v2.session.api :as session]
            [clojure.test :refer [deftest is testing]]
            [clojure.string :as str])
  (:import [java.nio.file Files]))

(def owner "owner@example.test")
(def other "other@example.test")

(defn room []
  (str (.toFile (Files/createTempDirectory "awp-session-test-"
                                      (make-array java.nio.file.attribute.FileAttribute 0)))))

(defn with-session [f]
  (let [dir (room)
        calls (atom [])
        env (atom 0)
        options {:storage-dir dir
                 :tool-environment env
                 :owned-task? (fn [actor task] (and (= actor owner) (= task "task-1")))
                 :turn-driver (fn [turn] (swap! calls conj turn))}
        system (session/open-system options)]
    (try (f system calls options)
         (finally (session/close-system! system)))))

(defn fresh [s n] (session/fresh! s owner (str "fresh-" n)))
(defn turn [s id text origin]
  (session/submit! s owner {:turn-id id :origin origin
                            :messages [{:role "system" :content "stable"}
                                       {:role "user" :content text}]}))
(defn finish [s id answer]
  (session/settle! s owner id :completed answer))
(defn texts [snapshot] (mapv :text (:entries snapshot)))

(deftest session-001-fresh-preserves-history
  (with-session (fn [s _ _]
    (let [old (:id (fresh s 1))]
      (turn s "t1" "first" :voice) (finish s "t1" "answer")
      (is (not= old (:id (fresh s 2))))
      (is (= ["first" "answer"] (texts (session/history s owner old))))
      (is (not= old (:id (session/selection s owner))))))))

(deftest session-002-resume-restores-live-history
  (with-session (fn [s _ _]
    (let [old (:id (fresh s 1))]
      (turn s "t1" "first" :typed) (finish s "t1" "answer")
      (fresh s 2)
      (is (= old (:id (session/resume! s owner "resume-1" old))))
      (is (= (texts (session/history s owner old))
             (texts (session/transcript s owner))))))))

(deftest session-003-selection-survives-reopen
  (let [dir (room) opts {:storage-dir dir :turn-driver (fn [_] nil)}
        s (session/open-system opts)
        old (:id (fresh s 1))]
    (turn s "t1" "old" :typed) (finish s "t1" "answer")
    (fresh s 2)
    (session/resume! s owner "resume-1" old)
    (turn s "t2" "again" :voice) (finish s "t2" "again-answer")
    (session/close-system! s)
    (let [reopened (session/open-system opts)]
      (try (is (= old (:id (session/selection reopened owner))))
           (is (= ["old" "answer" "again" "again-answer"]
                  (texts (session/transcript reopened owner))))
           (finally (session/close-system! reopened))))))

(deftest session-003-resume-choice-survives-without-new-turn
  (let [dir (room) opts {:storage-dir dir}
        first-system (session/open-system opts)
        old (:id (fresh first-system 1))]
    (fresh first-system 2)
    (session/resume! first-system owner "resume-older" old)
    (session/close-system! first-system)
    (let [reopened (session/open-system opts)]
      (try (is (= old (:id (session/selection reopened owner))))
           (finally (session/close-system! reopened))))))

(deftest session-004-missing-resume-preserves-selection
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (is (= :not-found (:error (session/resume! s owner "resume-1" "missing"))))
      (is (= id (:id (session/selection s owner))))
      (is (= [] (:entries (session/transcript s owner))))))))

(deftest session-005-busy-selection-refused
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (turn s "t1" "slow" :voice)
      (is (= :busy (:error (session/fresh! s owner "fresh-2"))))
      (is (= id (:id (session/selection s owner))))
      (finish s "t1" "done")
      (is (= ["slow" "done"] (texts (session/history s owner id))))))))

(deftest session-006-selection-retry-converges
  (with-session (fn [s _ _]
    (let [first (session/fresh! s owner "same")]
      (is (= first (session/fresh! s owner "same")))
      (is (= 1 (count (:sessions (session/catalog s owner)))))
      (is (= :conflict (:error (session/resume! s owner "same" (:id first)))))))))

(deftest session-006-concurrent-fresh-retry
  (with-session (fn [s _ _]
    (let [a (future (session/fresh! s owner "concurrent"))
          b (future (session/fresh! s owner "concurrent"))]
      (is (= (:id @a) (:id @b)))
      (is (= 1 (count (:sessions (session/catalog s owner)))))))))

(deftest session-007-voice-and-typed-share-selection
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (turn s "voice" "hello" :voice) (finish s "voice" "hi")
      (turn s "typed" "next" :typed) (finish s "typed" "yes")
      (is (= ["hello" "hi" "next" "yes"] (texts (session/history s owner id))))
      (is (= 1 (count (:sessions (session/catalog s owner)))))))))

(deftest session-008-only-latest-actionable-message
  (with-session (fn [s calls _]
    (fresh s 1)
    (session/submit! s owner {:turn-id "t1" :origin :typed
      :messages [{:role "user" :content "past"}
                 {:role "assistant" :content "prior answer"}
                 {:role "user" :content "current"}]})
    (is (= "current" (:prompt (first @calls))))
    (is (= ["current"] (texts (session/transcript s owner)))))))

(deftest session-009-safe-transcripts
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (turn s "t1" "hello" :voice)
      (session/observe! s owner "t1" {:kind :tool-start :tool-id "call" :name "read;secret" :input "private-input"})
      (session/observe! s owner "t1" {:kind :tool-end :tool-id "call" :failed? false :output "private-output"})
      (session/observe! s owner "t1" {:kind :assistant-delta :text "safe" :reasoning "private-reasoning"})
      (finish s "t1" "safe")
      (doseq [view [(session/transcript s owner) (session/history s owner id)]]
        (is (some #(= "readsecret" (:text %)) (:entries view)))
        (is (not (re-find #"private" (pr-str view))))
        (is (every? #(and (string? (:id %)) (integer? (:timestamp %)))
                    (:entries view))))))))

(deftest session-010-stop-retains-selection
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (turn s "t1" "stop" :voice)
      (is (= :requested (:status (session/stop! s owner "t1"))))
      (session/settle! s owner "t1" :interrupted nil)
      (is (= id (:id (session/selection s owner))))
      (turn s "t2" "later" :typed)
      (finish s "t2" "okay")
      (is (some #{"later"} (texts (session/transcript s owner))))))))

(deftest session-011-context-maintenance-serialized
  (with-session (fn [s _ _]
    (fresh s 1)
    (turn s "t1" "wait" :voice)
    (session/observe! s owner "t1" {:kind :context-usage :tokens 8000
                                     :contextWindow 16000 :estimated false
                                     :canCompact true})
    (is (= 50.0 (:percent (session/context s owner))))
    (is (= :queued (:status (session/compact! s owner "compact-1"))))
    (finish s "t1" "done")
    (is (pos? (:revision (session/context s owner))))
    (is (= (:id (session/selection s owner))
           (:conversation-id (session/context s owner)))))))

(deftest session-012-task-routing-boundary
  (with-session (fn [s calls _]
    (let [id (:id (fresh s 1))]
      (session/attach! s owner "task-1")
      (turn s "t1" "task question" :voice) (finish s "t1" "task answer")
      (is (= "task-1" (:task-id (first @calls))))
      (is (= id (:id (session/selection s owner))))
      (is (some #{"task answer"} (texts (session/transcript s owner))))
      (is (not (some #{"task question"} (texts (session/history s owner id)))))))))

(deftest session-013-selection-releases-task
  (with-session (fn [s _ _]
    (fresh s 1) (session/attach! s owner "task-1")
    (fresh s 2)
    (is (nil? (:task-id (session/routing s owner))))
    (is (= :not-found (:error (session/attach! s other "task-1")))))))

(deftest session-013-explicit-detach-restores-routing
  (with-session (fn [s calls _]
    (let [id (:id (fresh s 1))]
      (session/attach! s owner "task-1")
      (is (= :detached (:status (session/detach! s owner))))
      (turn s "after-detach" "home" :typed)
      (is (nil? (:task-id (first @calls))))
      (is (= id (:conversation-id (first @calls))))))))

(deftest session-014-owner-isolation
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (is (empty? (:sessions (session/catalog s other))))
      (is (= :not-found (:error (session/history s other id))))
      (is (= :not-found (:error (session/resume! s other "r" id))))
      (is (= :not-found (:error (session/submit! s other {:turn-id "x" :origin :voice
                                                         :messages [{:role "user" :content "x"}]}))))))))

(deftest session-015-voice-close-releases-attachment
  (with-session (fn [s calls _]
    (let [id (:id (fresh s 1))]
      (session/attach! s owner "task-1")
      (session/voice-ended! s owner)
      (turn s "t1" "home" :typed)
      (is (nil? (:task-id (first @calls))))
      (is (= id (:id (session/selection s owner))))))))

(deftest session-016-catalog-bounded-shape
  (with-session (fn [s _ _]
    (dotimes [n 32] (fresh s n))
    (let [items (:sessions (session/catalog s owner))]
      (is (= 30 (count items)))
      (is (:active (first items)))
      (is (= #{:id :title :updatedAt :messageCount :active}
             (set (keys (first items)))))
      (is (not (str/includes? (pr-str items) "awp-session-test")))))))

(deftest session-017-uncertain-turn-retry
  (with-session (fn [s calls _]
    (fresh s 1)
    (let [first (turn s "t1" "effect" :voice)]
      (session/settle! s owner "t1" :uncertain nil)
      (is (= :uncertain (:status (turn s "t1" "effect" :voice))))
      (is (= 1 (count @calls)))
      (is (= :conflict (:error (turn s "t1" "different" :voice))))))))

(deftest session-017-concurrent-turn-retry
  (with-session (fn [s calls _]
    (fresh s 1)
    (let [a (future (turn s "same" "effect" :voice))
          b (future (turn s "same" "effect" :voice))]
      (is (= (:turn-id @a) (:turn-id @b)))
      (is (= 1 (count @calls)))))))

(deftest session-010-failed-turn-can-be-followed
  (with-session (fn [s _ _]
    (fresh s 1)
    (turn s "failed" "first" :voice)
    (is (= :failed (:status (session/settle! s owner "failed" :failed nil))))
    (is (= :accepted (:status (turn s "next" "second" :typed)))))))

(deftest session-010-and-012-restart-recovery
  (let [dir (room)
        calls (atom [])
        opts {:storage-dir dir
              :owned-task? (fn [actor task] (and (= actor owner) (= task "task-1")))
              :turn-driver (fn [turn] (swap! calls conj turn))}
        first-system (session/open-system opts)
        id (:id (fresh first-system 1))]
    (session/attach! first-system owner "task-1")
    (turn first-system "pending" "may have run" :voice)
    (session/close-system! first-system)
    (let [reopened (session/open-system opts)]
      (try
        (is (= id (:id (session/selection reopened owner))))
        (is (nil? (:task-id (session/routing reopened owner))))
        (is (= :uncertain (:status (turn reopened "pending" "may have run" :voice))))
        (is (= 1 (count @calls)))
        (finally (session/close-system! reopened))))))

(deftest session-009-transcript-bounds
  (with-session (fn [s _ _]
    (let [id (:id (fresh s 1))]
      (dotimes [n 101]
        (let [turn-id (str "bounded-" n)]
          (turn s turn-id (if (= n 100) (apply str (repeat 25000 "x"))
                              (str "question-" n)) (if (= n 100) :voice :typed))
          (finish s turn-id (str "answer-" n))))
      (doseq [view [(session/transcript s owner) (session/history s owner id)]]
        (is (= 200 (count (:entries view))))
        (is (= 24000 (count (:text (nth (:entries view) 198)))))
        (is (every? #(<= (count (:text %)) 24000) (:entries view))))))))
