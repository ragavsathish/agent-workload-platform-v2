(ns awp-v2.knowledge.legacy-view)

(defn- subject [resource] (get-in resource [:value :subject]))
(defn interpretation [state]
  (let [records (vec (->> (get-in state [:legacy-resources (:selected-snapshot state)]) vals
                          (filter #(= :imported (:status %)))))
        accepted-subjects (set (map subject (filter #(= "accepted" (:kind %)) records)))
        actions (->> records (filter #(= "review-action" (:kind %)))
                     (group-by #(get-in % [:value :target])))
        items (into {} (for [resource records
                             :when (#{"journal" "candidate" "accepted"} (:kind resource))
                             :let [legacy-id (:legacy-id resource)
                                   value (:value resource)
                                   history (->> (get actions (subject resource))
                                                (sort-by #(get-in % [:value :createdAt]))
                                                (mapv #(select-keys % [:legacy-id :source-id :value])))] ]
                         [legacy-id {:item-id legacy-id :owner (:owner resource) :source-id (:source-id resource)
                                     :kind (if (= "journal" (:kind resource)) :journal :assertion)
                                     :original-text (or (:text value) (:label value))
                                     :current-text (or (:text value) (:label value))
                                     :status (if (or (= "accepted" (:kind resource))
                                                     (contains? accepted-subjects (subject resource))
                                                     (= "accepted" (:status value))) :accepted :pending)
                                     :reviews history :legacy? true}]))
        accounts (into {} (for [resource records :when (= "daily" (:kind resource))]
                            [(:legacy-id resource) {:account-id (:legacy-id resource)
                                                    :owner (:owner resource) :source-refs (get-in resource [:value :sourceRefs])
                                                    :story (get-in resource [:value :text]) :status :completed :legacy? true}]))]
    {:items items :accounts accounts}))
