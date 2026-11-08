(ns awp-v2.deployment.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))
(defreadmodel :deployment state
  {:events #{:deployment/fact} :version 1 :schema [:map-of :string :map]}
  "Requested level, observed active services, and readiness."
  [state event]
  (let [owner (:owner event) data (:data event)]
    (update state owner
      (fn [current]
        (-> (or current {})
            (merge (case (:kind event)
                     :selected {:requested-level (:level data) :selection-id (:selection-id data)
                                :readiness :starting}
                     :observed {:active-level (:active-level data) :services (:services data)
                                :readiness (:readiness data)}
                     {}))
            (update :revision (fnil inc 0)))))))
(defn owner-state [context owner] (get (rmp/project context :deployment/state) owner))
