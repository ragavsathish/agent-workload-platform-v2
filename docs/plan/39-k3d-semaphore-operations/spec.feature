Feature: Local Kubernetes deployment operations through Semaphore UI
  Semaphore provides the operator interface for deploying and observing AWP v2
  while the owner continues to use the Flutter voice workspace.

  @k3d-001
  Scenario: Operator UI is available only on the local host
    Given the dedicated local k3d cluster is running
    When the operator opens Semaphore UI
    Then the operator can authenticate and inspect deployment task history
    And the UI is reachable only through a loopback host port
    And no public or tailnet entry is created

  @k3d-002
  Scenario: Semaphore controls AWP through reviewed Ansible tasks
    Given the operator has selected the pinned AWP v2 repository revision
    When the operator starts an approved deployment task in Semaphore
    Then Semaphore runs the corresponding reviewed Ansible playbook
    And the task records its inputs, result, and output in Semaphore
    And the task cannot read or replace Grain-owned event state directly

  @k3d-002a
  Scenario: Kubernetes-compatible AWP services run in k3d
    Given the dedicated AWP namespace is deployed in the local k3d cluster
    When the deployment becomes ready
    Then Grain owns sessions, tasks, knowledge, song, incident, and deployment event models in the cluster
    And the gateway and Edge serve the owner API and Flutter client from the cluster
    And Gitea, artifact storage, Oxigraph, Grafana MCP, Prometheus, and OTLP are deployed as AWP support services in the cluster
    And Grain's SQLite and LMDB storage identities persist across pod and cluster restarts
    And native host Pi and broker services reach cluster support services through loopback-only host ports
    And k3d pods reach host-only APIs only through an authenticated internal bridge

  @k3d-002b
  Scenario: macOS-only runtime services are managed by Semaphore
    Given the native Semaphore runner is registered on the macOS host
    When the operator runs the reviewed AWP host-service template
    Then Pi voice, the MLX inference broker, and the Gondolin task scheduler are checked or restarted on macOS
    And the run reports bounded health and process outcomes without exposing credentials or owner content
    And the host APIs remain loopback-bound behind a narrowly scoped cluster bridge

  @k3d-003
  Scenario: Runner host networking reaches the intended host boundary
    Given the native macOS Semaphore runner uses the host network
    When the runner performs a harmless network preflight
    Then it proves the configured routes to AWP v2 and the k3d API
    And a failed route blocks deployment and rollback tasks
    And the result names the actual macOS, Docker Desktop, and k3d network boundaries
    And it does not treat runner host networking as a route from AWP pods to macOS loopback

  @k3d-004
  Scenario: Owner voice workspace remains the AWP frontend
    Given the Semaphore operations interface is installed
    When the owner opens the AWP voice workspace
    Then the existing Flutter client is still served through the AWP Edge route
    And Semaphore is not substituted for owner voice, conversation, or knowledge views

  @k3d-005
  Scenario: PromQL distinguishes v2 application telemetry
    Given AWP v2 services export their payload-free metrics to the configured collector
    When the operator queries `sum by (service_name) (pipecat_operation_total{deployment_environment_name="awp-v2-local"})`
    Then the result contains the v2 instrumented service names
    And it includes the k3d and macOS host services
    And it does not count series from the prior v1 deployment

  @k3d-006
  Scenario: Old deployment remains available until cutover evidence passes
    Given the v1 owner service is still authoritative
    When k3d services and macOS host services are staged and owner, task, and readiness checks run
    Then v1 remains available until those checks pass
    And a failed check leaves the v2 candidate unexposed
    And the old service is stopped only after the reviewed cutover gate passes

  @k3d-007
  Scenario: Cluster restart preserves AWP event and owner state
    Given AWP v2 has recorded an owner turn and an accepted task
    When the local k3d cluster or Semaphore UI is restarted
    Then the Grain event state and AWP state directory retain their identities
    And the same turn and task remain readable through the AWP owner route

