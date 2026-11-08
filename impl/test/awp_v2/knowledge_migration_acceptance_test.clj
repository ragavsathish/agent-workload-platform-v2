(ns awp-v2.knowledge-migration-acceptance-test
  (:require [clojure.test :refer :all]
            [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.grain.control-plane.interface :as control-plane]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.orc.orc-service.interface :as sheet]
            [litellm.router :as router]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.migration :as migration]
            [awp-v2.knowledge.projection :as projection]
            [awp-v2.knowledge.orc-adapter :as orc]
            [awp-v2.knowledge.qualification :as qualification]))

(def owner "owner@example.test")
(def turn {:source-id "c:m" :conversation-id "c" :message-id "m"
           :transcript "I learned stable identifiers." :captured-at "2026-11-08T10:00:00Z"
           :status :completed :origin :owner})
(defn system [] (knowledge/open-system {:submit-task! (fn [r] {:task-id (:request-id r)})}))
(defn task [sys mode] (first (filter #(= mode (:mode %)) (knowledge/tasks sys owner "c:m"))))
(defn result [sys mode status suggestions]
  (knowledge/ingest-worker-result! sys owner
    {:result-id (str "result-" mode) :task-id (:task-id (task sys mode))
     :source-id "c:m" :mode mode :status status :suggestions suggestions
     :model "pinned-model" :processor-version "v1"}))

(deftest extraction-01-independent-processing-outcomes
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (is (= :failed (:status (result sys :journal :failed []))))
      (is (= :completed (:status (result sys :classification :completed
                                         [{:item-id "candidate" :kind :assertion :text "Stable identifiers" :evidence "stable identifiers"}]))))
      (is (= #{:failed :completed} (set (map :status (knowledge/tasks sys owner "c:m")))))
      (is (= "c:m" (:source-id (knowledge/item sys owner "candidate"))))
      (finally (knowledge/close-system! sys)))))

(deftest extraction-02-empty-outcomes-visible
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner (assoc turn :transcript "Check the logs."))
      (is (= :completed (:status (result sys :journal :completed []))))
      (is (= :completed (:status (result sys :classification :completed []))))
      (is (empty? (knowledge/profile sys owner)))
      (finally (knowledge/close-system! sys)))))

(deftest extraction-03-replay-and-conflict
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (let [x (result sys :journal :completed [{:item-id "journal" :kind :journal :text "Stable identifiers" :evidence "stable identifiers"}])]
        (is (= :completed (:status x)))
        (is (= :completed (:status (result sys :journal :completed [{:item-id "journal" :kind :journal :text "Stable identifiers" :evidence "stable identifiers"}]))))
        (is (= :conflict (:error (result sys :journal :completed []))))
        (is (= 1 (count (knowledge/suggestions sys owner)))))
      (finally (knowledge/close-system! sys)))))

(deftest extraction-04-unsupported-output-refused
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (is (= :incorrect (:error (result sys :journal :completed [{:item-id "x" :kind :journal :text "Invented" :evidence "not in source"}]))))
      (is (empty? (knowledge/suggestions sys owner)))
      (finally (knowledge/close-system! sys)))))

(deftest extraction-05-daily-source-match
  (let [sys (system)
        source {:source-ref "c:m" :speaker :owner :captured-at "2026-11-08T10:00:00Z" :transcript "I learned stable identifiers."}]
    (try
      (let [id (:account-id (knowledge/daily-request! sys owner {:journal-date "2026-11-08" :time-zone "Europe/Helsinki" :sources [source]}))]
        (is (= :conflict (:error (knowledge/ingest-daily-worker-result! sys owner
                                    {:account-id id :task-id (:task-id (knowledge/account sys owner id))
                                     :result-id "daily-1" :model "pinned-model" :processor-version "v1" :title "Day" :story "A day."
                                     :sources [(assoc source :transcript "different")]}))))
        (is (= :pending (:status (knowledge/account sys owner id)))))
      (finally (knowledge/close-system! sys)))))

