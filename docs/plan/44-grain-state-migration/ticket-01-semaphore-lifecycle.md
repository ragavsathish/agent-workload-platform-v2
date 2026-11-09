# Ticket 01: pin and qualify host lifecycle operations through Semaphore

## Acceptance

- Create a dedicated local Semaphore project and repository pinned to an immutable local Git revision.
- Use runner tag `macos-host` and local executor; inventory target is only `localhost` on the native macOS runner.
- Add reviewed `stack.py stop` and `stack.py start` commands that identify the exact supervisor by state/env paths and recorded child ownership; never match by broad process-name substring.
- Stop is graceful and bounded. It sends termination only to the verified supervisor, waits for all verified children to exit, and has no force-kill fallback.
- Start runs the verified stack entrypoint detached with the existing private env/state files, then waits for payload-free GET health and expected child readiness.
- Semaphore no-op and read-only preflight tasks succeed and their output has no credentials or event payload.
- The AWP host mutation template remains disabled until the repository revision, runner tag, health probes, and rollback path are confirmed.

## Evidence

Record project/template identifiers, immutable repo revision, runner status/tag, task IDs, changed/unreachable/failed counts, and redacted output scan.
