Feature: Stage the Grain control plane in local k3d

  @k3d-grain-001
  Scenario: Grain is reachable only inside the cluster
    Given the local AWP v2 namespace exists
    When the Grain pod is ready
    Then its HTTP server listens on the pod interface
    And its Kubernetes Service is ClusterIP only
    And no public, LAN, tailnet, or host-port route is created

  @k3d-grain-002
  Scenario: Candidate Grain stores survive a pod restart
    Given Grain uses a host-backed persistent volume in the dedicated k3d cluster
    When the pod is restarted
    Then the same candidate SQLite and LMDB storage directory is mounted
    And the health endpoint responds without issuing a Grain command

  @k3d-grain-003
  Scenario: Native owner state is not replaced by an empty candidate
    Given the native Grain process currently owns non-empty session and deployment event stores
    When the k3d candidate is staged
    Then it uses a separate candidate directory
    And the native stores are not attached to the pod while live
    And no owner-continuity or cutover claim is made
    And cutover requires a separately qualified snapshot/replay migration

  @k3d-grain-004
  Scenario: Grain native storage libraries work on the deployment architecture
    Given Grain's source dependencies remain pinned
    When the k3d image starts on Linux arm64
    Then SQLite and LMDB native libraries load successfully
    And host macOS Grain still resolves a compatible native library
