Feature: Voice workspace routes and packaged UI assets
  The private voice workspace serves every page and asset that it advertises.

  @voice-ui-001
  Scenario: Bootstrap packages Flutter and auxiliary UI assets
    Given voice prerequisites are present during k3d bootstrap
    When the Edge image is built
    Then the Flutter workspace and all declared MCP app pages are included
    And the diagnostics and knowledge pages are included
    And the referenced favicon and PWA icon paths are served successfully

  @voice-ui-002
  Scenario: Owner opens every embedded MCP app
    Given the owner is signed into the authorized tailnet
    When a tool response selects Mermaid, Marp, trace explainer, journal review, Excalidraw, or Grafana trace UI
    Then the selected app page loads from the private voice origin
    And its static assets load from that same origin

  @voice-ui-003
  Scenario: Owner opens audio diagnostics and knowledge
    Given the owner is signed into the authorized tailnet
    When the owner opens diagnostics or knowledge from the workspace
    Then the selected page loads without a missing-route response
    And knowledge queries use the same-origin `/oxigraph/query` route

  @voice-ui-004
  Scenario: Owner opens Grafana system health
    Given the owner is signed into the authorized tailnet
    When the owner opens the system health dashboard under `/metrics/`
    Then Grafana serves the dashboard below the private voice origin
    And Grafana and Oxigraph remain inaccessible through unauthenticated app routes

  @voice-ui-005
  Scenario: Non-owner cannot use supporting UI routes
    Given a caller is connected to the tailnet but is not the configured owner
    When the caller requests Grafana or Oxigraph through the voice ingress
    Then ForwardAuth refuses the request before proxying to either support service
    And no public or direct service route is created
