Feature: Session store bootstrap and projection rebuild

  @session-bootstrap-001
  Scenario: Rebuild owner reads from durable facts after a service restart
    Given the session system is opened on durable storage
    And owner A creates and uses a conversation with a context observation
    And owner A attaches an ephemeral task route
    When the session system closes and reopens on the same storage
    Then the Grain bootstrap validates and registers session commands, facts, queries, and projections
    And owner A can read the rebuilt selection, transcript, turn, and context
    And owner A's ephemeral task route is cleared and unfinished turns are uncertain
    And owner B cannot read owner A's rebuilt state
