Feature: Recoverable owner task admission
  One owner request identity must recover one accepted durable task.

  @task-admission-007
  Scenario: Retry task creation after the accepted response is lost
    Given the owner submits an eligible task and the server accepts it
    And the accepted response is lost before the client can display it
    When the owner repeats the same bounded task request
    Then the client reuses its request identity and canonical input
    And the original task identity is returned without a second admission

  @task-admission-008
  Scenario: A deliberate new submission gets a fresh identity
    Given a task request was successfully accepted
    When the owner deliberately submits the same instruction again
    Then the client supplies a fresh request identity
    And the new accepted task is distinct from the earlier task
