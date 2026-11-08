Feature: Edge deployment serves the owner browser workspace
  The protected owner entry includes the complete web client required by the
  authenticated workspace.

  @candidate-012
  Scenario: The edge image contains the built Flutter workspace
    Given the Flutter web build is present
    When the candidate edge image is built
    Then the build copies the web client into the directory served by Edge
    And the owner page and bootstrap assets are available

  @candidate-013
  Scenario: Preflight refuses an absent browser build or image copy rule
    Given the Flutter build is absent or the edge image omits its copy rule
    When candidate preflight runs
    Then preflight reports the missing browser asset prerequisite
    And the candidate is not started

  @candidate-014
  Scenario: Loopback owner page check does not qualify public entry
    Given the owner page and API are available only on loopback
    When the local candidate is checked
    Then local page and API availability are recorded
    And the public candidate remains prepared-only without external identity and peer evidence