(deftest projection-01-repair-preserves-history
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (result sys :journal :completed [{:item-id "journal" :kind :journal :text "Original" :evidence "learned"}])
      (result sys :classification :completed [{:item-id "candidate" :kind :assertion :text "Stable identifiers" :evidence "stable identifiers"}])
      (knowledge/review! sys owner "journal" {:action-id "edit" :expected-revision 0 :decision :corrected :text "Correction"})
      (knowledge/review! sys owner "candidate" {:action-id "accept" :expected-revision 0 :decision :accepted})
      (let [a (projection/owner-view sys owner)]
        (is (= a (projection/owner-view sys owner)))
        (is (= 1 (count (:accepted a))))
        (is (= 1 (count (get-in a [:items "journal" :reviews])))))
      (finally (knowledge/close-system! sys)))))

(deftest projection-02-owner-isolation
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (result sys :journal :completed [{:item-id "private" :kind :journal :text "Private" :evidence "learned"}])
      (is (empty? (:items (projection/owner-view sys "other@example.test"))))
      (is (= :not-found (:error (knowledge/review! sys "other@example.test" "private" {:action-id "x" :expected-revision 0 :decision :accepted}))))
      (finally (knowledge/close-system! sys)))))

(deftest projection-03-orc-owner-scope-review-and-replay
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (result sys :journal :completed [{:item-id "private" :kind :journal :text "Stable identifiers" :evidence "stable identifiers"}])
      (is (empty? (orc/owner-concepts sys owner)))
      (knowledge/review! sys owner "private" {:action-id "accept" :expected-revision 0 :decision :accepted})
      (is (= 1 (:item-count (orc/sync-owner! sys owner))))
      (is (= 1 (count (orc/owner-concepts sys owner))))
      (is (empty? (orc/owner-concepts sys "other@example.test")))
      (knowledge/review! sys owner "private" {:action-id "edit" :expected-revision 1 :decision :corrected :text "Edited memory"})
      (orc/sync-owner! sys owner)
      (is (empty? (orc/owner-concepts sys owner)))
      (knowledge/review! sys owner "private" {:action-id "reaccept" :expected-revision 2 :decision :accepted})
      (orc/sync-owner! sys owner)
      (is (= "Edited memory" (:label (first (orc/owner-concepts sys owner)))))
      (orc/sync-owner! sys owner)
      (is (= 1 (count (orc/owner-concepts sys owner))))
      (knowledge/review! sys owner "private" {:action-id "reject" :expected-revision 3 :decision :rejected})
      (orc/sync-owner! sys owner)
      (is (empty? (orc/owner-concepts sys owner)))
      (finally (knowledge/close-system! sys)))))

(deftest projection-04-orc-model-candidates-stay-separate-from-reviewed-view
  (let [sys (system)
        call (atom nil)
        appended (atom nil)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (is (thrown? Exception (orc/explore-source-candidates! sys owner "c:m" "pinned-model")))
      (with-redefs [ontology/build-ontology-from-sources
                    (fn [_ params] (reset! call params) {:total-concepts 2 :events [:synthetic]})
                    es/append (fn [_ params] (reset! appended params) {:status :accepted})]
        (is (= 2 (:total-concepts (orc/explore-source-candidates! (assoc sys :orc-runtime-ready? true) owner "c:m" "pinned-model")))))
      (is (= (:tenant-id sys) (:tenant-id @appended)))
      (is (= #{[:ontology (orc/candidate-ontology-id owner "c:m")]}
             (get-in @appended [:cas :tags])))
      (is (= "text" (get-in @call [:sources 0 :type])))
      (is (= (:transcript turn) (get-in @call [:sources 0 :content])))
      (is (not= (orc/ontology-id owner) (get-in @call [:config :ontology-id])))
      (is (empty? (orc/owner-concepts sys owner)))
      (is (thrown? Exception (orc/explore-source-candidates! (assoc sys :orc-runtime-ready? true) "other@example.test" "c:m" "pinned-model")))
      (finally (knowledge/close-system! sys)))))

