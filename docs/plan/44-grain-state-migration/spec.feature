Feature: Migrate native Grain state into the durable k3d candidate

  @grain-migration-001
  Scenario: Only a reviewed Semaphore task can quiesce the native v2 stack
    Given Semaphore uses a pinned local repository revision and the native macOS runner
    When the migration template is run
    Then its read-only preflight requires zero active tasks and no in-flight turn or compaction
    And it validates the exact AWP v2 supervisor and child processes before stopping them
    And it does not touch v1 services or run a public route

  @grain-migration-002
  Scenario: A stopped source yields a recoverable durable snapshot
    Given every AWP v2 Grain writer has gracefully stopped
    When the migration task snapshots the source
    Then it preserves a timestamped rollback copy under durable private host storage
    And it copies only the configured Grain stores and task data
    And it does not include credentials, logs, runtime PID files, or Pi/Gondolin data
    And SQLite and LMDB copies pass integrity and identity checks before deployment

  @grain-migration-003
  Scenario: Candidate Grain preserves source state
    Given the candidate static PV is detached from the empty staging state
    When the copied Grain state is mounted and the candidate starts
    Then the candidate retains the source tenant, session, task, and deployment identities
    And owner-scoped query counts and statuses match the cold snapshot
    And the candidate GET /health is ready without issuing an observation command

  @grain-migration-004
  Scenario: Native rollback runtime stays available after migration
    Given the candidate snapshot is being evaluated
    When the Semaphore task finishes or a migration check fails
    Then the original native v2 state tree remains unchanged
    And the native stack is restarted and healthy
    And v1 remains running until a separate full cutover decision
