# Ticket 02: qualify runner networking and Ansible targets

## Boundary

Register one native macOS Semaphore remote runner with the local executor. Keep runner placement distinct from the in-cluster Semaphore UI. The native runner has the actual macOS host network and can manage launchd/filesystem-backed Pi voice, MLX broker, and Gondolin scheduler services. Establish how it reaches the k3d API and safely operates those host services.

## Acceptance

- The runner remains available after a service restart and its registration token is stored outside source control.
- A harmless preflight proves each configured route from the runner to Semaphore, AWP readiness endpoints, and the k3d API.
- Evidence names that the runner process shares the macOS host network and lists the verified loopback AWP, Semaphore, and k3d API endpoints.
- The playbook target model can perform approved macOS lifecycle operations without claiming that a k3d pod or Docker container shares the macOS filesystem or process table.
- The runner's host networking is not used as a substitute for a separate cluster-to-host API bridge; both routes have independent probes and explicit endpoints.
- Host-to-k3d support-service routes use host loopback ports; k3d-to-host Pi/broker routes use the separately qualified internal bridge.
- Missing host or k3d API reachability prevents deployment and rollback tasks from running.
- Do not switch to `network_cli`; these targets are the local AWP app and Kubernetes API, not network appliances.

## Evidence

Capture runner status, executor type, network mode, port probes, Ansible inventory host model, a no-op task run, and proof that no secret value entered task output.

