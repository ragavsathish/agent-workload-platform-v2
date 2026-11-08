(ns awp-v2.incident-adapter-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [awp-v2.incident.adapter :as adapter]
            [awp-v2.incident.api :as incident]
            [awp-v2.live.http :as live-http]
            [awp-v2.session.api :as session]
            [awp-v2.tasking.api :as tasking])
  (:import [com.sun.net.httpserver HttpHandler HttpServer]
           [java.net InetSocketAddress URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.nio.file Files]))

(def owner "incident-adapter@example.test")
(def rule {"awp_test" {:title "Test alert" :promql "up == 0" :summary "A service is down."}})
(def alert {:state "Alerting" :fingerprint "alert-1"
            :labels {:alertname "Test alert" :rule_uid "awp_test"
                     :managed_by "agent-workload-platform" :auto_investigate "true"
                     :payload "do not investigate"}})
(defn- send-json [exchange value]
  (let [body (.getBytes (json/write-str value) "UTF-8")]
    (.sendResponseHeaders exchange 200 (alength body))
    (with-open [out (.getResponseBody exchange)] (.write out body))))

(deftest incident-adapter-uses-loopback-alert-and-metric-apis
  (let [password (.toFile (Files/createTempFile "incident-password-" ".txt"
                                             (make-array java.nio.file.attribute.FileAttribute 0)))
        server (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)
        calls (atom [])
        tasks (tasking/open-system {:eligible-workspaces #{"awp"}})]
    (spit password "secret")
    (.createContext server "/alerts"
      (reify HttpHandler
        (handle [_ exchange]
          (swap! calls conj [(.getPath (.getRequestURI exchange))
                             (.getFirst (.getRequestHeaders exchange) "Authorization")])
          (send-json exchange {:status "success" :data {:alerts [alert]}}))))
    (.createContext server "/api/v1/query"
      (reify HttpHandler
        (handle [_ exchange]
          (swap! calls conj [(.getPath (.getRequestURI exchange)) nil])
          (send-json exchange {:status "success" :data {:result
            [{:metric {:service_name "voice-edge" :payload "private"} :value [1000 "3"]}]}}))))
    (.start server)
    (let [base (str "http://127.0.0.1:" (.getPort (.getAddress server)))
          source (adapter/adapter {:alerts-url (str base "/alerts")
                                   :metrics-url base :password-file (.getPath password)})
          sys (incident/open-system {:task-system tasks :workspace "awp" :rules rule
                                     :metric-fetch! (:metric-fetch! source)})]
      (try
        (adapter/poll! sys owner source 1000)
        (let [row (first (incident/incidents sys owner))
              task (tasking/task tasks owner (:request-id row))]
          (is (= :accepted (:submission-status row)))
          (is (= 1 (tasking/accepted-count tasks)))
          (is (re-find #"voice-edge" (:instruction task)))
          (is (not (re-find #"private|do not investigate" (:instruction task))))
          (is (= 2 (count @calls)))
          (is (.startsWith (second (first @calls)) "Basic ")))
        (finally (incident/close-system! sys)
                 (tasking/close-system! tasks)
                 (.stop server 0)
                 (.delete password)))))
  (is (= 8 (count (adapter/load-rules "../raw/v1/stack-alerts.json")))))

(deftest incident-internal-owner-query-is-bound-to-grain-state
  (let [tasks (tasking/open-system {:eligible-workspaces #{"awp"}})
        incidents (incident/open-system {:task-system tasks :workspace "awp" :rules rule})
        sessions (session/open-system {})
        server (live-http/start! {:system sessions :task-system tasks
                                  :incident-system incidents :token "test-token"})
        client (HttpClient/newHttpClient)
        base (str "http://127.0.0.1:" (live-http/port server))
        post (fn [owner operation body]
               (let [request (-> (HttpRequest/newBuilder (URI/create (str base "/internal/incident/" operation)))
                                 (.header "Authorization" "Bearer test-token")
                                 (.header "X-AWP-Owner" owner)
                                 (.POST (HttpRequest$BodyPublishers/ofString (json/write-str body)))
                                 .build)]
                 (.send client request (HttpResponse$BodyHandlers/ofString))))]
    (try
      (incident/observe! incidents owner 1000 [alert])
      (let [row (first (incident/incidents incidents owner))
            own (post owner "get" {:epoch-id (:epoch-id row)})
            foreign (post "foreign@example.test" "get" {:epoch-id (:epoch-id row)})
            listed (post owner "list" {})]
        (is (= 200 (.statusCode own)))
        (is (= 404 (.statusCode foreign)))
        (is (= (:epoch-id row) (:epoch-id (json/read-str (.body own) :key-fn keyword))))
        (is (= 1 (count (json/read-str (.body listed))))))
      (finally (live-http/stop! server)
               (session/close-system! sessions)
               (incident/close-system! incidents)
               (tasking/close-system! tasks)))))
