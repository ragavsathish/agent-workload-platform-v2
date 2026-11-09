# Slice 50 decisions

1. Use Flux v2.10.0 with controllers installed through the Kubernetes API
   reached by `docker exec ... k3s kubectl`; do not require a host Flux,
   kubectl, or Helm executable.
2. Use a private GitHub repository and a read-only SSH deploy key. The key is
   kept under the user support directory with restrictive permissions and is
   stored as a Kubernetes Secret only in the `flux-system` namespace.
3. Do not recreate the existing k3d cluster or replace any PV/PVC during
   bootstrap. Reuse the existing persistent state and its loopback ports.
4. Keep the Flux source scope explicit. Do not recursively apply every YAML
   file in the repository.
5. Flux does not build container images. Build/import the pinned local Grain
   and gateway images before reconciliation; keep other app image ownership
   explicit in later slices.
6. Host-native Pi/MLX/Gondolin lifecycle remains under Semaphore. A successful
   k3d/Flux bootstrap does not claim that host services or voice are qualified.
