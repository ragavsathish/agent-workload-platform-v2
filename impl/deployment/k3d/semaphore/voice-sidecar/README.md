# Semaphore source: v2 voice sidecar

This directory contains a standalone playbook for a future, separately managed
Semaphore project. It does not alter or refresh the existing Grain migration
project pin and has not been registered with the live Semaphore instance.

Create a dedicated local project/repository on the `macos-host` runner and add
`host_voice_sidecar.yml`, `host_voice_sidecar.py`, and
`voice-sidecar.env.example`. Set these non-secret project environment values:

- `awp_runtime_root`: reviewed staged v2 runtime directory ending in
  `-full` (the same immutable runtime artifact used by host lifecycle review).
- `awp_stack_sha256`: SHA-256 of that staged runtime's `impl/deployment/stack.py`.
- `awp_voice_sidecar_action`: `preflight`, `start`, `check-ready`, `stop`, or
  `status`.

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

Do not invoke project setup or refresh the existing project pin as part of this
slice. Project registration and runtime deployment remain operator actions
after review. Edge cutover is a separate operation requiring route qualification
and an independently invocable rollback.
