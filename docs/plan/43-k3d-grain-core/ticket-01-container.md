# Ticket 01: package Grain for k3d

## Acceptance

- Build a Linux image from the checked-in AWP source and pinned Grain/ORC SHAs.
- Keep the current native bind address as the default; permit an explicit pod-interface bind.
- Expose a payload-free GET health endpoint suitable for Kubernetes probes.
- Use a non-root process and a dedicated ClusterIP Service.

## Evidence

Record the image reference and digest, k3d image availability, pod status, service type, and GET health result. Do not run owner commands as a health probe.
