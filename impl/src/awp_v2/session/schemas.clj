(ns awp-v2.session.schemas
  (:require [ai.obney.grain.event-store-v3.interface :refer [defevent]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(def command-schema [:map [:owner :string] [:data :map]])
(defschemas commands
  {:session/fresh command-schema
   :session/resume command-schema
   :session/submit command-schema
   :session/settle command-schema
   :session/observe command-schema
   :session/stop command-schema
   :session/compact-start command-schema
   :session/compact-complete command-schema
   :session/compact-uncertain command-schema
   :session/attach command-schema
   :session/detach command-schema
   :session/voice-ended command-schema})

(defevent :session/fact "One committed conversation decision or safe observation."
  {:schema [:map [:owner :string] [:kind :keyword] [:data :map]]})

(defschemas queries
  {:session/selection [:map [:owner :string]]
   :session/history [:map [:owner :string] [:conversation-id :string]]
   :session/transcript [:map [:owner :string]]
   :session/catalog [:map [:owner :string]]
   :session/context [:map [:owner :string]]
   :session/routing [:map [:owner :string]]
   :session/routing-command [:map [:owner :string] [:command-id :string]]
   :session/turn [:map [:owner :string] [:turn-id :string]]
   :session/latest-typed-turn [:map [:owner :string]]})
