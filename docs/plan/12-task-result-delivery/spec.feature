Feature: Deliver a task result and its captured work product
  A task owner can inspect a safe answer and retrieve only the work product that task declares.

  @artifact-001
  Scenario: Repository task captures its patch
    Given an owner has a task for an approved repository snapshot
    When the task changes that repository and completes
    Then its result declares a patch for the same task
    And the downloaded patch contains those changes

  @artifact-002
  Scenario: Scratch task captures its workspace
    Given an owner has a repository-free scratch task
    When the task writes regular files and completes
    Then its result declares a workspace archive for the same task
    And the downloaded archive contains only its bounded regular files

  @artifact-003
  Scenario: Capture failure prevents a false completion
    Given a task produces an oversized work product or an escaping file
    When capture is attempted
    Then the task retains a bounded failure
    And no downloadable artifact is advertised

  @artifact-004
  Scenario: The selected workspace determines the artifact kind
    Given one repository task and one scratch task have completed
    When the owner requests the other kind for either task
    Then each request fails without returning bytes

  @artifact-005
  Scenario: Public answer hides internal execution detail
    Given a task completed with an answer and captured work product
    When its owner reads the task
    Then the answer and captured artifact kind are visible in bounded form
    And private storage addresses, raw execution events, and capability fields are absent

  @artifact-006
  Scenario: Ownership covers both task and artifact reads
    Given an owner has a completed task with an artifact
    When another owner reads that task or artifact
    Then both reads reveal no task
    And neither returns the artifact bytes

  @artifact-007
  Scenario: Retrieval validates the task's exact declaration
    Given a completed task declares one artifact
    When a read names another task, an unknown kind, or an altered reference
    Then the read fails explicitly without returning bytes
    And no arbitrary object can be selected by the caller

  @artifact-008
  Scenario: Artifact loss does not rewrite task completion
    Given a task completed with a declared artifact
    When its object is missing, oversized, or read incompletely
    Then the owner sees an explicit artifact lookup failure
    And the task remains completed with its original answer

  @artifact-009
  Scenario: Owner delivery survives a fresh process
    Given an owner has a completed task with a captured work product
    When the owner service restarts and the owner downloads it
    Then the same answer and bytes are available
    And the response has a private download filename and content type
