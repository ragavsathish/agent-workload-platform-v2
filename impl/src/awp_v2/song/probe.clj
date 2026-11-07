(ns awp-v2.song.probe
  (:require [awp-v2.song.api :as song]
            [clojure.java.io :as io]))

(def owner "song-probe@example.test")
(def request {:request-id "song-probe-1" :brief "A song about durable work"
              :style "Warm acoustic" :duration-seconds 30 :workspace "awp"})
(def manifest {:title "Durable work" :style "Warm acoustic"
               :lyrics "[Verse 1]\nCarry the work\n[Chorus]\nCarry it home"
               :duration-seconds 30 :bpm 90 :key-scale "D Minor" :time-signature "4"})
(defn -main [mode dir]
  (let [submitted (atom [])
        system (song/open-system {:storage-dir dir :eligible-workspaces #{"awp"}
                                  :submit-task! (fn [task] (swap! submitted conj task)
                                                  {:task-id (:task-id task)})
                                  :get-audio! (fn [_] (.getBytes "probe audio" "UTF-8"))})]
    (try
      (case mode
        "create"
        (let [created (song/create! system owner request)
              id (:id created)]
          (assert (= 1 (count @submitted)))
          (song/lyrics-result! system owner id (:lyrics-task-id created) manifest)
          (let [ready (song/get-song system owner id)]
            (assert (= :approval-required (:error (song/render! system owner id))))
            (song/decide! system owner id {:decision-id "probe-approval"
                                           :manifest-digest (:manifest-digest ready)
                                           :choice :approved}))
          (let [rendering (song/render! system owner id)]
            (assert (= 2 (count @submitted)))
            (song/render-result! system owner id (:render-task-id rendering) id))
          (spit (io/file dir "generation-id") id))
        "verify"
        (let [id (slurp (io/file dir "generation-id"))
              view (song/get-song system owner id)]
          (assert (= :complete (:status view)))
          (assert (= :approved (:approval view)))
          (assert (= :not-found (:error (song/get-song system "foreign@example.test" id))))
          (assert (= "probe audio" (String. (song/audio system owner id) "UTF-8")))
          (assert (empty? @submitted))
          (println "public song restart probe OK"))
        (throw (ex-info "unknown mode" {:mode mode})))
      (finally (song/close-system! system)))))
