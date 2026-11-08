Feature: Owner controlled durable task routing
  A selected conversation can temporarily route following turns to an owned
  durable task while the server coordinates both sides of that route.

  @routing-001
  Scenario: Attach through the owner command boundary
    Given an active selected conversation and an eligible task owned by the owner
    When the owner attaches the task through the workspace
    Then the recorded route and live task session use the same identity
    And a following turn is routed to that task without changing selection

  @routing-002
  Scenario: Retrying a lost attachment response converges
    Given an attachment command completed but its response was lost
    When the owner retries that command with the same identity and task
    Then it returns the attached task without creating another transition
    When the command identity is reused for a different task
    Then the conflict is refused and the existing route remains active

  @routing-003
  Scenario: A rejected or failed attachment has no successful route
    Given a task is foreign, terminal, or its task connection cannot attach
    When the owner requests task attachment
    Then the request is refused or reported uncertain
    And no turn is silently sent to a different task

  @routing-004
  Scenario: Detach returns following turns to the selected conversation
    Given an owned task is attached to the selected conversation
    When the owner detaches it
    Then the live task session and recorded route both report detached
    And the next turn returns to the selected conversation

  @routing-005
  Scenario: Selection and voice shutdown release the live task route
    Given an owned task is attached to the selected conversation
    When the owner changes selection or the voice service closes
    Then the temporary task route is released
    And the durable task retains its own identity and history

  @routing-006
  Scenario: Restart blocks a turn until routing is reconciled
    Given an attachment existed before the voice service restarts
    When the service recovers without the previous live task connection
    Then it clears the stale route or reports routing as uncertain
    And it does not dispatch another turn until both routing views agree
    When the owner explicitly reconciles the route
    Then both routing views report detached
