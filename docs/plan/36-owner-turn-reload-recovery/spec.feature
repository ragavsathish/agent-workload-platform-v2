Feature: Recover the owner typed-turn receipt after reload
  The durable session stream preserves a typed turn identity and outcome across
  owner-client reload without replaying an external model effect.

  @owner-turn-recovery-001
  Scenario: Reload recovers a completed answer from the durable transcript
    Given the typed turn completed but its HTTP response was lost
    When the owner workspace reloads
    Then the selected conversation transcript contains the completed answer
    And no new model effect is dispatched
    And the next submission receives a fresh turn identity

  @owner-turn-recovery-002
  Scenario: Reload recovers an uncertain turn without replay
    Given a typed turn was accepted and the service restarted before settlement
    When the owner workspace reloads and observes its turn receipt
    Then the receipt identifies the same turn as uncertain
    And the original text remains visible in the owner transcript
    And changed content is blocked until the owner resolves the held state
    And no model effect is replayed

  @owner-turn-recovery-003
  Scenario: Turn receipt is scoped to the selected conversation and owner
    Given another owner or an unselected conversation has a newer typed turn
    When the owner queries the selected conversation's latest typed-turn receipt
    Then no other owner's or conversation's identity or status is returned

  @owner-turn-recovery-004
  Scenario: Attached-task turn is not selected as an orchestrator typed receipt
    Given the owner routed a typed turn to an attached task
    When the owner queries the selected conversation's latest typed-turn receipt
    Then the task turn is excluded from that receipt
    And task history remains owned by the task
