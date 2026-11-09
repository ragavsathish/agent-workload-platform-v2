# Ticket 03: add AWP readiness, PromQL, and cutover tasks

## Boundary

Add reviewed Semaphore task templates for in-cluster Grain, gateway, and Edge deployment/readiness, macOS host-service status/restart, PromQL evidence, and rollback. Keep Grain as the authority for task, session, and deployment readiness state. Perform v1 shutdown only after a reviewed cutover gate passes.

## Acceptance

- Read-only tasks report current AWP service state and `check-ready` without exposing tokens or private transcript content.
- Deployment and readiness tasks cover Grain/gateway/Edge in k3d and Pi/MLX/Gondolin scheduler on macOS through Semaphore; they verify the internal host bridge from an AWP pod before owner traffic is enabled.
- The PromQL query `sum by (service_name) (pipecat_operation_total{deployment_environment_name="awp-v2-local"})` returns v2 service series; v1 series are excluded.
- A mutating deployment task runs only after the runner network preflight passes and references a pinned release.
- Rollback restores the last qualified AWP v2 release without changing Grain state identity.
- The v1 deployment stays available if any candidate readiness, owner, task, PromQL, or rollback check fails.
- Public entry remains unexposed until its existing identity and separate-device media gates pass.

## Evidence

Retain task IDs and bounded results for readiness, PromQL, owner/task continuity, and staged rollback. Do not include transcript text, tokens, or other owner payloads.

