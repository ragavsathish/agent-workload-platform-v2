Feature: Reversible continuity of private owner knowledge
  A frozen source can be proved equivalent to an isolated shadow before any live read choice.

  @continuity-01
  Scenario: Source drift is detected by content
    Given a frozen source snapshot and a current source with the same total count
    When one source statement changes
    Then the current source fails the frozen digest check
    And no live read choice changes

  @continuity-02
  Scenario: Every resource has an accounted disposition
    Given a frozen inventory with mapped and unlinked metadata resources
    When disposition is reviewed
    Then every source resource is mapped, quarantined, or explicitly excluded with evidence
    And no unlinked metadata becomes accepted knowledge

  @continuity-03
  Scenario: Unresolved metadata blocks the gate
    Given an unlinked metadata resource without an approved disposition
    When shadow qualification runs
    Then its identity and reason remain in the private discrepancy ledger
    And the live switch gate stays open

  @continuity-04
  Scenario: Shadow import resumes without duplication
    Given an isolated target and a frozen mapped package
    When import stops midway, resumes, and runs again
    Then each resource and review action occurs once
    And owner reads still use the prior source

  @continuity-05
  Scenario: Category and review parity is checked
    Given source suggestions with ordered corrections and accepted facts
    When the shadow owner view is reconciled with the prior view
    Then each category, review sequence, current status, and accepted fact agrees
    And a missing or reordered action is reported by class

  @continuity-06
  Scenario: Daily versions have honest parity
    Given daily accounts with a date, time zone, and source bundle
    When shadow and prior owner views are compared
    Then selected versions and every extant historical version agree
    And a version lost by a prior overwrite is reported as unknown

  @continuity-07
  Scenario: Replay and graph repair preserve scope
    Given a reconciled shadow with accepted, pending, and revoked concepts
    When decision history is replayed and the derived graph is rebuilt
    Then the owner view remains equal to the pre-rebuild shadow view
    And no other owner's or unaccepted concept enters accepted reads

  @continuity-08
  Scenario: A rehearsal can return to the prior source
    Given a reconciled shadow and an unchanged prior source
    When an isolated rehearsal chooses shadow and then chooses prior reads
    Then prior owner-visible results are restored
    And both histories and the snapshot remain intact

  @continuity-09
  Scenario: Live selection requires a separate decision
    Given source, parity, replay, and rollback checks pass
    When no explicit owner approval has been recorded for the frozen report
    Then live owner reads remain on the prior source
    And the report describes a qualified shadow candidate only
