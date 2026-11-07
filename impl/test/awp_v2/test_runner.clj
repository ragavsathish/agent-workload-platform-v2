(ns awp-v2.test-runner
  (:require [clojure.test :as t]
            [awp-v2.tasking-acceptance-test]
            [awp-v2.lifecycle-acceptance-test]
            [awp-v2.session-acceptance-test]
            [awp-v2.live-acceptance-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (t/run-tests 'awp-v2.tasking-acceptance-test
                                          'awp-v2.lifecycle-acceptance-test
                                          'awp-v2.session-acceptance-test
                                          'awp-v2.live-acceptance-test)]
    (System/exit (if (zero? (+ fail error)) 0 1))))