(deftest projection-05-orc-candidate-owner-review-publication
  (let [sys (system)
        uri "urn:awp:test:stable-identifiers"]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (with-redefs [ontology/build-ontology-from-sources
                    (fn [ctx params]
                      (let [id (get-in params [:config :ontology-id])]
                        (ontology/create-ontology! ctx {:command/id id :name (get-in params [:config :name]) :scope :custom})
                        (ontology/create-concept! ctx id {:uri uri :label "Stable identifiers matter"
                                                          :description "Source-backed candidate" :scope :custom
                                                          :provenance {:kind :source-extracted}})
                        (ontology/create-concept! ctx id {:uri "urn:awp:test:retries" :label "Retries"
                                                          :description "Candidate parent" :scope :custom
                                                          :provenance {:kind :source-extracted}})
                        (ontology/create-relationship! ctx {:source-ontology-id id :target-ontology-id id
                                                             :source-uri uri :target-uri "urn:awp:test:retries"
                                                             :predicate "skos:broader"})
                        {:ontology-id id :total-concepts 2}))]
        (is (= 2 (:total-concepts (orc/explore-source-candidates! (assoc sys :orc-runtime-ready? true) owner "c:m" "pinned-model")))))
      (is (= :reused (:status (orc/explore-source-candidates! (assoc sys :orc-runtime-ready? true) owner "c:m" "pinned-model"))))
      (is (contains? (:broader (ontology/get-concept-by-uri sys (orc/candidate-ontology-id owner "c:m") uri))
                     "urn:awp:test:retries"))
      (is (empty? (orc/owner-concepts sys owner)))
      (is (= :incorrect (:error (orc/stage-exploration-candidates! sys owner "c:m" "pinned-model"
                                                                 [{:uri uri :evidence "invented"}]))))
      (is (= :completed (:status (orc/stage-exploration-candidates! sys owner "c:m" "pinned-model"
                                                                  [{:uri uri :evidence "stable identifiers"}]))))
      (let [item (first (knowledge/suggestions sys owner))]
        (is (= uri (:candidate-uri (knowledge/item sys owner (:item-id item)))))
        (is (empty? (orc/owner-concepts sys owner)))
        (knowledge/review! sys owner (:item-id item)
                           {:action-id "accept-orc" :expected-revision 0 :decision :accepted})
        (orc/sync-owner! sys owner)
        (is (= uri (get-in (first (orc/owner-concepts sys owner)) [:provenance :candidate-uri])))
        (is (= "pinned-model" (get-in (first (orc/owner-concepts sys owner)) [:provenance :model])))
        (is (= "c:m" (get-in (first (orc/owner-concepts sys owner)) [:provenance :source-id])))
        (is (empty? (:broader (first (orc/owner-concepts sys owner)))))
        (is (empty? (orc/owner-concepts sys "other@example.test"))))
      (finally (knowledge/close-system! sys)))))

(deftest projection-06-orc-local-runtime-starts-and-stops
  (let [sys (system)
        calls (atom [])]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (with-redefs [router/register! (fn [& _] (swap! calls conj :provider))
                    control-plane/start (fn [_] (swap! calls conj :start) :control)
                    control-plane/stop (fn [_] (swap! calls conj :stop))
                    orc/explore-source-candidates! (fn [& _] (swap! calls conj :explore) {:total-concepts 1})]
        (is (= 1 (:total-concepts (orc/explore-local-source-candidates! sys owner "c:m" "pinned-model")))))
      (is (= [:provider :start :explore :stop] @calls))
      (finally (knowledge/close-system! sys)))))

