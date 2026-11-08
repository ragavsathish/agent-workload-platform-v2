(ns awp-v2.knowledge.taxonomy)

(def ^:private base
  [{:category-id "knowledge-item" :parent-id nil :definition "A source-backed owner knowledge item."}
   {:category-id "journal" :parent-id "knowledge-item" :definition "A durable owner reflection."}
   {:category-id "assertion" :parent-id "knowledge-item" :definition "A classified owner statement or topic."}
   {:category-id "thought" :parent-id "journal" :definition "A meaningful thought."}
   {:category-id "idea" :parent-id "journal" :definition "A proposed possibility."}
   {:category-id "question" :parent-id "journal" :definition "A durable genuine question."}
   {:category-id "decision" :parent-id "journal" :definition "An explicit owner decision."}
   {:category-id "goal" :parent-id "journal" :definition "An explicit owner goal."}
   {:category-id "learning" :parent-id "journal" :definition "An explicit new understanding."}
   {:category-id "unclassified" :parent-id "journal" :definition "A meaningful reflection without a narrower kind."}
   {:category-id "conversation-topic" :parent-id "assertion" :definition "A subject discussed; not evidence of skill."}
   {:category-id "skill-evidence" :parent-id "assertion" :definition "An explicit owner skill statement."}
   {:category-id "profile-update" :parent-id "assertion" :definition "An explicit biographical update."}
   {:category-id "job-title" :parent-id "profile-update" :definition "An explicit professional title."}
   {:category-id "home-location" :parent-id "profile-update" :definition "An explicit home location."}
   {:category-id "credential" :parent-id "profile-update" :definition "An explicit educational or occupational credential."}
   {:category-id "employment" :parent-id "profile-update" :definition "An explicit employment statement."}])

(defn categories [version]
  (case version
    "personal-v1" (mapv #(assoc % :version-id version) base)
    "personal-v2" (mapv #(assoc % :version-id version)
                        (conj base {:category-id "reflection" :parent-id "journal"
                                    :definition "An owner-reviewed general reflection."}))
    []))

(defn category [version id]
  (first (filter #(= id (:category-id %)) (categories version))))

(defn publishable? [version id]
  (let [entry (category version id)]
    (and entry (not (#{"knowledge-item" "journal" "assertion"} id)))))
