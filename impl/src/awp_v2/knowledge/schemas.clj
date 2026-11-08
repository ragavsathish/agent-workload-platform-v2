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
   :knowledge/legacy-rollback command-schema
   :knowledge/taxonomy-proposal command-schema
   :knowledge/taxonomy-review command-schema
   :knowledge/taxonomy-map command-schema
   :knowledge/taxonomy-model-outcome command-schema
   :knowledge/taxonomy-relation command-schema
   :knowledge/taxonomy-relation-review command-schema
   :knowledge/quality-case command-schema
   :knowledge/quality-review command-schema
   :knowledge/quality-run command-schema
   :knowledge/quality-holdout command-schema
   :knowledge/quality-verdict command-schema})
(defevent :knowledge/fact "One committed source, extraction, review, or account fact."
  {:schema [:map [:owner :string] [:kind :keyword] [:data :map]]})
(defschemas queries
  {:knowledge/tasks [:map [:owner :string] [:source-id :string]]
   :knowledge/suggestions [:map [:owner :string]]
   :knowledge/item [:map [:owner :string] [:item-id :string]]
   :knowledge/profile [:map [:owner :string]]
   :knowledge/account [:map [:owner :string] [:account-id :string]]
   :knowledge/accounts [:map [:owner :string]]
   :knowledge/taxonomy-item [:map [:owner :string] [:proposal-id :string]]
   :knowledge/taxonomy-proposals [:map [:owner :string]]
   :knowledge/taxonomy-current [:map [:owner :string]]
   :knowledge/taxonomy-relations [:map [:owner :string]]
   :knowledge/taxonomy-outcomes [:map [:owner :string]]
   :knowledge/quality-cases [:map [:owner :string]]
   :knowledge/quality-run-status [:map [:owner :string] [:run-id :string]]})
