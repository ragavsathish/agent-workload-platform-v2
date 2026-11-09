# Slice 49: Semaphore-managed native broker lifecycle

## Goal
Provide one narrowly scoped, operator-invoked Semaphore operation for the v2 native inference broker LaunchAgent `com.sathish.pipecat-broker`. Semaphore uses its reviewed `macos-host` runner. A read-only preflight must establish the exact loaded target, authenticated broker health, and safe restart conditions before any restart. The task may only kickstart this exact label and must verify local and Edge-to-host authenticated health. If any verification fails, restore the previously loaded state and verify recovery.

## Baseline and boundaries
- AWP v1 Compose remains stopped; its named volumes are retained and untouched.
- AWP v2 runs in k3d with ClusterIP app/support Services. Semaphore remains the only host-local operator ingress.
- Do not expose host services, change broker bind addresses, create public/tailnet routes, or restart v1 services.
- Do not print credentials, environment values, process arguments, or unredacted command output.
- Full owner-authenticated live voice qualification remains gated on a valid owner identity; synthetic or absent identity must not be represented as owner evidence.

## Implementation
Add an explicit Semaphore playbook and helper for broker lifecycle. `--check` must be read-only. The helper checks the single approved LaunchAgent label and authenticated `/health` without emitting the token. Before restart it must query a documented, authenticated signal that proves there is no active or unresolved live turn; if no trustworthy signal is available, stop before mutation and report the gate. On restart, use `launchctl kickstart -k gui/<uid>/com.sathish.pipecat-broker`, then verify the label and health locally plus the authenticated Edge pod-to-host health path. On post-check failure, re-kickstart the same exact label and verify recovery. All task output is allowlisted status only.

## Validation
1. Inspect Semaphore project/template IDs and current pinned revision read-only; do not invoke setup without reviewed explicit arguments.
2. Validate Python helper syntax and Ansible playbook syntax/check-mode without mutating host services.
3. If safe preconditions can be proven, invoke only the new Semaphore template and capture redacted output; otherwise keep the operation read-only and record the exact unmet gate.
4. Recheck all 11 v2 pods, `/readyz`, Prometheus readiness, gateway and Fluent Bit node PromQL, v1 Compose stopped/volumes retained, and localhost-only Semaphore ingress.

## Acceptance
See `spec.feature`. No implementation may claim the broker restart succeeded unless both local and Edge authenticated health pass; rollback must restore/verify the initial loaded state if any post-restart check fails.

## Execution record (2026-11-09)

- Source review confirmed `latest-typed-turn` queries Grain turns with origin `typed` only. Pi voice turns have a distinct active in-memory turn and are not covered by that query; the broker's current `/health` endpoint does not expose active inference. The current broker runtime returns HTTP 404 for `/v1/pi/turn/latest`, so there is no deployed trustworthy turn quiescence probe.
- Added `host_broker_probe.py` and `project/host_broker_probe.yml`, an exact-label, read-only Semaphore probe. It uses dotenvx without printing values; the helper reports allowlisted local and Edge authenticated health, typed-turn/compaction status if available, and explicitly says native voice activity is unobservable and `restart_safe=false`. It has no restart operation.
- Ran the helper read-only through the broker dotenvx environment. Result: exact broker LaunchAgent loaded; local authenticated health `ok`; Edge pod authenticated health `ok`; typed-turn and compaction status unavailable; native voice activity not observable; restart safety false. Direct status check returned HTTP 404. No host service was restarted.
- Updated `setup_project.py` to include the focused template and helper in future project provisioning. Did not run the setup script: it re-pins the project repository and rewrites migration runtime metadata. Existing Semaphore live project remains unchanged pending a separately reviewed targeted template registration.
- No host-to-k3d Prometheus path or host metric was added; existing gateway and k3d-node PromQL proofs remain the qualified metrics.
- Final k3d check: all 11 `awp-v2` pods are 1/1 Ready and `/readyz=ok`. Prometheus `/-/ready` passed; PromQL returned gateway readiness `1`, Fluent Bit node `up=1`, and 112 k3d-node CPU series. v2 Services remain ClusterIP with no Ingress objects.
- Final v1 check: zero Compose containers running (three are exited); all 11 named project volumes remain. Semaphore is listening only on `127.0.0.1:9780`.
- Live Semaphore inventory remains project 2/repository 1, pinned at `37c6415b01bc177c4a91a92ebb6f1198608edc36`, with templates 1–12. The new read-only template source is not yet in the live project; existing setup provisioning would rewrite unrelated project/runtime state, so it was not invoked.
- `python3 -m py_compile` and `ansible-playbook --syntax-check` passed for the new helper/playbook. No tests were run.