(deftest projection-07-orc-deterministic-rdf-build-persists-scoped-taxonomy
  (let [sys (system)
        id (orc/candidate-ontology-id owner "c:m")
        rdf (str "<urn:awp:test:retries> <http://www.w3.org/2000/01/rdf-schema#label> \"Retries\" .\n"
                 "<urn:awp:test:identifiers> <http://www.w3.org/2000/01/rdf-schema#label> \"Identifiers\" .\n"
                 "<urn:awp:test:identifiers> <http://www.w3.org/2004/02/skos/core#broader> <urn:awp:test:retries> .\n")]
    (try
      (let [built (ontology/build-ontology-from-sources
                    sys {:sources [{:path "memory://synthetic-taxonomy.nt" :type "rdf" :content rdf}]
                         :config {:ontology-id id :name "Synthetic owner taxonomy"
                                  :base-uri "urn:awp:test:" :enable-colbert? false
                                  :enable-embeddings? false}})]
        (is (= 2 (:total-concepts built)))
        (is (seq (:events built)))
        (is (not (:cognitect.anomalies/category
                  (es/append (:event-store sys) {:tenant-id (:tenant-id sys)
                                                 :events (vec (:events built))}))))
        (let [concept (ontology/get-concept-by-uri sys id "urn:awp:test:identifiers")]
          (is (= "Identifiers" (:label concept)))
          (is (= :source-extracted (get-in concept [:provenance :kind])))
          (is (contains? (:broader concept) "urn:awp:test:retries")))
        (is (nil? (ontology/get-concept-by-uri sys (orc/ontology-id owner) "urn:awp:test:identifiers")))
        (is (empty? (orc/owner-concepts sys owner))))
      (finally (knowledge/close-system! sys)))))

(deftest projection-08-bounded-orc-sheet-candidates-require-owner-review
  (let [sys (system)
        calls (atom [])]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (with-redefs [router/register! (fn [& _] (swap! calls conj :provider))
                    control-plane/start (fn [_] (swap! calls conj :start) :control)
                    control-plane/stop (fn [_] (swap! calls conj :stop))
                    sheet/build-workflow! (fn [_ _] (swap! calls conj :sheet) (random-uuid))
                    sheet/execute (fn [_ _ inputs & _]
                                    (swap! calls conj :execute)
                                    (is (= (:transcript turn) (:source-text inputs)))
                                    {:status :success :trace-id (random-uuid)
                                     :outputs {:concepts [{:label "Stable identifiers"
                                                           :evidence "stable identifiers"}]}})]
        (let [result (orc/explore-local-source-bounded! sys owner "c:m" "pinned-model")
              candidate (first (:candidates result))]
          (is (= :candidates (:status result)))
          (is (= "Stable identifiers" (:label candidate)))
          (is (= :source-extracted
                 (get-in (ontology/get-concept-by-uri sys (:ontology-id result) (:uri candidate))
                         [:provenance :kind])))
          (is (= [candidate] (:candidates (orc/explore-local-source-bounded!
                                           sys owner "c:m" "pinned-model"))))
          (is (empty? (orc/owner-concepts sys owner)))
          (is (= :completed (:status (orc/stage-exploration-candidates!
                                      sys owner "c:m" "pinned-model"
                                      [(select-keys candidate [:uri :evidence])]))))
          (is (empty? (orc/owner-concepts sys owner)))
          (let [item (first (knowledge/suggestions sys owner))]
            (knowledge/review! sys owner (:item-id item)
                               {:action-id "accept-bounded" :expected-revision 0 :decision :accepted})
            (orc/sync-owner! sys owner)
            (is (= (:uri candidate)
                   (get-in (first (orc/owner-concepts sys owner)) [:provenance :candidate-uri])))
            (is (empty? (orc/owner-concepts sys "other@example.test"))))))
      (is (= [:provider :start :sheet :execute :stop] @calls))
      (finally (knowledge/close-system! sys)))))

