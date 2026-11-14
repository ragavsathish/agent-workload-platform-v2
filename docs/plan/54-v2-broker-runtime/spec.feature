Feature: Side-by-side v2 Pi and inference broker runtime
  A k3d Edge calls a v2 broker backed by v2 Pi and the existing k3d Grain
  authority while the legacy host broker and Pi services remain available.

  @v2-broker-runtime-001
  Scenario: Start v2 voice services beside the legacy host services
    Given the legacy broker and Pi services are running on their existing ports
    And a Semaphore-managed side-by-side v2 runtime has unique loopback ports
    When the v2 runtime is started
    Then its Pi and broker health checks pass on the new ports
    And the legacy process identities and health remain unchanged

  @v2-broker-runtime-002
  Scenario: Use the existing k3d Grain authority
    Given the v2 Pi service is configured with the current k3d Grain URL and owner
    When it handles a turn or task-routing request
    Then reads and writes use the existing Grain service and owner key
    And no second Grain event store is started for the side-by-side runtime

  @v2-broker-runtime-003
  Scenario: Serve the v2 Edge broker contract
    Given Edge is configured for the side-by-side v2 broker endpoint
    When Edge requests `/v1/pi/tasks/routing` or `/v1/pi/turn/latest`
    Then the v2 broker returns the defined response or a bounded upstream error
    And it does not silently fall back to the legacy broker

  @v2-broker-runtime-004
  Scenario: Keep the v2 broker private
    Given the v2 broker listens on a loopback-only host port
    When the Edge pod connects over its qualified host route
    Then the request reaches the v2 broker with the existing service credential
    And no public, LAN, NodePort, or unauthenticated endpoint is created

  @v2-broker-runtime-005
  Scenario: Refuse an unqualified Grain network path
    Given the host cannot reach the existing k3d Grain service through a reviewed private bridge
    When the side-by-side runtime is prepared
    Then Semaphore does not start a second Grain authority or mutate Grain secrets
    And Edge remains on its last known configuration until a reviewed bridge exists

  @v2-broker-runtime-006
  Scenario: Roll back the Edge broker target without stopping legacy services
    Given v2 Pi or broker readiness fails after the Edge is configured
    When the reviewed rollback is invoked through Semaphore and Flux
    Then Edge returns to its recorded prior image and broker target
    And the legacy host broker and Pi services remain available

  @v2-broker-runtime-007
  Scenario: Do not replace the legacy runtime without a quiescence proof
    Given the current broker cannot report active or uncertain voice work
    When the v2 runtime is deployed side by side
    Then no legacy service is restarted, stopped, or replaced
    And replacement remains gated until native voice quiescence is observable
