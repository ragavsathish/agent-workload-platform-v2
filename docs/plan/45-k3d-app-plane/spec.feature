Feature: Isolated AWP v2 app plane in k3d

  Scenario: Operators inspect services locally
    Given the dedicated AWP v2 k3d cluster is ready
    When an operator uses Semaphore or a loopback-only port-forward
    Then AWP APIs are not published beyond the local machine
    And no public or tailnet route exists

  Scenario: Gateway serves Grain-backed owner APIs
    Given gateway and Grain are ready in namespace awp-v2
    When a pod-namespace probe calls health and an owner read API
    Then gateway responds successfully
    And the read uses Grain without appending deployment observations

  Scenario: Support services expose private APIs and durable data
    Given Gitea, S3-compatible storage, and Oxigraph are ready
    When an in-cluster client checks their APIs and restarts S3Mock and Oxigraph
    Then each API responds successfully
    And test data in S3Mock and Oxigraph remains available

  Scenario: Host services remain operator managed
    Given Pi, MLX, and Gondolin are macOS-native
    When a host lifecycle or status operation is requested
    Then Semaphore's registered macOS runner is the operations path
