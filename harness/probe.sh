#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/impl"
clojure -J--add-opens=java.base/java.nio=ALL-UNNAMED \
  -J--add-exports=java.base/sun.nio.ch=ALL-UNNAMED \
  -M -e '(require (quote awp-v2.tasking.api))
    (let [api (find-ns (quote awp-v2.tasking.api))
          open (ns-resolve api (quote open-system))
          submit (ns-resolve api (quote submit!))
          task (ns-resolve api (quote task))
          close (ns-resolve api (quote close-system!))
          id #uuid "c305a2e2-77e9-4dcf-93df-13e3d0e25cc2"
          system (open {:eligible-workspaces #{"project-a"}})]
      (try
        (let [admitted (submit system "owner@example.test"
                               {:request-id id :owner "owner@example.test"
                                :instruction "Check work" :workspace "project-a"})
              observed (task system "owner@example.test" id)]
          (assert (= id (:task-id admitted)))
          (assert (= id (:task-id observed)))
          (assert (= "Check work" (:instruction observed)))
          (println "public module probe OK"))
        (finally (close system))))'
