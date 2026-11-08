(ns awp-v2.knowledge.continuity-tool
  "Isolated Grain/ORC shadow and rollback rehearsal. Private output stays outside Git."
  (:require [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.migration :as migration]
            [awp-v2.knowledge.orc-adapter :as orc]
            [awp-v2.knowledge.projection :as projection]
            [clojure.data.json :as json]
            [clojure.java.io :as io])
  (:import [java.nio.file Files StandardOpenOption]
           [java.nio.file.attribute PosixFilePermissions]
           [java.security MessageDigest]))

(defn- sha [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (pr-str value) "UTF-8"))))
(defn- private-write! [path value]
  (let [file (.toPath (io/file path))
        perms (PosixFilePermissions/fromString "rw-------")]
    (Files/createFile file (into-array java.nio.file.attribute.FileAttribute
                                      [(PosixFilePermissions/asFileAttribute perms)]))
    (Files/writeString file (json/write-str value)
                       (into-array StandardOpenOption [StandardOpenOption/WRITE]))))
(defn- owner-view [system owner]
  (let [view (projection/owner-view system owner)]
    {:read-source (name (:read-source view))
     :items (:items view) :accounts (:accounts view) :accepted (:accepted view)
     :suggestions (knowledge/suggestions system owner)
     :profile (knowledge/profile system owner)
     :owner-concept-count (count (orc/owner-concepts system owner))}))
(defn -main [& [storage-dir package-file output-file]]
  (when-not (and storage-dir package-file output-file)
    (throw (ex-info "usage: continuity-tool <isolated-storage-dir> <private-package.json> <new-private-output.json>" {})))
  (let [package (with-open [r (io/reader package-file)] (json/read r :key-fn keyword))
        owner (:owner package)
        resources (:resources package)
        half (quot (count resources) 2)
        system (knowledge/open-system {:storage-dir storage-dir})]
    (try
      (let [prior (owner-view system owner)
            first-pass (migration/import-into-grain! system owner (assoc package :resources (vec (take half resources))))
            resumed (migration/import-into-grain! system owner package)
            replay (migration/import-into-grain! system owner package)
            reconciliation (migration/reconcile-grain system owner package)
            selected (migration/select-grain-import! system owner package)
            shadow (owner-view system owner)
            _ (orc/sync-owner! system owner)
            after-graph (owner-view system owner)]
        (knowledge/close-system! system)
        (let [reopened (knowledge/open-system {:storage-dir storage-dir})]
          (try
            (let [after-restart (owner-view reopened owner)
                  graph-rebuild (orc/sync-owner! reopened owner)
                  after-rebuild (owner-view reopened owner)
                  rollback (migration/rollback-grain-import! reopened owner (:snapshot-id package))
                  restored (owner-view reopened owner)
                  retained (migration/reconcile-grain reopened owner package)
                  private {:first-pass first-pass :resumed resumed :replay replay
                           :reconciliation reconciliation :selected selected
                           :prior prior :shadow shadow :after-graph after-graph
                           :after-restart after-restart :graph-rebuild graph-rebuild
                           :after-rebuild after-rebuild :rollback rollback :restored restored
                           :retained-after-rollback retained}
                  safe {:snapshot-matched? (:matched? reconciliation)
                        :source-count (:source-count reconciliation)
                        :imported-count (:imported-count reconciliation)
                        :resume-reused? (= :reused (:status replay))
                        :shadow-item-count (count (:items shadow))
                        :shadow-account-count (count (:accounts shadow))
                        :shadow-accepted-count (count (:accepted shadow))
                        :shadow-concept-count (:owner-concept-count shadow)
                        :restart-equal? (= (dissoc shadow :owner-concept-count)
                                           (dissoc after-restart :owner-concept-count))
                        :graph-equal? (= (dissoc shadow :owner-concept-count)
                                         (dissoc after-rebuild :owner-concept-count))
                        :rollback-equal? (= prior restored)
                        :history-retained? (:matched? retained)
                        :read-source-after-rollback (:read-source restored)
                        :private-output-digest (sha private)}]
              (private-write! output-file private)
              (println (json/write-str safe)))
            (finally (knowledge/close-system! reopened)))))
      (finally (knowledge/close-system! system)))))
