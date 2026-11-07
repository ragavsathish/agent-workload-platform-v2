(ns awp-v2.knowledge-gateway-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [awp-v2.knowledge.gateway-adapter :as gateway])
  (:import [com.sun.net.httpserver HttpServer HttpHandler]
           [java.net InetSocketAddress]))

(deftest gateway-protocol-sends-owner-source-and-bundle
  (let [seen (atom [])
        server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext server "/" (reify HttpHandler
                                 (handle [_ exchange]
                                   (let [body (json/read-str (slurp (.getRequestBody exchange)) :key-fn keyword)
                                         path (.getPath (.getRequestURI exchange))
                                         response (if (= path "/v1/digital-twin/ingestions")
                                                    {:tasks {:journal {:task_id "journal-task"}
                                                             :classify {:task_id "classify-task"}}}
                                                    {:task_id "daily-task"})
                                         bytes (.getBytes (json/write-str response) "UTF-8")]
                                     (swap! seen conj {:path path :body body
                                                       :authorization (.getFirst (.getRequestHeaders exchange) "Authorization")})
                                     (.sendResponseHeaders exchange 202 (count bytes))
                                     (with-open [out (.getResponseBody exchange)] (.write out bytes))))))
    (.start server)
    (try
      (let [submit (gateway/task-submitter {:base-url (str "http://127.0.0.1:" (.getPort (.getAddress server)))
                                            :token "inner-secret"})
            base {:owner "owner@example.test" :source-id "conversation-1:message-1"
                  :conversation-id "conversation-1" :message-id "message-1"
                  :transcript "I learned a new song." :captured-at "2026-11-08T10:00:00Z"}]
        (is (= "journal-task" (:task-id (submit (assoc base :mode :journal)))))
        (is (= "classify-task" (:task-id (submit (assoc base :mode :classification)))))
        (is (= "daily-task" (:task-id (submit {:owner "owner@example.test" :mode :daily-account
                                               :journal-date "2026-11-08" :time-zone "Europe/Helsinki"
                                               :sources [{:source-ref "conversation-1:message-1" :speaker :owner
                                                          :captured-at "2026-11-08T10:00:00Z"
                                                          :transcript "I learned a new song."}]}))))
        (is (= ["/v1/digital-twin/ingestions" "/v1/digital-twin/ingestions"
                "/v1/digital-twin/daily-journals"] (mapv :path @seen)))
        (is (every? #(= "Bearer inner-secret" (:authorization %)) @seen))
        (is (= "message-1" (get-in @seen [0 :body :messageId])))
        (is (= "conversation-1:message-1" (get-in @seen [2 :body :sources 0 :sourceRef]))))
      (finally (.stop server 0)))))
