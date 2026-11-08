Feature: Deployment readiness projection
  The deployment read model reports the health of the active service set.

  @deployment-readiness-001
  Scenario: Healthy services stay ready through the owner status query
    Given the selected deployment level has every required service healthy
    When the supervisor records a ready observation
    Then the owner deployment status reports ready
    And the readiness remains ready after the durable projection is reopened

  @deployment-readiness-002
  Scenario: An unhealthy required service cannot be reported ready
    Given the selected deployment level has a required service unhealthy
    When the supervisor records a ready observation
    Then the owner deployment status reports degraded

  @deployment-readiness-003
  Scenario: Starting and not-ready states remain explicit
    Given the supervisor reports that services are starting or not ready
    When the observation is stored
    Then the owner deployment status retains that reported lifecycle state
