(ns awp-v2.knowledge.native-worker
  "Local Effect worker process adapter. The Grain knowledge stream owns task and result identity."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.nio.charset StandardCharsets]
           [java.util.concurrent TimeUnit]))

(defn- work-payload [{:keys [owner mode request-id source-id transcript captured-at
                             account-id journal-date time-zone sources]}]
  (if (= mode :daily-account)
    {:owner owner :mode "daily-account" :taskId request-id :accountId account-id
     :journalDate journal-date :timeZone time-zone
     :sources (mapv (fn [source] {:sourceRef (:source-ref source) :speaker "owner"
                                 :capturedAt (:captured-at source) :transcript (:transcript source)}) sources)}
    {:owner owner :mode (name mode) :taskId request-id :sourceId source-id
     :transcript transcript :capturedAt captured-at}))

(defn runner [{:keys [script node timeout-seconds]
               :or {node "node" timeout-seconds 330}}]
  (when (str/blank? script) (throw (ex-info "native knowledge worker script required" {})))
  (when-not (.isFile (io/file script)) (throw (ex-info "native knowledge worker script missing" {:script script})))
  (fn [request]
    (let [process (.start (ProcessBuilder. [node script "--emit-result"]))]
      (with-open [output (.getOutputStream process)]
        (.write output (.getBytes (json/write-str (work-payload request)) StandardCharsets/UTF_8)))
      (if-not (.waitFor process timeout-seconds TimeUnit/SECONDS)
        (do (.destroyForcibly process) (throw (ex-info "native knowledge worker timed out" {})))
        (let [stdout (slurp (.getInputStream process))
              stderr (slurp (.getErrorStream process))]
          (when-not (zero? (.exitValue process))
            (throw (ex-info "native knowledge worker failed" {:reason (subs stderr 0 (min 500 (count stderr)))})))
          (json/read-str stdout :key-fn keyword))))))
