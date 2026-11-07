(ns awp-v2.song-acceptance-test
  (:require [clojure.test :refer :all]
            [awp-v2.song.api :as song]))

(def owner "song-owner@example.test")
(def other "other@example.test")
(def request {:request-id "song-request-1" :brief "A song about learning" :style "Warm acoustic" :duration-seconds 120 :workspace "awp"})
(def manifest {:title "Keep learning" :style "Warm acoustic" :lyrics "[Verse 1]\nWe learn\n[Chorus]\nKeep learning"
               :duration-seconds 120 :bpm 96 :key-scale "D Minor" :time-signature "4"})
(defn with-song [f]
  (let [calls (atom [])
        sys (song/open-system {:eligible-workspaces #{"awp"}
                               :submit-task! (fn [task] (swap! calls conj task) {:task-id (:task-id task)})
                               :get-audio! (fn [_] (.getBytes "audio" "UTF-8"))})]
    (try (f sys calls) (finally (song/close-system! sys)))))
(defn ready!
  ([sys] (ready! sys request))
  ([sys input]
   (let [created (song/create! sys owner input)]
     (song/lyrics-result! sys owner (:id created) (:lyrics-task-id created) manifest)
     (song/get-song sys owner (:id created)))))
(defn approved!
  ([sys] (approved! sys request))
  ([sys input]
   (let [ready (ready! sys input)]
     (song/decide! sys owner (:id ready) {:decision-id "approve-1" :manifest-digest (:manifest-digest ready) :choice :approved})
     (song/get-song sys owner (:id ready)))))

(deftest song-001-request-replay-one-lyric-task
  (with-song (fn [sys calls]
               (let [a (song/create! sys owner request) b (song/create! sys owner request)]
                 (is (= (:id a) (:id b))) (is (= 1 (count @calls)))
                 (is (= (:lyrics-task-id a) (:task-id (first @calls))))))))
(deftest song-002-conflicting-request-identity
  (with-song (fn [sys calls]
               (song/create! sys owner request)
               (is (= :conflict (:error (song/create! sys owner (assoc request :brief "Changed")))))
               (is (= 1 (count @calls))))))
(deftest song-003-complete-manifest-before-audio
  (with-song (fn [sys _]
               (let [view (ready! sys)]
                 (is (= :lyrics-ready (:status view)))
                 (is (= manifest (:manifest view)))
                 (is (nil? (:audio-path view)))))))
(deftest song-004-exact-approval-before-render
  (with-song (fn [sys calls]
               (let [ready (ready! sys)]
                 (is (= :approval-required (:error (song/render! sys owner (:id ready)))))
                 (is (= 1 (count @calls)))
                 (is (= :conflict (:error (song/decide! sys owner (:id ready)
                                                     {:decision-id "bad" :manifest-digest "wrong" :choice :approved}))))
                 (song/decide! sys owner (:id ready) {:decision-id "yes" :manifest-digest (:manifest-digest ready) :choice :approved})
                 (is (= :generating-music (:status (song/render! sys owner (:id ready)))))
                 (is (= 2 (count @calls)))))))
(deftest song-005-rejection-needs-new-generation
  (with-song (fn [sys _]
               (let [ready (ready! sys)]
                 (song/decide! sys owner (:id ready) {:decision-id "no" :manifest-digest (:manifest-digest ready) :choice :rejected})
                 (is (= :rejected (:error (song/render! sys owner (:id ready)))))
                 (is (= :conflict (:error (song/lyrics-result! sys owner (:id ready)
                                                             (:lyrics-task-id ready) (assoc manifest :title "Changed")))))
                 (let [next-ready (ready! sys (assoc request :request-id "new"))]
                   (is (not= (:id ready) (:id next-ready)))
                   (is (= :conflict (:error (song/decide! sys owner (:id next-ready)
                                                       {:decision-id "no" :manifest-digest (:manifest-digest next-ready)
                                                        :choice :approved})))))))))
(deftest song-006-render-replay-one-task
  (with-song (fn [sys calls]
               (let [ready (approved! sys)
                     a (song/render! sys owner (:id ready)) b (song/render! sys owner (:id ready))]
                 (is (= (:render-task-id a) (:render-task-id b)))
                 (is (= 2 (count @calls)))))))
