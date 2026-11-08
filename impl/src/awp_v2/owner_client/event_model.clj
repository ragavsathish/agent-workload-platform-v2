(ns awp-v2.owner-client.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :owner-client
  {:description "Private owner workspace over session and task services."
   :screens
   {:owner-client/workspace
    {:description "Conversation, task, context, and safe presentation workspace."
     :queries #{:session/selection :session/catalog :session/history
                :session/transcript :session/context
                :tasking/list :tasking/task :tasking/progress :tasking/artifact}
     :commands #{:session/fresh :session/resume :session/submit :session/stop
                 :session/compact :session/attach :session/detach
                 :session/voice-ended :tasking/submit-task :tasking/abort-task}
     :grain/allium [{:spec "specs/owner-client.allium"
                     :kind :surface :name "OwnerWorkspace"}]}}
   :flows
   {:owner-client/open-workspace
    {:description "The screen reads its owner-scoped workspace projections."
     :steps [{:from [:query :session/catalog]
              :to [:screen :owner-client/workspace]}
             {:from [:query :session/selection]
              :to [:screen :owner-client/workspace]}
             {:from [:query :session/transcript]
              :to [:screen :owner-client/workspace]}
             {:from [:query :session/context]
              :to [:screen :owner-client/workspace]}
             {:from [:query :tasking/list]
              :to [:screen :owner-client/workspace]}]}
    :owner-client/start-conversation
    {:description "The screen starts a conversation and refreshes its selection."
     :steps [{:from [:screen :owner-client/workspace]
              :to [:command :session/fresh]}
             {:from [:command :session/fresh]
              :to [:event :session/fact]}
             {:from [:event :session/fact]
              :to [:read-model :session/state]}
             {:from [:read-model :session/state]
              :to [:query :session/selection]}
             {:from [:query :session/selection]
              :to [:screen :owner-client/workspace]}]}
    :owner-client/admit-task
    {:description "The screen submits work and refreshes its owner task list."
     :steps [{:from [:screen :owner-client/workspace]
              :to [:command :tasking/submit-task]}
             {:from [:command :tasking/submit-task]
              :to [:event :tasking/task-accepted]}
             {:from [:event :tasking/task-accepted]
              :to [:read-model :tasking/requests]}
             {:from [:read-model :tasking/requests]
              :to [:query :tasking/list]}
             {:from [:query :tasking/list]
              :to [:screen :owner-client/workspace]}]}}})
