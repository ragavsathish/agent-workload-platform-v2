# Sources: docs/wiki/tailnet-owner-voice.md, docs/wiki/deployed-voice-journey.md,
# docs/wiki/live-session-integration.md, and the Slice 51 decisions.
Feature: Private owner voice journey through the host runtime
  An authorized owner uses one private entry and receives a complete, recoverable voice turn.

  @voice-tailnet-001
  Scenario: Configured owner opens the private voice workspace
    Given the owner's device is authorized for the private AWP voice service
    And the voice workspace and secure browser origin are ready
    When the owner opens the private voice address
    Then the Flutter voice workspace is served over HTTPS
    And the Edge receives the verified identity of the connected owner
    And the workspace does not become available through a public sharing route

  @voice-tailnet-002
  Scenario: Tailnet access alone does not authorize another owner
    Given a connected caller is not the owner configured for this voice workspace
    When the caller opens an owner route or starts a voice session
    Then Edge refuses the request before exposing owner session or task data
    And no caller-supplied identity header is accepted as proof of identity

  @voice-tailnet-003
  Scenario: Spoofed identity cannot bypass the trusted entry
    Given an untrusted caller sends a Tailscale identity header directly to the owner service
    When the caller requests an owner route
    Then the request is denied unless it passed through the trusted private entry
    And the service exposes no selected conversation, transcript, or voice capability

  @voice-tailnet-004
  Scenario: Explicit owner action starts microphone access
    Given the configured owner has opened the secure voice workspace
    When the owner explicitly starts a voice conversation
    Then the browser requests microphone access from that user action
    And loading or retrying the workspace does not request microphone access

  @voice-tailnet-005
  Scenario: Spoken turn crosses the private network and host boundary
    Given the configured owner has selected a conversation and started a live media connection
    And the Edge can reach the host voice and inference capabilities
    When the owner speaks one final utterance
    Then the browser and Edge establish bidirectional media
    And the host runtime processes the utterance once for the selected conversation
    And the owner receives audible response media and a safe transcript for that same turn
    And Grain retains the selected conversation and settled turn identity

  @voice-tailnet-006
  Scenario: Media connectivity is unavailable
    Given the owner can load the secure voice workspace
    But the bidirectional media path cannot be established
    When the owner attempts to start a spoken turn
    Then the workspace reports voice as unavailable
    And no successful spoken turn or assistant response is reported

  @voice-tailnet-007
  Scenario: Host voice capability is unavailable
    Given the owner has opened the private voice workspace
    But the host voice or inference capability is unavailable
    When the owner submits a spoken turn
    Then the owner receives a bounded failure or inspectable uncertainty
    And the turn is not reported as successfully answered

  @voice-tailnet-008
  Scenario: Completed owner turn is recoverable after service restart
    Given the owner completed a spoken turn in the selected conversation
    When the owner reloads the workspace after the voice services restart
    Then the selected conversation and safe turn history remain available
    And the completed utterance is not dispatched again

  @voice-tailnet-009
  Scenario: Private route is not publicly shared
    Given the owner voice service is ready on the private network
    When the operator inspects exposed routes
    Then only authorized private-network devices can reach the owner entry
    And no public sharing or unauthenticated route is enabled
