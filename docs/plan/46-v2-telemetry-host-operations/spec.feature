Feature: AWP v2 telemetry and host operations

  Scenario: PromQL measures a real v2 service
    Given the dedicated v2 OTEL-LGTM collector is ready
    And an AWP v2 service emits bounded operational metrics with environment and service labels
    When an operator queries the dedicated v2 Prometheus API
    Then the returned series identify the v2 local environment and emitting service
    And the series reflect the observed service readiness or operation outcome
    And no owner, prompt, session, task, credential, or artifact data appears in labels

  Scenario: Host services are checked through Semaphore
    Given the registered native macOS Semaphore runner is active
    And the approved host inventory identifies only v2-native services
    When a read-only Semaphore task checks service status and the local k3d API
    Then the task reports bounded status without secrets or sensitive process arguments
    And v1 services remain running and unchanged

  Scenario: Host bridge is qualified independently
    Given host-runner networking and pod networking are separate
    When an explicitly approved harmless probe checks the pod-to-host route
    Then the route result is recorded independently from the runner-to-host result
    And no public or tailnet route is introduced

  Scenario: Native lifecycle task is bounded and reversible
    Given read-only inventory and host path probes have passed
    When an operator invokes a Semaphore lifecycle template
    Then it targets only the exact approved v2-native service
    And it verifies the requested state and provides a documented rollback
    And it does not stop or modify v1
