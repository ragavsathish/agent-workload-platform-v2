Feature: Deployment levels
  The operator selects the available AWP capabilities without changing owner
  or work identity.

  @deployment-001
  Scenario: Omitted level selects full
    Given no deployment level is configured
    When the application starts
    Then the requested deployment level is full

  @deployment-002
  Scenario: Voice level supports conversation and refuses durable work
    Given the deployment level is voice
    When the owner submits a durable task
    Then the task is refused before acceptance
    And voice conversation remains available

  @deployment-003
  Scenario: Durable level runs approved work on the host
    Given the deployment level is durable
    And the task has an approved workspace and owner authority
    When the task is accepted
    Then it uses the host task executor

  @deployment-004
  Scenario: Full level runs approved work in isolation
    Given the deployment level is full
    And the task has an approved workspace and owner authority
    When the task is accepted
    Then it uses the isolated task executor
    And the persistent voice tool environment remains isolated

  @deployment-005
  Scenario: Transition preserves existing work identity
    Given an owner has sessions, accepted tasks, reviews, and artifacts
    When the operator changes the deployment level
    Then all existing identities and decisions are preserved
    And capabilities excluded by the new level are stopped

  @deployment-006
  Scenario: Interrupted task requires explicit recovery
    Given an accepted task is running during a level transition
    When its executor is stopped
    Then the same task is marked as requiring recovery
    And it is not automatically replayed

  @deployment-007
  Scenario: Requested level survives restart
    Given the operator selected durable
    When the application restarts
    Then durable remains the requested level
    And readiness is reconstructed from observed health

  @deployment-008
  Scenario: Missing capability prevents ready status
    Given a required service failed to start
    When deployment status is queried
    Then readiness is not ready
    And the missing capability is reported

  @deployment-009
  Scenario: Unsupported level leaves active services unchanged
    Given the deployment level is durable
    When the operator selects an unsupported level
    Then the request is refused
    And the active services remain unchanged

  @deployment-010
  Scenario: Repeating the active level is idempotent
    Given the deployment level is full
    When the operator selects full again
    Then no duplicate service or task is created
