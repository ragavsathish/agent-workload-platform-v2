(ns awp-v2.knowledge.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(def command-schema [:map [:owner :string] [:data :map]])
(defschemas commands
  {:knowledge/intake command-schema
   :knowledge/submission command-schema
   :knowledge/submission-uncertain command-schema
   :knowledge/outcome command-schema
   :knowledge/review command-schema
   :knowledge/daily-request command-schema
   :knowledge/daily-submission command-schema
   :knowledge/daily-uncertain command-schema
   :knowledge/daily-result command-schema
   :knowledge/legacy-snapshot command-schema
   :knowledge/legacy-resource command-schema
   :knowledge/legacy-switch command-schema
   :knowledge/legacy-rollback command-schema})
(defevent :knowledge/fact "One committed source, extraction, review, or account fact."
  {:schema [:map [:owner :string] [:kind :keyword] [:data :map]]})
(defschemas queries
  {:knowledge/tasks [:map [:owner :string] [:source-id :string]]
   :knowledge/suggestions [:map [:owner :string]]
   :knowledge/item [:map [:owner :string] [:item-id :string]]
   :knowledge/profile [:map [:owner :string]]
   :knowledge/account [:map [:owner :string] [:account-id :string]]
   :knowledge/accounts [:map [:owner :string]]})
