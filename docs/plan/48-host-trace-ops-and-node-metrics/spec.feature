Feature: Operate the local AWP v2 host bridge and telemetry privately

  Scenario: Verify the host trace viewer from the v2 pod
    Given the native trace-explainer service is loaded and healthy under Semaphore inventory
    When the Edge pod makes a status-only request through the private host bridge
    Then the trace-explainer responds without binding a public or LAN interface

  Scenario: Render a synthetic trace through v2
    Given the trace worker and S3Mock are ready in the awp-v2 namespace
    When a synthetic trace with no owner or session data is submitted
    Then the job completes and its artifact can be read through the private viewer path

  Scenario: Manage the native broker through Semaphore
    Given the broker LaunchAgent label and current health are verified
    When an exact-target restart task is run with bounded output
    Then health is verified after restart or the same exact service is restarted to restore readiness

  Scenario: Export k3d node signals
    Given Fluent Bit can read only the k3d node proc/sys paths
    When its bounded metrics are scraped by v2 Prometheus
    Then they carry system=k3d-node and remain distinct from Docker Desktop VM and macOS host metrics

  Scenario: Preserve private deployment boundaries
    Then v1 Compose remains stopped with its volumes retained
    And only localhost Semaphore is exposed as an Ingress
    And no AWP public or Tailnet route exists
