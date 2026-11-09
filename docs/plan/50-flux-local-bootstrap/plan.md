# Slice 50: Flux local bootstrap

## Goal

Make local AWP v2 start from the public GitHub source through Flux on k3d. The
user-side bootstrap requires Docker and k3d, creates the cluster only when
absent, reuses existing cluster and persistent storage, installs a pinned Flux
release, and points Flux at the public AWP v2 repository without a credential.
Application Services remain ClusterIP. The current Semaphore route stays
loopback-only; no public or tailnet application route is added.

## Discovery

- The v2 checkout initially had no Git remote. A public source repository
  is available at `ragavsathish/agent-workload-platform-v2`; its selected
  source revision must be pushed before Flux can reconcile it.
- The k3d host already has Docker and k3d, and the `awp-v2-ops` cluster is
  running with data in the host-backed support directory. Bootstrap must reuse
  it and must not recreate or delete it. A clean host with no cluster gets a
  new loopback-only cluster and persistent local-path backing.
- The k3d server image provides `/bin/kubectl` as a symlink to `k3s`;
  `docker exec k3d-awp-v2-ops-server-0 kubectl` avoids a host `kubectl`
  install. K3s also has the packaged Helm controller,
  although the first GitOps slice does not move the existing Semaphore release.
- Flux reads the public source anonymously over HTTPS; no Git key or
  Kubernetes authentication Secret is needed.
- Flux reconciles Kubernetes manifests; it does not build local images or
  provide host macOS services. The bootstrap builds and imports the source-
  defined Grain, gateway, Edge, and trace-render images before reconciliation
  when voice inputs are present. Voice remains degraded when the private
  native broker credential/host capability is not available.

## Acceptance scenarios

See [spec.feature](spec.feature). The script must:

1. Require only `docker` and `k3d` host executables, a running Docker engine,
   a checkout of the public source.
2. Create `awp-v2-ops` only if absent; reuse or start it otherwise without
   deleting it, changing the current context, or touching PVC data.
3. Install Flux controllers at the pinned version and configure a public
   `GitRepository` plus `Kustomization` for the repository's deployment path.
4. Create runtime Secrets outside Git; build and import local source-defined
   images; suspend voice reconciliation when broker/owner inputs are missing.
5. Keep AWP Services ClusterIP and the only local operator entry at loopback.
6. Report a clear prerequisite error when the source is unavailable;
   never claim GitOps readiness before Flux reports the source and
   Kustomization Ready.

## Implementation and evidence

`impl/deployment/k3d/bootstrap.sh` creates or reuses/starts the cluster, builds
and imports the source-defined local images, installs Flux `v2.10.0` through
the k3d server's bundled `kubectl` entry point, prepares private Secrets, and
waits for Flux readiness. Core, optional voice, and fresh-cluster Semaphore
Kustomizations are under `impl/deployment/k3d/`.

Static validation passed: `bash -n impl/deployment/k3d/bootstrap.sh` and a
trailing-whitespace scan across Slice 50 deployment and plan files. The public
GitHub source repo is populated on `main`; Flux reads it anonymously.

Runtime verification on 2026-11-09:

- `bootstrap.sh up` reused `awp-v2-ops`, built/imported the four source-defined
  local images, and completed without deleting the cluster or its PVCs.
- Flux `GitRepository/awp-v2`, `Kustomization/awp-v2-core`, and
  `Kustomization/awp-v2-voice` reported Ready at `main@sha1:57211353336b3bed9ca26ebc06b283642c8a84e1`.
- All 11 AWP pods returned Ready. The Grain PVC remained Bound with UID
  `99491dfc-8d65-4417-b413-a42ee1f87804` and PV `awp-v2-grain-candidate`.
- Prometheus `/-/ready` passed. PromQL returned gateway readiness `1`,
  `up{job="fluent-bit-k3d-node"}=1`, and 112 k3d-node CPU series.
- Every AWP Service remains ClusterIP. The only Ingress is Semaphore at
  `localhost`; k3d maps its UI to `127.0.0.1:9780` and API to
  `127.0.0.1:6448`.
- The existing Semaphore Helm release and PVCs are preserved. Flux's
  Semaphore Kustomization remains suspended on this existing cluster pending
  a reviewed Helm ownership migration; a newly created cluster installs
  Semaphore through K3s's Helm Controller.
- Voice inputs were present, so the Edge and trace-render Kustomization was
  enabled. This establishes deployment readiness only; owner-authenticated
  live voice and host-native Pi/MLX/Gondolin operations remain separate
  qualification gates.

Anonymous-source verification after the public-source update:

- `bootstrap.sh up` completed and Flux fetched public `main` at
  `main@sha1:022fb691eeda0058abb2bb7903d2deafc8160cf8` with an HTTPS URL and
  no `secretRef`.
- After that fetch succeeded, the generated GitHub deploy key, local keypair,
  and `flux-system/awp-v2-source` Secret were deleted. A forced source
  reconciliation remained Ready at the same revision; core and voice
  Kustomizations remained Ready.
- The deploy-key listing is empty, both local key files are absent, and the
  Kubernetes Secret is NotFound. No key dependency remains in the bootstrap,
  source manifest, README, or Slice 50 plan/spec/decision files.
