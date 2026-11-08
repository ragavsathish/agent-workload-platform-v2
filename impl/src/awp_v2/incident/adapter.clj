(ns awp-v2.incident.adapter
  (:require [awp-v2.incident.api :as incident]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.net URI URLEncoder]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]
           [java.time Duration]
           [java.util Base64]
           [java.util.concurrent Executors TimeUnit]))

(defn load-rules [path]
  (let [document (json/read-str (slurp path) :key-fn keyword)]
    (into {} (for [group (:groups document)
                   rule (:rules group)
                   :when (= "true" (get-in rule [:labels :auto_investigate]))]
               [(:uid rule) {:title (:title rule)
                             :summary (get-in rule [:annotations :summary])
                             :promql (get-in rule [:data 0 :model :expr])}]))))
(defn- http-get [^HttpClient client url headers timeout-seconds]
  (let [builder (-> (HttpRequest/newBuilder (URI/create url))
                    (.timeout (Duration/ofSeconds timeout-seconds))
                    .GET)
        _ (doseq [[name value] headers] (.header builder name value))
        response (.send client (.build builder) (HttpResponse$BodyHandlers/ofString))]
    (when-not (= 200 (.statusCode response))
      (throw (ex-info "monitor source unavailable" {:status (.statusCode response)})))
    (when (> (count (.body response)) 1048576)
      (throw (ex-info "monitor source response too large" {})))
    (json/read-str (.body response) :key-fn keyword)))
(defn adapter [{:keys [alerts-url metrics-url password-file]}]
  (when-not (and (re-matches #"http://(?:127\.0\.0\.1|localhost):[0-9]+/.+" (str alerts-url))
                 (re-matches #"http://(?:127\.0\.0\.1|localhost):[0-9]+" (str metrics-url))
                 (.isFile (io/file password-file)))
    (throw (ex-info "monitor requires loopback sources and password file" {})))
  (let [client (HttpClient/newHttpClient)]
    {:fetch-alerts! (fn []
                      (let [password (str/trim (slurp password-file))
                            token (.encodeToString (Base64/getEncoder)
                                                   (.getBytes (str "admin:" password) "UTF-8"))
                            result (http-get client alerts-url {"Authorization" (str "Basic " token)} 10)]
                        (when-not (and (= "success" (:status result))
                                       (vector? (get-in result [:data :alerts])))
                          (throw (ex-info "invalid alert list" {})))
                        (get-in result [:data :alerts])))
     :metric-fetch! (fn [expression]
                      (http-get client
                                (str metrics-url "/api/v1/query?query="
                                     (URLEncoder/encode expression "UTF-8"))
                                {} 3))}))
(defn poll! [system owner source at]
  (let [result (try {:alerts ((:fetch-alerts! source))}
                    (catch Throwable _ {:unavailable true}))]
    (if (:unavailable result)
      (incident/unavailable! system owner at)
      (incident/observe! system owner at (:alerts result)))))
(defn start-poller! [system owner source]
  (let [executor (Executors/newSingleThreadScheduledExecutor)]
    (.scheduleWithFixedDelay executor
                             ^Runnable (fn [] (poll! system owner source (quot (System/currentTimeMillis) 1000)))
                             0 60 TimeUnit/SECONDS)
    executor))
