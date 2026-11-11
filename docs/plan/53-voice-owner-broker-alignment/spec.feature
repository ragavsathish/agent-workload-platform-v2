Feature: Voice owner identity and broker API compatibility
  The private voice Edge authenticates the Tailnet principal, preserves the
  configured Grain owner key, and calls a broker that serves the v2 contract.

  @voice-owner-broker-001
  Scenario: Map the single authenticated Tailnet owner to the persisted Grain owner
    Given the Edge accepts exactly one authenticated Tailnet owner
    And Grain and the gateway use their existing canonical owner key
    When the owner lists, attaches, or reads an agent task through the Edge
    Then the Edge validates the Tailnet principal before forwarding
    And the Edge sends the canonical Grain owner to the gateway
    And the gateway authorizes the request without changing stored Grain ownership

  @voice-owner-broker-002
  Scenario: Fail closed when Edge owner configuration is ambiguous
    Given the Edge is configured with zero or multiple allowed Tailnet owners
    When an owner-scoped gateway request is made
    Then the Edge does not forward the request to the gateway
    And the request reports an unavailable owner configuration

  @voice-owner-broker-003
  Scenario: Keep a legacy broker from masquerading as the v2 broker
    Given the configured host endpoint serves the legacy broker API
    And it does not expose the v2 task-routing and turn-status routes
    When voice readiness checks the broker contract
    Then the capability is reported unavailable
    And no synthetic successful turn or task-routing response is returned

  @voice-owner-broker-004
  Scenario: Use the v2 broker routes for Pi task routing and turn status
    Given the Edge is configured to reach the deployed v2 broker
    When it requests Pi task routing or latest-turn status
    Then the broker serves the corresponding v2 route
    And the result is scoped to the configured Grain owner

  @voice-owner-broker-005
  Scenario: Preserve the existing Grain owner during Tailnet enrollment
    Given the Grain event store may contain records under its configured owner key
    When Tailnet enrollment updates the Edge allowlist
    Then enrollment does not rewrite the Grain owner key
    And authenticated Edge requests map to that existing owner key

  @voice-owner-broker-006
  Scenario: Refuse an unauthenticated or non-owner caller
    Given a caller is not the single Tailnet owner configured at the Edge
    When the caller requests owner-scoped voice or task APIs
    Then the Edge rejects the caller before calling the gateway or broker
