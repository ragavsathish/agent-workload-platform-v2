Feature: Remaining Linux-compatible AWP services run in k3d

  Scenario: Owner Edge and Flutter assets remain private
    Given the v2 k3d namespace contains the Pipecat Edge service
    When an operator checks deployment and service resources
    Then Edge has a ready pod and a ClusterIP service
    And no public, tailnet, NodePort, or LoadBalancer route is added
    And its Flutter web assets can be checked through a temporary local port-forward

  Scenario: Edge uses v2 internal services and qualified host services
    Given the v2 gateway, Grafana MCP, object store, and Oxigraph are ready
    When Edge calls a dependency required by the current flow
    Then it uses v2 ClusterIP DNS and scoped credentials
    And host calls use a source-confirmed endpoint with authenticated health
    And no owner identity, transcript, prompt, artifact payload, or credential is emitted in logs or metrics

  Scenario: Grafana MCP is isolated to v2
    Given the v2 OTEL-LGTM Grafana is ready
    When Grafana MCP initializes with its dedicated v2 service account
    Then its health check succeeds and it remains ClusterIP-only
    And the caller token is stored in a Kubernetes Secret

  Scenario: Stateful MLflow artifacts use v2 storage
    Given MLflow is deployed in the v2 namespace with a bound persistent backend volume
    When a qualification run logs and lists a non-sensitive artifact
    Then the artifact proxy stores it in the v2 S3Mock bucket
    And the tracking service remains ClusterIP-only

  Scenario: Legacy routes and v1 state are reconciled
    Given the v1 Compose project was stopped after v2 component qualification
    When Slice 47 records final runtime state
    Then all 13 v1 containers are stopped and named volumes are preserved
    And no v1 service is used as v2 evidence
    And Semaphore at localhost remains the only Ingress
    And full owner trace viewer and live Pi turn flows remain gated until their host dependency path is qualified
