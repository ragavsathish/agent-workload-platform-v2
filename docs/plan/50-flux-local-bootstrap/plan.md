# Slice 50: Flux local bootstrap

## Goal

Make local AWP v2 start from the private GitHub source through Flux on k3d. The
user-side bootstrap requires Docker and k3d, creates the cluster only when
absent, reuses existing cluster and persistent storage, installs a pinned Flux
release, and points Flux at the private AWP v2 repository using a read-only
deploy key.
Application Services remain ClusterIP. The current Semaphore route stays
loopback-only; no public or tailnet application route is added.

## Discovery

- The v2 checkout initially had no Git remote. A new private source repository
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
- A private repository needs a checkout and a read-only credential inside the
  cluster. The bootstrap reads an SSH key from the private local support
  directory and never prints it. Acquiring/attaching that key to GitHub is a
  one-time repository setup operation, separate from repeated local bootstrap.
  Docker and k3d alone cannot provide credentials or source access to a private
  GitHub repository.
- Flux reconciles Kubernetes manifests; it does not build local images or
  provide host macOS services. The bootstrap imports the two locally built
  application images before Flux reconciliation. Voice remains degraded when
  the private native broker credential/host capability is not available.

## Acceptance scenarios

See [spec.feature](spec.feature). The script must:

1. Require only `docker` and `k3d` host executables, a running Docker engine,
   a checkout, and the preprovisioned private read-only deploy key.
2. Create `awp-v2-ops` only if absent; reuse or start it otherwise without
   deleting it, changing the current context, or touching PVC data.
3. Install Flux controllers at the pinned version and configure a private
   `GitRepository` plus `Kustomization` for the repository's local deployment
   path, using a mode-0600 read-only deploy key outside source control.
4. Create runtime Secrets outside Git; build and import local source-defined
   images; suspend voice reconciliation when broker/owner inputs are missing.
5. Keep AWP Services ClusterIP and the only local operator entry at loopback.
6. Report a clear prerequisite error when source/auth material is missing;
   never claim GitOps readiness before Flux reports the source and
   Kustomization Ready.

## Implementation and evidence

`impl/deployment/k3d/bootstrap.sh` creates or reuses/starts the cluster, builds
and imports the source-defined local images, installs Flux `v2.10.0` through
the in-cluster K3s kubectl client, prepares private Secrets, and waits for Flux
readiness. Core, optional voice, and fresh-cluster Semaphore Kustomizations
are under `impl/deployment/k3d/`.

Static validation passed: `bash -n impl/deployment/k3d/bootstrap.sh` and a
trailing-whitespace scan across Slice 50 deployment and plan files. Runtime
reconciliation is intentionally not run here: the GitHub repository is private
and must first contain the selected source snapshot. A read-only deploy key is
provisioned at the documented support path with mode 0600. On the existing
cluster, Semaphore's current Helm release and PVCs are preserved; takeover by
the K3s Helm Controller is deferred to a separately reviewed migration.
