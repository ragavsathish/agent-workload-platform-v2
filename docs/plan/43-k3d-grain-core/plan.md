# Slice 43: stage Grain in k3d with durable isolated state

This slice advances [the k3d operations specification](../39-k3d-semaphore-operations/plan.md), especially scenarios k3d-002a, k3d-006, and k3d-007. Its deployment choices follow [decision D04](../39-k3d-semaphore-operations/decisions.md). The native library adjustment follows the upstream [LmdbJava changelog](https://github.com/lmdbjava/lmdbjava/wiki/Change-Log) and [0.9.3 POM](https://repo.maven.apache.org/maven2/org/lmdbjava/lmdbjava/0.9.3/lmdbjava-0.9.3.pom).

## Goal

Package the existing Grain control plane as a pinned, local k3d workload with a stable host-backed data volume. Keep the candidate private inside the cluster and use a new state directory. Prove the process and HTTP readiness path without copying or attaching the currently live native Grain stores.

## Boundary

The candidate image runs the existing Grain model and endpoint implementation. Native defaults remain loopback-only; a configurable bind host permits the pod to listen on its pod interface behind a Kubernetes `ClusterIP` Service. A public ingress, Edge route, task scheduler, and cutover are outside this slice. The existing v1 Compose stack and native v2 services remain available.

The current native v2 state is non-empty. Slice 43 therefore does not claim owner or task continuity: a later slice must produce and qualify a supported quiesced snapshot/replay migration into the durable v2 data directory before cutover.

The pinned Grain source tree resolves LmdbJava 0.9.2 with native artifact 0.9.33-2, whose Linux arm64 library requires glibc 2.38. This slice overrides only `org.lmdbjava/native` to official 0.9.33-5, the compatible upstream repack, and keeps all Grain source SHAs unchanged. Verify the candidate on Linux arm64 and preserve macOS native classpath resolution.

## Scenarios

See [feature scenarios](spec.feature).

## Tickets

- Ticket 01: build and run a versioned Grain image in k3d.
- Ticket 02: persist Grain's candidate stores across pod restart and report read-only readiness.

## Exit evidence

Record image identity and source pins, ClusterIP-only service exposure, persistent volume path and identity, pod readiness, health response, restart persistence, current live-state inventory summary, and the explicit continuity migration gate. No event payload or credential is included.
