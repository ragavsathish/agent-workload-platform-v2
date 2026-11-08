Feature: Stable owner typed-turn retry
  The authenticated owner submits typed turns through the same Grain session
  authority that records spoken turns.

  @owner-turn-001
  Scenario: Typed turn carries one stable identity across an ambiguous retry
    Given an owner submits a typed message and the response is lost
    When the owner retries the unchanged message
    Then both requests carry the same turn identity and content
    And the session authority returns the existing decision without another model effect

  @owner-turn-002
  Scenario: A completed submission releases its identity for a new command
    Given a typed submission completed successfully
    When the owner submits another message, including identical text
    Then the new command carries a different turn identity

  @owner-turn-003
  Scenario: Pending command content cannot be changed under the same identity
    Given a typed submission has an ambiguous outcome
    When the owner changes the message before resolving that submission
    Then the client refuses to reuse the pending identity for the changed content
    And it does not dispatch the changed content

  @owner-turn-004
  Scenario: The session authority refuses conflicting identity reuse
    Given the session authority has recorded a turn identity with one message payload
    When that identity is submitted with different content
    Then the owner receives a bounded conflict answer
    And no second model effect is dispatched
