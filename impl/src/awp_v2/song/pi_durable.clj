(ns awp-v2.song.pi-durable
  (:require [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))

(defn client [{:keys [base-url token-file]}]
  (when-not (and (string? base-url) (re-matches #"http://(?:127\.0\.0\.1|localhost):[0-9]+" (str/replace base-url #"/$" ""))
                 (string? token-file))
    (throw (ex-info "Pi Durable owner requires loopback URL and token file" {})))
  {:url (str (str/replace base-url #"/$" "") "/private/task")
   :token-file token-file :http (HttpClient/newHttpClient)})
(defn call! [{:keys [url token-file ^HttpClient http]} command fields]
  (let [token (str/trim (slurp token-file))
        body (json/write-str (merge {:id (str (random-uuid)) :command command} fields))
        request (-> (HttpRequest/newBuilder (URI/create url))
                    (.header "Authorization" (str "Bearer " token))
                    (.header "Content-Type" "application/json")
                    (.timeout (Duration/ofSeconds 30))
                    (.POST (HttpRequest$BodyPublishers/ofString body))
                    .build)
        response (.send http request (HttpResponse$BodyHandlers/ofString))
        envelope (json/read-str (.body response) :key-fn keyword)]
    (when-not (and (= 200 (.statusCode response)) (:success envelope))
      (throw (ex-info "Pi Durable owner request failed" {:command command :status (.statusCode response)})))
    (:data envelope)))
(defn submit-lyrics! [client {:keys [task-id generation-id owner brief workspace style duration-seconds]}]
  (let [params {:generation_id generation-id :identity owner :brief brief :workspace workspace
                :style style :duration_seconds duration-seconds}
        result (call! client "submit" {:requestId task-id
                                        :input {:kind "song-lyrics" :params params :requestId task-id}})]
    {:task-id (str (:task_id result))}))
(defn get-task! [client task-id]
  (call! client "get" {:taskId task-id}))
(defn abort! [client task-id owner]
  (call! client "cancel_product" {:taskId task-id :identity owner})
  (call! client "abort" {:taskId task-id}))
