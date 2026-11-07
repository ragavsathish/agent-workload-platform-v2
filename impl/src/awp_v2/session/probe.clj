(ns awp-v2.session.probe
  (:require [awp-v2.session.api :as session]
            [clojure.java.io :as io]))

(def owner "probe-owner@example.test")

(defn -main [mode dir]
  (let [tool-env (atom {:identity "persistent-voice-vm"})
        driver-calls (atom [])
        system (session/open-system
                 {:storage-dir dir :tool-environment tool-env
                  :turn-driver (fn [turn] (swap! driver-calls conj turn))})]
    (try
      (case mode
        "create"
        (let [id (:id (session/fresh! system owner "probe-fresh"))
              accepted (session/submit! system owner
                         {:turn-id "probe-turn" :origin :voice
                          :messages [{:role "user" :content "session proof"}]})]
          (assert (= :accepted (:status accepted)))
          (assert (= id (:conversation-id (first @driver-calls))))
          (assert (= :completed (:status (session/settle! system owner
                                           "probe-turn" :completed "persisted answer"))))
          (spit (io/file dir "selection-id") id))
        "verify"
        (let [id (slurp (io/file dir "selection-id"))
              selected (session/selection system owner)
              history (session/history system owner id)
              live (session/transcript system owner)]
          (assert (= id (:id selected)))
          (assert (= ["session proof" "persisted answer"]
                     (mapv :text (:entries history))))
          (assert (= (:entries history) (:entries live)))
          (assert (empty? @driver-calls))
          (assert (= {:identity "persistent-voice-vm"} @tool-env))
          (println "public session restart probe OK"))
        (throw (ex-info "unknown probe mode" {:mode mode})))
      (finally (session/close-system! system)))))