(deftest projection-09-bounded-orc-sheet-refuses-ungrounded-evidence
  (let [sys (system)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (with-redefs [router/register! (fn [& _] nil)
                    control-plane/start (fn [_] :control)
                    control-plane/stop (fn [_] nil)
                    sheet/build-workflow! (fn [_ _] (random-uuid))
                    sheet/execute (fn [& _] {:status :success :trace-id (random-uuid)
                                             :outputs {:concepts [{:label "Invented"
                                                                   :evidence "absent quote"}]}})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"exact source evidence"
                              (orc/explore-local-source-bounded! sys owner "c:m" "pinned-model"))))
      (is (not (ontology/ontology-exists? sys (orc/candidate-ontology-id owner "c:m"))))
      (finally (knowledge/close-system! sys)))))

(deftest projection-10-incomplete-candidate-write-cannot-be-replayed-or-staged
  (let [sys (system)
        id (orc/candidate-ontology-id owner "c:m")
        model "pinned-model"
        name-for-model (str "AWP candidate "
                            (let [digest (.digest (java.security.MessageDigest/getInstance "SHA-256")
                                                  (.getBytes (str owner ":c:m:" model) "UTF-8"))]
                              (.formatHex (java.util.HexFormat/of) digest)))
        uri "urn:awp:test:incomplete"]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (ontology/create-ontology! sys {:command/id id :name name-for-model :scope :custom
                                      :description "Unreviewed owner source concepts; expected=2"})
      (ontology/create-concept! sys id {:uri uri :label "Stable identifiers"
                                        :description "stable identifiers" :scope :custom
                                        :provenance {:kind :source-extracted}})
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"incomplete candidate ontology"
                            (orc/explore-local-source-bounded! sys owner "c:m" model)))
      (is (= :incorrect (:error (orc/stage-exploration-candidates!
                                  sys owner "c:m" model
                                  [{:uri uri :evidence "stable identifiers"}]))))
      (finally (knowledge/close-system! sys)))))

(def snapshot {:snapshot-id "snapshot-1" :digest "digest-1"
               :resources [{:legacy-id "source-1" :owner owner :source-id "old:m" :kind :source :value "Source"}
                           {:legacy-id "review-1" :owner owner :source-id "old:m" :kind :review :value "accepted"}
                           {:legacy-id "daily-1" :owner owner :source-id "old:m" :kind :daily :value "Day"}]})

(deftest migration-01-resume-and-replay
  (let [state (migration/empty-state)
        first-pass (migration/import-snapshot state snapshot)
        again (migration/import-snapshot first-pass snapshot)]
    (is (= first-pass again))
    (is (= 3 (count (:resources again))))
    (is (= "review-1" (:legacy-id (get (:resources again) ["snapshot-1" "review-1"]))))))

(deftest migration-02-quarantine-ambiguous-provenance
  (let [bad (assoc snapshot :resources [{:legacy-id "orphan" :kind :accepted :value "Claim"}])
        state (migration/import-snapshot (migration/empty-state) bad)]
    (is (= :quarantined (:status (get (:resources state) ["snapshot-1" "orphan"]))))
    (is (empty? (migration/accepted-facts state owner)))))

(deftest migration-03-discrepancy-blocks-cutover
  (let [state (migration/import-snapshot (migration/empty-state) snapshot)
        report (migration/reconcile state (update snapshot :resources pop))]
    (is (false? (:matched? report)))
    (is (seq (:discrepancies report)))
    (is (= :source (:read-source (migration/select-import state report))))))

(deftest migration-04-rollback-restores-source
  (let [state (migration/import-snapshot (migration/empty-state) snapshot)
        report (migration/reconcile state snapshot)
        selected (migration/select-import state report)]
    (is (= :import (:read-source selected)))
    (is (= :source (:read-source (migration/rollback selected))))
    (is (= (:resources state) (:resources (migration/rollback selected))))))

