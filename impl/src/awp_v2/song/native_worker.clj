(ns awp-v2.song.native-worker
  (:require [awp-v2.song.api :as song]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell])
  (:import [java.io File]))

(defn- camel-manifest [manifest]
  {:title (:title manifest) :style (:style manifest) :lyrics (:lyrics manifest)
   :durationSeconds (:duration-seconds manifest) :bpm (:bpm manifest)
   :keyScale (:key-scale manifest) :timeSignature (:time-signature manifest)})
(defn renderer [{:keys [script artifact-dir node]}]
  (when-not (and (string? script) (.isFile (io/file script))
                 (string? artifact-dir) (not (.isBlank artifact-dir)))
    (throw (ex-info "song renderer requires compiled script and artifact directory" {})))
  (fn [system {:keys [owner generation-id task-id manifest]}]
    (future
      (try
        (let [work {:owner owner :generationId generation-id :taskId task-id
                    :manifest (camel-manifest manifest)}
              result (shell/sh (or node "node") script :in (json/write-str work)
                               :env (assoc (into {} (System/getenv)) "AWP_SONG_ARTIFACT_DIR" artifact-dir))
              output (when (zero? (:exit result)) (json/read-str (:out result) :key-fn keyword))]
          (song/render-result! system owner generation-id task-id (:artifactId output)))
        (catch Throwable _
          (song/render-result! system owner generation-id task-id nil))))))
(defn local-audio [artifact-dir]
  (fn [{:keys [artifact-id]}]
    (if (and (string? artifact-id) (re-matches #"[0-9a-f-]{36}" artifact-id))
      (let [file (File. artifact-dir (str artifact-id ".m4a"))]
        (if (and (.isFile file) (<= (.length file) (* 128 1024 1024)))
          (java.nio.file.Files/readAllBytes (.toPath file))
          (throw (ex-info "song artifact unavailable" {}))))
      (throw (ex-info "invalid song artifact" {})))))
