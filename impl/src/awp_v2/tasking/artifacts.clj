(ns awp-v2.tasking.artifacts
  (:require [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream File]
           [java.nio.file Files LinkOption Path StandardCopyOption]
           [java.security MessageDigest]
           [java.util.zip ZipEntry ZipOutputStream]))

(def kinds {"patch" ["changes.patch" "text/x-diff"]
            "workspace" ["workspace.zip" "application/zip"]})
(def default-max-bytes 16777216)
(defn- digest [^bytes data]
  (format "%064x" (BigInteger. 1 (.digest (MessageDigest/getInstance "SHA-256") data))))
(defn- fail! [reason] (throw (ex-info reason {:artifact/error true})))
(defn- run-git [^File root args]
  (let [process (.start (ProcessBuilder. ^java.util.List (into ["git" "-C" (.getPath root)] args)))
        data (with-open [in (.getInputStream process)] (.readAllBytes in))
        error (with-open [in (.getErrorStream process)] (.readAllBytes in))]
    (when-not (zero? (.waitFor process))
      (fail! (str "repository capture failed: " (String. error "UTF-8"))))
    data))
(defn- repo-patch [^File root max-bytes]
  (run-git root ["add" "--intent-to-add" "--all"])
  (let [data (run-git root ["diff" "--binary" "HEAD"])]
    (when (> (alength data) max-bytes) (fail! "task patch exceeds allowed size"))
    data))
(defn- scratch-archive [^File root max-bytes]
  (let [base (.toRealPath (.toPath root) (make-array LinkOption 0))]
    (with-open [walk (Files/walk base (make-array java.nio.file.FileVisitOption 0))]
      (let [files (->> (.toArray walk)
                       (filter #(Files/isRegularFile ^Path % (into-array LinkOption [LinkOption/NOFOLLOW_LINKS])))
                       (sort-by str))]
        (with-open [output (ByteArrayOutputStream.)
                    zip (ZipOutputStream. output)]
          (loop [remaining files count 0 total 0]
            (if-let [^Path path (first remaining)]
              (let [real (.toRealPath path (make-array LinkOption 0))
                    size (Files/size path)
                    _ (when (> (+ total size) max-bytes) (fail! "scratch workspace exceeds allowed size"))
                    data (Files/readAllBytes path)]
                (when-not (.startsWith real base) (fail! "scratch artifact escaped workspace"))
                (when (> (inc count) 1000) (fail! "scratch workspace exceeds file limit"))
                (when (> (+ total (alength data)) max-bytes) (fail! "scratch workspace exceeds allowed size"))
                (.putNextEntry zip (ZipEntry. (str (.relativize base real))))
                (.write zip data)
                (.closeEntry zip)
                (recur (next remaining) (inc count) (+ total (alength data))))
              (do (.finish zip)
                  (let [data (.toByteArray output)]
                    (when (> (alength data) max-bytes) (fail! "scratch archive exceeds allowed size"))
                    data)))))))))
(defn capture! [{:keys [artifact-dir workspace-roots max-artifact-bytes]} task-id workspace]
  (let [^File root (some-> (get workspace-roots workspace) io/file)
        max-bytes (or max-artifact-bytes default-max-bytes)
        kind (if (= workspace "scratch") "workspace" "patch")]
    (when-not (and root (.isDirectory root)) (fail! "selected workspace is unavailable"))
    (let [data (if (= kind "patch") (repo-patch root max-bytes)
                   (scratch-archive root max-bytes))
          sha (digest data)
          directory (io/file artifact-dir (str task-id))
          filename (first (get kinds kind))
          target (io/file directory filename)
          temporary (io/file directory (str filename ".tmp"))]
      (.mkdirs directory)
      (with-open [out (io/output-stream temporary)] (.write out data))
      (Files/move (.toPath temporary) (.toPath target)
                  (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING
                                                  StandardCopyOption/ATOMIC_MOVE]))
      {kind {:sha256 sha :bytes (alength data)}})))
(defn read! [{:keys [artifact-dir max-artifact-bytes]} task-id kind descriptor]
  (let [[filename content-type] (get kinds kind)
        max-bytes (or max-artifact-bytes default-max-bytes)
        ^File file (when filename (io/file artifact-dir (str task-id) filename))]
    (when-not (and file descriptor (Files/isRegularFile (.toPath file) (into-array LinkOption [LinkOption/NOFOLLOW_LINKS])))
      (fail! "task artifact is unavailable"))
    (let [size (.length file)]
      (when (or (> size max-bytes) (not= size (:bytes descriptor)))
        (fail! "task artifact is outside its allowed size"))
      (let [data (with-open [in (io/input-stream file)] (.readNBytes in (inc max-bytes)))]
        (when (or (not= (alength data) size) (not= (digest data) (:sha256 descriptor)))
          (fail! "task artifact read was incomplete or changed"))
        {:bytes data :filename filename :content-type content-type}))))
