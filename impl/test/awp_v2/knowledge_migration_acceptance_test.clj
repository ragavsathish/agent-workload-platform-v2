(ns awp-v2.knowledge-migration-acceptance-test
  (:require [clojure.test :refer :all]
            [ai.obney.orc.ontology.interface :as ontology]
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
      (is (= 1 (:item-count (orc/sync-owner! sys owner))))
      (is (= 1 (count (orc/owner-concepts sys owner))))
      (is (empty? (orc/owner-concepts sys "other@example.test")))
      (knowledge/review! sys owner "private" {:action-id "edit" :expected-revision 0 :decision :corrected :text "Edited memory"})
      (orc/sync-owner! sys owner)
      (is (= "Edited memory" (:label (first (orc/owner-concepts sys owner)))))
      (orc/sync-owner! sys owner)
      (is (= 1 (count (orc/owner-concepts sys owner))))
      (knowledge/review! sys owner "private" {:action-id "reject" :expected-revision 1 :decision :rejected})
      (orc/sync-owner! sys owner)
      (is (empty? (orc/owner-concepts sys owner)))
      (finally (knowledge/close-system! sys)))))

(deftest projection-04-orc-model-candidates-stay-separate-from-reviewed-view
  (let [sys (system)
        call (atom nil)]
    (try
      (knowledge/ingest-turn! sys owner turn)
      (with-redefs [ontology/build-ontology-from-sources
                    (fn [_ params] (reset! call params) {:total-concepts 2})]
        (is (= 2 (:total-concepts (orc/explore-source-candidates! sys owner "c:m" "pinned-model")))))
      (is (= "text" (get-in @call [:sources 0 :type])))
      (is (= (:transcript turn) (get-in @call [:sources 0 :content])))
      (is (not= (orc/ontology-id owner) (get-in @call [:config :ontology-id])))
      (is (empty? (orc/owner-concepts sys owner)))
      (is (thrown? Exception (orc/explore-source-candidates! sys "other@example.test" "c:m" "pinned-model")))
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
                               :value {:subject "urn:entry" :text "Legacy learning" :sourceRefs ["old:m"]}}
                              {:legacy-id "g|fact" :owner owner :source-id "old:m" :kind "accepted"
                               :value {:subject "urn:fact" :label "Stable identifiers" :sourceRefs ["old:m"]}}
                              {:legacy-id "g|daily" :owner owner :source-id "old:m" :kind "daily"
                               :value {:subject "urn:daily" :text "A prior day." :sourceRefs ["old:m"]}}]}]
    (try
      (migration/import-into-grain! sys owner imported)
      (migration/select-grain-import! sys owner imported)
      (is (= #{"g|entry" "g|fact"} (set (map :item-id (knowledge/suggestions sys owner)))))
      (is (= ["g|fact"] (mapv :item-id (knowledge/profile sys owner))))
      (is (= "A prior day." (:story (knowledge/account sys owner "g|daily"))))
      (migration/rollback-grain-import! sys owner "typed-1")
      (is (empty? (knowledge/suggestions sys owner)))
      (finally (knowledge/close-system! sys)))))
