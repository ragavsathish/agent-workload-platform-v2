# Semaphore source: v2 voice sidecar

This directory is the source for the dedicated live `AWP v2 Voice Sidecar`
Semaphore project. It does not alter or refresh the existing Grain migration
project pin. `setup_project.py` provisions this isolated project and pins the
playbooks to a local immutable Git revision.

The live project provides `preflight`, `start`, `check-ready`, `status`, and
`stop` templates on the `macos-host` runner. Its non-secret environment values
are:

- `awp_runtime_root`: reviewed staged v2 runtime directory ending in
  `-full` (the same immutable runtime artifact used by host lifecycle review).
- `awp_stack_sha256`: SHA-256 of that staged runtime's `impl/deployment/stack.py`.
- `awp_voice_sidecar_action`: set by each template to `preflight`, `start`,
  `check-ready`, `stop`, or `status`.

The helper reads non-secret runtime settings from the active v2 broker process
and reads the existing `broker-client` and `grain-session` Kubernetes Secrets
into memory. If the local Gitea owner or private workspace is missing, it
creates the configured owner, a repository-scoped token, a `gitea-client`
Kubernetes Secret, and an initialized private workspace repository. It copies
only the non-secret overlay to
`~/Library/Application Support/AWPv2/k3d/voice-sidecar/` with mode 0600 on a
`start` action. Tokens and owner identity are not copied to files, Semaphore
variables, task output, or Ansible logs. The sidecar has a separate state
directory, session directory, task socket directory, broker/Pi ports, and
loopback-only k3d service forwards.

The start operation first checks authenticated health on the legacy broker and
Pi, then starts the reviewed runtime and verifies its sidecar services and
both v2 broker routes. On startup/readiness failure it stops only the verified
sidecar supervisor. The existing Edge broker target and legacy LaunchAgents
are never modified by this playbook. `stop` delegates to `stack.py`'s strict
PID/process-group ownership check for the sidecar env and state paths.

Live verification completed on 2026-11-14. Semaphore check-ready and start
both succeeded; the start was idempotent because the sidecar was already
running. All ten service and broker-route checks returned true. The readiness
profile uses an isolated Python path and concurrent probes so Semaphore's
runner can finish the bounded checks within its task timeout.

Refresh the project pin only through `setup_project.py` after reviewing the
source and staged runtime digest. Edge now targets the sidecar broker at
`host.docker.internal:9553`; the legacy broker and Pi remain available for
rollback and are not stopped by this project.