(deftest song-006-render-effect-starts-after-approval-once
  (let [effects (atom [])
        sys (song/open-system {:eligible-workspaces #{"awp"}
                               :submit-task! (fn [task] {:task-id (:task-id task)})
                               :run-render! (fn [_ work] (swap! effects conj work))})]
    (try
      (let [ready (ready! sys)]
        (is (= :approval-required (:error (song/render! sys owner (:id ready)))))
        (is (empty? @effects))
        (song/decide! sys owner (:id ready) {:decision-id "approve-run" :manifest-digest (:manifest-digest ready)
                                             :choice :approved})
        (song/render! sys owner (:id ready))
        (song/render! sys owner (:id ready))
        (is (= 1 (count @effects)))
        (is (= manifest (:manifest (first @effects)))))
      (finally (song/close-system! sys)))))
(deftest song-008-restart-interrupts-an-unfinished-render
  (let [dir (.toFile (java.nio.file.Files/createTempDirectory "song-restart-"
                                                        (make-array java.nio.file.attribute.FileAttribute 0)))
        options {:storage-dir (.getPath dir) :eligible-workspaces #{"awp"}
                 :submit-task! (fn [task] {:task-id (:task-id task)})}]
    (try
      (let [sys (song/open-system options)]
        (try
          (let [ready (approved! sys)]
            (song/render! sys owner (:id ready))
            (is (= :generating-music (:status (song/get-song sys owner (:id ready))))))
          (finally (song/close-system! sys))))
      (let [sys (song/open-system options)]
        (try
          (let [id (:id (song/create! sys owner request))]
            (is (= :failed (:status (song/get-song sys owner id))))
            (is (= :failed (:status (song/render! sys owner id)))))
          (finally (song/close-system! sys))))
      (finally (doseq [f (reverse (file-seq dir))]
                 (clojure.java.io/delete-file f true))))))
(deftest song-007-complete-private-audio
  (with-song (fn [sys _]
               (let [ready (approved! sys) rendering (song/render! sys owner (:id ready))]
                 (song/render-result! sys owner (:id ready) (:render-task-id rendering) "artifact-1")
                 (let [view (song/get-song sys owner (:id ready))]
                   (is (= :complete (:status view)))
                   (is (.startsWith (:audio-path view) "/v1/music/generations/"))
                   (is (not (contains? view :renderer-path)))
                   (is (= "audio" (String. (song/audio sys owner (:id ready)) "UTF-8"))))))))
(deftest song-008-failure-and-cancellation
  (with-song (fn [sys _]
               (let [a (song/create! sys owner request)]
                 (song/lyrics-result! sys owner (:id a) (:lyrics-task-id a) {:title "invalid"})
                 (is (= :failed (:status (song/get-song sys owner (:id a)))))
                 (is (= :not-ready (:error (song/render! sys owner (:id a))))))
               (let [ready (approved! sys (assoc request :request-id "render-fail"))
                     rendering (song/render! sys owner (:id ready))]
                 (is (string? (:render-task-id rendering)))
                 (song/render-result! sys owner (:id ready) (:render-task-id rendering) nil)
                 (is (= :failed (:status (song/get-song sys owner (:id ready)))))
                 (is (nil? (:audio-path (song/render! sys owner (:id ready))))))
               (let [created (song/create! sys owner (assoc request :request-id "cancel"))]
                 (song/cancel! sys owner (:id created))
                 (song/lyrics-result! sys owner (:id created) (:lyrics-task-id created) manifest)
                 (is (= :cancelled (:status (song/get-song sys owner (:id created)))))))))
(deftest song-009-owner-isolation
  (with-song
    (fn [sys calls]
      (let [ready (approved! sys)
            id (:id ready)
            rendering (song/render! sys owner id)]
        (song/render-result! sys owner id (:render-task-id rendering) "artifact-1")
        (is (= :not-found (:error (song/get-song sys other id))))
        (is (= :not-found (:error (song/decide! sys other id
                                               {:decision-id "x" :manifest-digest (:manifest-digest ready)
                                                :choice :approved}))))
        (is (= :not-found (:error (song/render! sys other id))))
        (is (= :not-found (:error (song/cancel! sys other id))))
        (is (= :not-found (:error (song/audio sys other id))))
        (is (= 2 (count @calls)))))))