(deftest qualification-01-model-gate-measures-quality
  (let [report (qualification/evaluate [{:expected-kind :journal :actual-kind :journal :expected-evidence "learned" :actual-evidence "learned"}
                                         {:expected-kind :none :actual-kind :journal :actual-evidence "check"}]
                                        {:model "m" :prompt-version "p" :corpus-version "c"})]
    (is (= 1 (:false-additions report)))
    (is (= 1 (:correct report)))
    (is (= :open (:status report)))))

(deftest qualification-02-gates-are-independent
  (is (false? (qualification/cutover-qualified? {:quality-gate :passed :migration-gate :open})))
  (is (true? (qualification/cutover-qualified? {:quality-gate :passed :migration-gate :passed}))))

(deftest migration-grain-replay-and-owner-read-switch
  (let [sys (system)]
    (try
      (is (= :accepted (:status (migration/import-into-grain! sys owner snapshot))))
      (is (= :reused (:status (migration/import-into-grain! sys owner snapshot))))
      (is (= 3 (count (migration/owner-resources sys owner "snapshot-1"))))
      (is (empty? (migration/owner-resources sys "other@example.test" "snapshot-1")))
      (is (:matched? (migration/reconcile-grain sys owner snapshot)))
      (is (= :import (:read-source (migration/select-grain-import! sys owner snapshot))))
      (is (= :source (:read-source (migration/rollback-grain-import! sys owner "snapshot-1"))))
      (finally (knowledge/close-system! sys)))))

(deftest extraction-native-scheduler-incorporates-after-admission
  (let [calls (atom [])
        sys (knowledge/open-system
              {:submit-task! (fn [request] {:task-id (:request-id request)})
               :run-worker! (fn [request]
                              (swap! calls conj (:mode request))
                              {:result-id (str "native-" (name (:mode request)))
                               :status "completed" :suggestions []
                               :model "stub-model" :processor-version "stub-v1"})})]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (is (some (fn [_] (Thread/sleep 10)
                        (and (= 2 (count @calls))
                             (every? #(= :completed (:status %)) (knowledge/tasks sys owner "c:m"))))
                (range 100)))
      (is (= #{:journal :classification} (set @calls)))
      (finally (knowledge/close-system! sys)))))

(deftest migration-selected-owner-reads-use-imported-interpretation
  (let [sys (system)
        imported {:snapshot-id "typed-1" :digest "typed-digest"
                  :resources [{:legacy-id "g|entry" :owner owner :source-id "old:m" :kind "journal"
                               :value {:graph "urn:test:journal" :subject "urn:entry" :text "Legacy learning"
                                       :sourceRefs ["old:m"]
                                       :quads [{:p {:value "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}
                                                :o {:value "https://schema.org/CreativeWork"}}
                                               {:p {:value "https://schema.org/additionalType"}
                                                :o {:value "urn:test:learning"}}]}}
                              {:legacy-id "g|fact" :owner owner :source-id "old:m" :kind "accepted"
                               :value {:graph "urn:test:accepted" :subject "urn:fact" :label "Stable identifiers"
                                       :sourceRefs ["old:m"]
                                       :quads [{:p {:value "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}
                                                :o {:value "https://schema.org/Claim"}}]}}
                              {:legacy-id "g|daily" :owner owner :source-id "old:m" :kind "daily"
                               :value {:graph "urn:test:journal" :subject "urn:daily" :text "A prior day."
                                       :sourceRefs ["old:m"]
                                       :quads [{:p {:value "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}
                                                :o {:value "urn:test:DailyJournal"}}]}}]}]
    (try
      (migration/import-into-grain! sys owner imported)
      (migration/select-grain-import! sys owner imported)
      (is (= #{"g|entry" "g|fact"} (set (map :item-id (knowledge/suggestions sys owner)))))
      (is (= ["g|fact"] (mapv :item-id (knowledge/profile sys owner))))
      (is (= "A prior day." (:story (knowledge/account sys owner "g|daily"))))
      (migration/rollback-grain-import! sys owner "typed-1")
      (is (empty? (knowledge/suggestions sys owner)))
      (finally (knowledge/close-system! sys)))))
