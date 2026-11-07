(ns awp-v2.knowledge.orc-adapter
  "ORC is a rebuildable concept index. Grain knowledge decisions remain authoritative."
  (:require [ai.obney.orc.ontology.interface :as ontology]
            [ai.obney.orc.orc-service.interface]
            [ai.obney.orc.orc-service.interface :as sheet]
            [ai.obney.orc.orc-service.core.dsl :as dsl]
            [ai.obney.grain.control-plane.interface :as control-plane]
            [ai.obney.grain.event-store-v3.interface :as es]
            [awp-v2.knowledge.api :as knowledge]
            [awp-v2.knowledge.projection :as projection]
            [awp-v2.knowledge.read-models :as models]
            [clojure.string :as str]
            [litellm.router :as router])
  (:import [java.security MessageDigest]
           [java.util UUID]))

(defn- stable-uuid [value]
  (UUID/nameUUIDFromBytes (.getBytes (str value) "UTF-8")))

(defn- digest [value]
  (.formatHex (java.util.HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str value) "UTF-8"))))

(defn ontology-id [owner] (stable-uuid (str "awp-v2:knowledge:ontology:" owner)))
(defn candidate-ontology-id [owner source-id]
  (stable-uuid (str "awp-v2:knowledge:candidates:" owner ":" source-id)))
(defn item-uri [owner item-id]
  (str "urn:awp:v2:knowledge:item:" (digest (str owner ":" item-id))))

(defn- failed! [result]
  (when (:cognitect.anomalies/category result)
    (throw (ex-info "ORC ontology projection failed" {:result result})))
  result)

(defn- ensure-ontology! [system owner]
  (let [id (ontology-id owner)]
    (when-not (ontology/ontology-exists? system id)
      (failed! (ontology/create-ontology!
                system {:command/id id :name (str "AWP owner " (digest owner))
                        :scope :custom :description "Owner-scoped derived AWP knowledge concepts"
                        :base-uri (str "urn:awp:v2:knowledge:owner:" (digest owner))})))
    id))

(defn- concept-description [item]
  (str "kind=" (name (:kind item)) "; status=" (name (:status item))
       "; source=" (:source-id item) "; evidence=" (:evidence item)))

