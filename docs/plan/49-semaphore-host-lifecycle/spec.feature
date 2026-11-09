Feature: Semaphore manages the v2 native broker lifecycle safely
  Semaphore uses the macos-host runner for host-native operations. The operation
  is restricted to com.sathish.pipecat-broker and never accepts a caller-selected
  service label, credential, PID, or arbitrary command.

  Scenario: Read-only preflight reports only allowlisted broker status
    Given the reviewed Semaphore project and macos-host runner
    When the broker lifecycle template runs in check mode
    Then it inspects only the exact approved LaunchAgent label
    And it checks authenticated broker health without printing credentials
    And it reports only loaded state and health status
    And it performs no host mutation

  Scenario: Restart only when no live or unresolved voice turn exists
    Given the exact broker LaunchAgent is loaded and authenticated health is 200
    And a trusted authenticated check proves there is no active or unresolved live turn
    When the operator runs the broker lifecycle template
    Then Semaphore kickstarts only com.sathish.pipecat-broker
    And it verifies the label remains loaded
    And it verifies local authenticated health is 200
    And it verifies authenticated Edge pod-to-host health is 200
    And it emits no secret, environment value, PID, or unfiltered command output

  Scenario: Safety precondition is unavailable
    Given the exact broker LaunchAgent is loaded
    And no trustworthy check can establish that a live voice turn is absent
    When the operator runs the broker lifecycle template
    Then the operation exits before kickstarting the broker
    And reports the missing safety precondition without sensitive details

  Scenario: Failed post-check restores the loaded healthy broker
    Given the exact broker LaunchAgent was loaded before the operation
    When a restart post-check fails
    Then the operation kickstarts only the exact approved label to restore service
    And verifies the label is loaded and broker health is 200
    And reports rollback verification status without sensitive details

  Scenario: Deployment boundaries remain unchanged
    Then AWP v1 Compose remains stopped and its named volumes remain retained
    And AWP v2 application and support Services remain ClusterIP
    And Semaphore remains localhost-only
    And no public or tailnet route is added
