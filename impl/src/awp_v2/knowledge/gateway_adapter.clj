(ns awp-v2.knowledge.gateway-adapter
  "Legacy AWP Gateway task protocol. Its response is checked before admission is confirmed."
  (:require [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))

(defn- post-json [client base-url token path body]
  (let [request (-> (HttpRequest/newBuilder (URI/create (str (str/replace base-url #"/$" "") path)))
                    (.timeout (Duration/ofSeconds 10))
                    (.header "Authorization" (str "Bearer " token))
                    (.header "Content-Type" "application/json")
                    (.POST (HttpRequest$BodyPublishers/ofString (json/write-str body)))
                    .build)
        response (.send client request (HttpResponse$BodyHandlers/ofString))]
    (when-not (<= 200 (.statusCode response) 299)
      (throw (ex-info "knowledge gateway rejected task submission"
                      {:status (.statusCode response) :path path})))
    (json/read-str (.body response) :key-fn keyword)))

(defn task-submitter [{:keys [base-url token]}]
  (when (or (str/blank? base-url) (str/blank? token))
    (throw (ex-info "gateway URL and token are required" {})))
  (let [uri (URI/create base-url)]
    (when-not (and (= "http" (.getScheme uri))
                   (contains? #{"127.0.0.1" "localhost"} (.getHost uri)))
      (throw (ex-info "knowledge task gateway must be loopback HTTP" {}))))
  (let [client (HttpClient/newHttpClient)]
    (fn [{:keys [owner mode conversation-id message-id source-id transcript captured-at
                 journal-date time-zone sources]}]
      (case mode
        (:journal :classification)
        (let [[conversation-id message-id] (if (and conversation-id message-id)
                                             [conversation-id message-id]
                                             (let [parts (str/split source-id #":" 2)]
                                               (when-not (= 2 (count parts))
                                                 (throw (ex-info "invalid source identity" {})))
                                               parts))
              response (post-json client base-url token "/v1/digital-twin/ingestions"
                                  {:identity owner :sessionId conversation-id :messageId message-id
                                   :transcript transcript :capturedAt captured-at})
              task (get-in response [:tasks (if (= mode :journal) :journal :classify) :task_id])]
          (when-not (and (string? task) (seq task))
            (throw (ex-info "gateway omitted extraction task identity" {:mode mode})))
          {:task-id task})
        :daily-account
        (let [response (post-json client base-url token "/v1/digital-twin/daily-journals"
                                  {:identity owner :journalDate journal-date :timeZone time-zone
                                   :sources (mapv (fn [{:keys [source-ref speaker captured-at transcript]}]
                                                    {:sourceRef source-ref :speaker (name speaker)
                                                     :capturedAt captured-at :transcript transcript}) sources)})
              task (:task_id response)]
          (when-not (and (string? task) (seq task))
            (throw (ex-info "gateway omitted daily task identity" {})))
          {:task-id task})
        (throw (ex-info "unsupported knowledge mode" {:mode mode}))))))
