Feature: Bootstrap AWP v2 through local Flux GitOps

  Scenario: Bootstrap an existing local k3d cluster without host kubectl or Helm
    Given Docker is running and k3d is installed
    And the public GitOps source is reachable
    When the operator runs the local AWP bootstrap command
    Then the script reuses the existing awp-v2-ops cluster and persistent volumes
    And installs only the pinned Flux controllers and source synchronization objects
    And Flux reconciles the explicitly listed AWP v2 Kubernetes manifests
    And every AWP Service remains ClusterIP
    And no public or tailnet AWP route is created

  Scenario: Refuse to claim GitOps readiness when the public source is unavailable
    Given the public repository or required source revision is unavailable
    When the operator runs the local AWP bootstrap command
    Then the script reports the exact missing setup input
    And it does not report Flux reconciliation as successful
    And it does not print private key material