(defn sync-owner! [system owner]
  (let [id (ensure-ontology! system owner)
        items (-> (projection/owner-view system owner) :items)]
    (doseq [[item-id item] (sort-by key items)
            :when (and (= :accepted (:status item))
                       (not (str/blank? (str (:current-text item))))
                       (#{:journal :assertion} (:kind item)))]
      (let [uri (item-uri owner item-id)
            label (:current-text item)
            description (concept-description item)
            existing (ontology/get-concept-by-uri system id uri)]
        (if existing
          (when (or (not= label (:label existing))
                    (not= description (:description existing)))
            (failed! (ontology/update-concept!
                       system id (:id existing)
                       {:label label :description description}
                       {:command/id (stable-uuid (str uri ":" label ":" description))})))
          (failed! (ontology/create-concept!
                    system id {:command/id (stable-uuid uri)
                               :uri uri :label label :description description
                               :scope :custom
                               :provenance {:kind :source-extracted
                                            :owner-hash (digest owner)
                                            :source-id (:source-id item)
                                            :item-id item-id
                                            :candidate-uri (:candidate-uri item)
                                            :result-id (:result-id item)
                                            :model (:model item)
                                            :processor-version (:processor-version item)}})))))
    {:ontology-id id :item-count (count items)}))

(defn owner-concepts
  "Scoped lookup of current Grain items. Rejected items cannot reappear through ORC."
  [system owner]
  (let [{:keys [items]} (projection/owner-view system owner)
        id (ontology-id owner)]
    (if-not (ontology/ontology-exists? system id)
      []
      (->> items
           (filter (fn [[_ item]] (= :accepted (:status item))))
           (sort-by key)
           (keep (fn [[item-id item]]
                   (when-let [concept (ontology/get-concept-by-uri system id (item-uri owner item-id))]
                     (assoc concept :awp-status (:status item) :awp-source-id (:source-id item)))))
           vec))))

(defn explore-source-candidates!
  "Explicit, gated ORC model exploration. Its separate ontology is never a published review view."
  [system owner source-id model]
  (let [source (get-in (models/owner-state system owner) [:sources source-id])
        id (candidate-ontology-id owner source-id)
        candidate-name (str "AWP candidate " (digest (str owner ":" source-id ":" model)))]
    (when-not (and (:orc-runtime-ready? system) source (= source-id (:source-id source))
                   (string? model) (not (str/blank? model)))
      (throw (ex-info "owned source, model, and ORC runtime required" {:source-id source-id})))
    (if-let [existing (ontology/get-ontology system id)]
      (if (= candidate-name (:name existing))
        {:status :reused :ontology-id id
         :total-concepts (count (ontology/get-concepts system {:ontology-id id}))}
        (throw (ex-info "candidate ontology model conflicts with prior build" {:source-id source-id})))
      (let [result (ontology/build-ontology-from-sources
                     system {:sources [{:type "text" :content (:transcript source)
                                        :source-ref source-id}]
                             :config {:ontology-id id :name candidate-name
                                      :base-uri (str "urn:awp:v2:knowledge:candidate:" (digest (str owner ":" source-id)) ":")
                                      :domain "Owner knowledge journal and assertion candidates"
                                      :model model :enable-colbert? false :enable-embeddings? false}})
            events (vec (:events result))]
        (if (seq events)
          (failed! (es/append (:event-store system)
                              {:tenant-id (:tenant-id system) :events events
                               :cas {:tags #{[:ontology id]}
                                     :predicate-fn (fn [prior] (empty? (into [] prior)))}}))
          (when-not (ontology/ontology-exists? system id)
            (throw (ex-info "ORC builder returned no persisted ontology" {:source-id source-id}))))
        (assoc (select-keys result [:ontology-id :build-id :total-sources :total-concepts
                                    :total-triples :duration-ms]) :status :built)))))

(defn explore-local-source-candidates!
  "Run ORC's sheet control plane against the local Ollama OpenAI-compatible API."
  [system owner source-id model]
  (let [source (get-in (models/owner-state system owner) [:sources source-id])]
    (when-not (and source (string? model) (not (str/blank? model)))
      (throw (ex-info "owned source and model required" {:source-id source-id})))
    (router/register! :awp-local-ollama
                      {:provider :openai :model model
                       :config {:api-base "http://127.0.0.1:11434/v1"
                                :api-key "ollama"}})
    (let [control (control-plane/start
                    {:event-store (:event-store system) :cache (:cache system)
                     :context (assoc system :llm-provider :awp-local-ollama)
                     :heartbeat-interval-ms 1000})]
      (try
        (explore-source-candidates! (assoc system :orc-runtime-ready? true) owner source-id model)
        (finally (control-plane/stop control))))))

(defn- bounded-extraction-sheet [model]
  (dsl/workflow (str "awp-owner-source-bounded-concept-extraction-v1-" (digest model))
    (dsl/blackboard
      {:source-text :string
       :concepts [:vector [:map [:label :string] [:evidence :string]]]})
    (dsl/sequence "extract-source"
      (dsl/llm "extract-concepts" :model model
        :instruction (str "Extract at most two personal knowledge concepts explicitly supported by the source. "
                          "Use concise labels. Each evidence value must be an exact substring of source-text. "
                          "Return an empty list when no grounded concept is present.")
        :reads [:source-text] :writes [:concepts]
        :retry {:max-attempts 1 :backoff-ms [200]}))))

(defn- run-local-source-bounded!
  "Run one ORC LLM behavior-tree node and persist grounded candidates in a private owner/source ontology.
   The concepts are invisible to owner-concepts until a separate Grain review accepts them."
  [system owner source-id model]
  (let [source (get-in (models/owner-state system owner) [:sources source-id])
        id (candidate-ontology-id owner source-id)
        owner-hash (digest owner)]
    (when-not (and source (= source-id (:source-id source))
                   (string? model) (not (str/blank? model)))
      (throw (ex-info "owned source and model required" {:source-id source-id})))
    (router/register! :awp-local-ollama
                      {:provider :openai :model model
                       :config {:api-base "http://127.0.0.1:11434/v1"
                                :api-key "ollama"}})
    (let [control (control-plane/start
                    {:event-store (:event-store system) :cache (:cache system)
                     :context (assoc system :llm-provider :awp-local-ollama)
                     :heartbeat-interval-ms 1000})]
      (try
        (let [sheet-id (sheet/build-workflow! system (bounded-extraction-sheet model))
              result (sheet/execute system sheet-id
                                    {:source-text (:transcript source)} :timeout-ms 150000)
              concepts (get-in result [:outputs :concepts])]
          (when-not (= :success (:status result))
            (throw (ex-info "bounded ORC extraction failed"
                            {:status (:status result) :error (:error result)
                             :trace-id (:trace-id result)})))
          (when-not (and (vector? concepts) (<= (count concepts) 2)
                         (every? (fn [{:keys [label evidence]}]
                                   (and (string? label) (not (str/blank? label))
                                        (string? evidence) (not (str/blank? evidence))
                                        (str/includes? (:transcript source) evidence))) concepts))
            (throw (ex-info "ORC candidates lack exact source evidence"
                            {:source-id source-id :trace-id (:trace-id result)})))
          (let [candidate-name (str "AWP candidate " (digest (str owner ":" source-id ":" model)))
                existing (ontology/get-ontology system id)]
            (if existing
              (when-not (= candidate-name (:name existing))
                (throw (ex-info "candidate ontology model conflict" {:source-id source-id})))
              (failed! (ontology/create-ontology!
                         system {:command/id id :name candidate-name :scope :custom
                                 :description (str "Unreviewed owner source concepts; expected=" (count concepts))
                                 :base-uri (str "urn:awp:v2:knowledge:candidate:" (digest (str owner ":" source-id)) ":")})))
            (let [candidates
                  (mapv (fn [{:keys [label evidence]}]
                          (let [uri (str "urn:awp:v2:knowledge:candidate:"
                                         (digest (str owner ":" source-id ":" model ":" label ":" evidence)))]
                            (when-not (ontology/get-concept-by-uri system id uri)
                              (failed! (ontology/create-concept!
                                         system id {:command/id (stable-uuid uri)
                                                    :uri uri :label label :description evidence
                                                    :scope :custom
                                                    :provenance {:kind :source-extracted
                                                                 :owner-hash owner-hash :source-id source-id
                                                                 :model model :trace-id (:trace-id result)}})))
                            {:uri uri :label label :evidence evidence})) concepts)]
              {:status :candidates :ontology-id id :trace-id (:trace-id result)
               :model model :candidates candidates})))
        (finally (control-plane/stop control))))))

(defn explore-local-source-bounded!
  "Idempotent bounded model exploration. A persisted candidate ontology fixes the model result for replay."
  [system owner source-id model]
  (let [source (get-in (models/owner-state system owner) [:sources source-id])
        id (candidate-ontology-id owner source-id)
        existing (ontology/get-ontology system id)]
    (when-not (and source (= source-id (:source-id source))
                   (string? model) (not (str/blank? model)))
      (throw (ex-info "owned source and model required" {:source-id source-id})))
    (if existing
      (let [expected-name (str "AWP candidate " (digest (str owner ":" source-id ":" model)))
            expected-count (some->> (:description existing)
                                    (re-find #"expected=(\d+)") second parse-long)
            concepts (ontology/get-concepts system {:ontology-id id})]
        (when-not (= expected-name (:name existing))
          (throw (ex-info "candidate ontology model conflict" {:source-id source-id})))
        (when-not (and expected-count (= expected-count (count concepts)))
          (throw (ex-info "incomplete candidate ontology requires reconciliation"
                          {:source-id source-id :expected expected-count :actual (count concepts)})))
        {:status :reused :ontology-id id :model model
         :candidates (mapv (fn [concept] {:uri (:uri concept)
                                         :label (:label concept)
                                         :evidence (:description concept)}) concepts)})
      (run-local-source-bounded! system owner source-id model))))

(defn stage-exploration-candidates!
  "Ground selected ORC concepts in exact owner evidence, then submit to Grain review."
  [system owner source-id model selections]
  (let [state (models/owner-state system owner)
        source (get-in state [:sources source-id])
        task-id (get-in state [:work source-id :classification :task-id])
        candidate-id (candidate-ontology-id owner source-id)
        candidate-ontology (ontology/get-ontology system candidate-id)
        expected-count (some->> (:description candidate-ontology)
                                (re-find #"expected=(\d+)") second parse-long)]
    (if-not (and source task-id (string? model) (not (str/blank? model))
                 (vector? selections) (seq selections)
                 (= (count selections) (count (set (map :uri selections))))
                 (= (str "AWP candidate " (digest (str owner ":" source-id ":" model)))
                    (:name candidate-ontology))
                 (or (nil? expected-count)
                     (= expected-count
                        (count (ontology/get-concepts system {:ontology-id candidate-id})))))
      {:error :incorrect}
      (let [candidates
            (mapv (fn [{:keys [uri evidence]}]
                    (let [concept (when (string? uri)
                                    (ontology/get-concept-by-uri system candidate-id uri))]
                      (when (and concept (string? evidence) (not (str/blank? evidence))
                                 (str/includes? (:transcript source) evidence)
                                 (or (nil? (get-in concept [:provenance :trace-id]))
                                     (= evidence (:description concept))))
                        {:item-id (str "orc:" (subs (digest (str owner ":" source-id ":" uri)) 0 32))
                         :kind :assertion :text (:label concept) :evidence evidence
                         :candidate-uri uri}))) selections)]
        (if (some nil? candidates)
          {:error :incorrect}
          (knowledge/ingest-worker-result!
            system owner {:result-id (str "orc:" (digest (pr-str [owner source-id model selections])))
                          :task-id task-id :source-id source-id :mode :classification
                          :status :completed :suggestions candidates
                          :model model :processor-version "orc-exploration-v1"}))))))
