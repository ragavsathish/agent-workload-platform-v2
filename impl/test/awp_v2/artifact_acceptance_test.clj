(ns awp-v2.artifact-acceptance-test
  (:require [awp-v2.tasking.api :as tasking]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [cognitect.anomalies :as anom])
  (:import [java.nio.file Files]
           [java.util UUID]
           [java.util.zip ZipInputStream]))

(def owner "owner@example.test")
(def foreign "foreign@example.test")
(defn- temp-dir [] (.toFile (Files/createTempDirectory "artifact-slice-" (make-array java.nio.file.attribute.FileAttribute 0))))
(defn- delete! [root] (doseq [file (reverse (file-seq root))] (io/delete-file file true)))
(defn- git! [dir & args]
  (let [process (-> (ProcessBuilder. (into ["git" "-C" (.getPath dir)] args)) .inheritIO .start)]
    (when-not (zero? (.waitFor process)) (throw (ex-info "git command failed" {:args args})))))
(defn- fixture [workspace f]
  (let [root (temp-dir) dir (io/file root workspace) id (UUID/randomUUID)]
    (try
      (.mkdirs dir)
      (when (= workspace "project")
        (git! dir "init" "-q")
        (spit (io/file dir "readme.txt") "before\n")
        (git! dir "add" ".")
        (git! dir "-c" "user.name=Test" "-c" "user.email=test@example.test" "commit" "-qm" "baseline"))
      (let [opts {:storage-dir (.getPath root) :eligible-workspaces #{workspace}
                  :workspace-roots {workspace (.getPath dir)}}
            sys (tasking/open-system opts)]
        (try
          (tasking/submit! sys owner {:request-id id :owner owner :instruction "Make work" :workspace workspace})
          (tasking/dispatch! sys id workspace)
          (f root dir sys id opts)
          (finally (tasking/close-system! sys))))
      (finally (delete! root)))))

(defn- capture! [sys id answer]
  (if-let [f (ns-resolve 'awp-v2.tasking.api 'capture-complete!)]
    (f sys id {:text answer})
    (throw (ex-info "capture-complete! unavailable" {}))))
(defn- artifact [sys who id kind]
  (if-let [f (ns-resolve 'awp-v2.tasking.api 'artifact)]
    (f sys who id kind)
    (throw (ex-info "artifact query unavailable" {}))))

(deftest artifact-001-repository-patch
  (fixture "project" (fn [_ dir sys id _]
    (spit (io/file dir "readme.txt") "after\n")
    (is (= :completed (:status (capture! sys id "Done"))))
    (let [bytes (:bytes (artifact sys owner id "patch"))]
      (is (re-find #"before|after" (String. ^bytes bytes "UTF-8")))))))

(deftest delivery-002-capture-preserves-repository-index
  (fixture "project" (fn [_ dir sys id _]
    (let [index (io/file dir ".git" "index")
          original (Files/readAllBytes (.toPath index))]
      (spit (io/file dir "readme.txt") "after\n")
      (spit (io/file dir "new.txt") "new\n")
      (is (= :completed (:status (capture! sys id "Done"))))
      (let [patch (String. ^bytes (:bytes (artifact sys owner id "patch")) "UTF-8")]
        (is (re-find #"new.txt" patch))
        (is (re-find #"after" patch)))
      (is (java.util.Arrays/equals original (Files/readAllBytes (.toPath index))))))))

(deftest artifact-002-scratch-archive
  (fixture "scratch" (fn [_ dir sys id _]
    (spit (io/file dir "note.txt") "hello")
    (capture! sys id "Done")
    (let [bytes (:bytes (artifact sys owner id "workspace"))]
      (with-open [zip (ZipInputStream. (java.io.ByteArrayInputStream. bytes))]
        (is (= "note.txt" (.getName (.getNextEntry zip))))
        (is (= "hello" (String. (.readAllBytes zip) "UTF-8"))))))))

(deftest artifact-003-capture-failure
  (fixture "scratch" (fn [_ dir sys id _]
    (spit (io/file dir "large.txt") (apply str (repeat 2000 "x")))
    (is (= :failed (:status (capture! (assoc sys :max-artifact-bytes 100) id "Done"))))
    (is (nil? (:result (tasking/task sys owner id)))))))

(deftest artifact-004-kind-bound-to-workspace
  (doseq [[workspace wrong] [["scratch" "patch"] ["project" "workspace"]]]
    (fixture workspace (fn [_ dir sys id _]
      (spit (io/file dir "note.txt") "work")
      (capture! sys id "Done")
      (is (= ::anom/not-found (::anom/category (artifact sys owner id wrong))))))))

(deftest artifact-005-safe-public-answer
  (fixture "scratch" (fn [_ dir sys id _]
    (spit (io/file dir "note.txt") "work")
    (capture! sys id "Done")
    (let [result (:result (tasking/task sys owner id))]
      (is (= "Done" (get-in result [:response :text])))
      (is (= #{"workspace"} (set (keys (:artifacts result)))))
      (is (not (re-find #"s3://|source-uri|rawEvents" (pr-str result))))))))

(deftest owner-client-007-artifact-006-owner-boundary
  (fixture "scratch" (fn [_ dir sys id _]
    (spit (io/file dir "note.txt") "work")
    (capture! sys id "Done")
    (is (= ::anom/not-found (::anom/category (tasking/task sys foreign id))))
    (is (= ::anom/not-found (::anom/category (artifact sys foreign id "workspace")))))))

(deftest artifact-007-exact-declaration
  (fixture "scratch" (fn [_ dir sys id _]
    (spit (io/file dir "note.txt") "work")
    (capture! sys id "Done")
    (is (= ::anom/not-found (::anom/category (artifact sys owner (UUID/randomUUID) "workspace"))))
    (is (= ::anom/not-found (::anom/category (artifact sys owner id "execution")))))))

(deftest owner-client-007-artifact-008-loss-is-separate
  (fixture "scratch" (fn [root dir sys id _]
    (spit (io/file dir "note.txt") "work")
    (capture! sys id "Done")
    (let [file (io/file root "artifacts" (str id) "workspace.zip")
          original (Files/readAllBytes (.toPath file))
          tampered (aclone original)]
      (with-open [out (io/output-stream file)]
        (.write out original)
        (.write out (byte-array [1])))
      (is (= ::anom/not-found (::anom/category (artifact sys owner id "workspace"))))
      (aset-byte tampered 0 (byte (bit-xor 1 (aget tampered 0))))
      (with-open [out (io/output-stream file)] (.write out tampered))
      (is (= ::anom/not-found (::anom/category (artifact sys owner id "workspace"))))
      (io/delete-file file))
    (is (= ::anom/not-found (::anom/category (artifact sys owner id "workspace"))))
    (is (= :completed (:status (tasking/task sys owner id)))))))

(deftest artifact-009-restart-preserves-delivery
  (fixture "scratch" (fn [root dir sys id opts]
    (spit (io/file dir "note.txt") "work")
    (capture! sys id "Done")
    (tasking/close-system! sys)
    (let [reopened (tasking/open-system opts)]
      (try
        (is (= :completed (:status (tasking/task reopened owner id))))
        (is (= "workspace.zip" (:filename (artifact reopened owner id "workspace"))))
        (finally (tasking/close-system! reopened)))))))
