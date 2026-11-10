# Local AWP v2 k3d deployment

## One-command Flux bootstrap

The v2 local platform bootstrap is:

```sh
impl/deployment/k3d/bootstrap.sh up
impl/deployment/k3d/bootstrap.sh status
```

The host-side command prerequisites are Docker and k3d. It uses the k3d server
container's bundled `kubectl` entry point (a symlink to `k3s`), installs Flux
`v2.10.0` from its pinned upstream release manifest, builds/imports the
source-defined Grain, gateway, Edge, and
trace-render images, then waits for Flux to report the public Git source and
core Kustomization Ready. Flutter web assets are built in the pinned
`ghcr.io/cirruslabs/flutter:3.47.7` container when absent from the checkout.
Flux applies only the resources explicitly listed in
[`kustomization.yaml`](kustomization.yaml). It pulls the chart and upstream
images declared by those manifests.

Flux reads the public
[`ragavsathish/agent-workload-platform-v2`](https://github.com/ragavsathish/agent-workload-platform-v2)
repository over HTTPS without credentials. The repository's `main` branch
must contain this bootstrap slice before Flux can reconcile it.

The script creates `awp-v2-ops` only when absent. When present it reuses or
starts the existing cluster, preserves all PVCs, and never deletes/recreates
it. A new cluster binds the k3d API and operator UI ports to `127.0.0.1`; AWP
Services remain ClusterIP. No AWP public or tailnet route is created.

Bootstrap generates and retains private local credentials under
`~/Library/Application Support/AWPv2/k3d` for Grain, the gateway, Grafana,
Grafana MCP, and the fresh-cluster Semaphore admin and runner registration.
The following workload inputs are handled as follows:

| Workload | Image source | Secret/config input | Bootstrap behavior |
| --- | --- | --- | --- |
| Grain | `grain.Containerfile` | `grain-session` token and local owner identity | Generated once if absent; existing Kubernetes Secret is preserved. |
| Gateway | `gateway.Containerfile` | `gateway-access` | Generated once if absent. |
| Gitea, S3Mock, Oxigraph, OTEL-LGTM, MLflow, Fluent Bit | Pinned upstream images in manifests | None beyond local service credentials already declared | Flux applies after the local images are imported. |
| Grafana MCP | Pinned upstream image in manifest | `grafana-admin`, `grafana-mcp-caller` | Both generated once if absent; MCP bootstraps a read-only Grafana account. |
| Edge and trace renderer | Built from the voice-agent source; Flutter assets are built in a container | `broker-client`, `voice-edge-settings`, `voxlock-owner-sample` | A separate Flux Kustomization is enabled only when all three existing secrets are present. Otherwise voice stays degraded and these workloads are suspended. The active Pi broker, owner settings, sample asset, and macOS services are not fabricated. |
| Semaphore | Pinned chart `16.2.2` via K3s Helm Controller `HelmChart` | `semaphore-admin`, `semaphore-runner` | Installed by Flux on a newly created cluster, with generated credentials and loopback-only ingress. An existing release and its PVCs are preserved; migration to K3s Helm Controller ownership is a separate reviewed step. |
| Semaphore macOS runner | Existing private native binary/LaunchAgent | Runner registration is held by Semaphore | Reused only when already installed on the host. A new host still needs its macOS runner installation before Semaphore can operate host services; this is outside Docker+k3d bootstrap. |

Host-native Pi/MLX/Gondolin services still require a macOS Semaphore runner and
their owner credentials/assets. The bootstrap reports voice as degraded when
the required inputs are absent. It does not claim that host services or live
voice work from k3d alone.

## Private voice URL

After the host broker, owner settings, and voice assets are present, run:

```sh
impl/deployment/k3d/voice/tailnet-up.sh
```

The command asks for the Tailscale owner login and a preauthorized,
non-ephemeral auth key. It reads the key without echo, stores it only in the
`awp-v2/voice-tailnet-auth` Kubernetes Secret, resumes the voice Flux
Kustomization, enrolls a persistent `awp-v2-voice` tailnet node, sets the exact
HTTPS origin and owner allowlist in `voice-edge-settings`, and prints the
resulting MagicDNS URL. The Tailscale node state is persisted in a local-path
PVC. The helper does not alter tailnet ACLs or expose Funnel/public ingress.

Open the URL from a device signed into the same tailnet. HTTPS and signaling
are provided by Tailscale Serve; live voice also needs direct bidirectional
WebRTC media over the tailnet interface. The URL is only considered ready for
use after connection and actual audio both work. Tailscale auth and access
policy must allow this device to reach the voice node. If the owner email does
not match the login Tailscale supplies, rerun the helper with the exact
Tailscale user login. Re-enrollment after deleting the state PVC requires a
new auth key.

The older `semaphore.sh` and `grain.sh` scripts remain for focused operations;
they still use host `kubectl`/Helm as documented below. Use `bootstrap.sh` for
the Docker+k3d-only entry path.

## Earlier operator-only setup

The following sections record the earlier Semaphore-only and staged deployment
procedures. They are not the one-command bootstrap path above.

Semaphore UI uses a loopback-only host port; there is no public or tailnet
route.

## Prerequisites

Install the command line dependencies with `brew install k3d kubectl helm` and
install/start Docker Desktop separately. The cluster is named `awp-v2-ops`, uses
K3s `v1.34.13`, API port `127.0.0.1:6448`, and Semaphore port
`127.0.0.1:9780`. Open the UI at `http://localhost:9780`.

## Start, inspect, stop

From the repository root:

```sh
impl/deployment/k3d/semaphore.sh up
impl/deployment/k3d/semaphore.sh status
impl/deployment/k3d/semaphore.sh stop
```

`stop` deletes this dedicated k3d cluster but retains persistent files under
`~/Library/Application Support/AWPv2/k3d`. Re-run `up` to recreate the cluster
and reinstall the chart. It fails before creation if either required loopback
port is already occupied. It does not change kubectl's global current context.

The chart is pinned to Semaphore chart `16.2.2` / app `v2.18.3` and uses the
official `https://semaphoreui.github.io/charts` Helm repository. The chart
creates durable BoltDB and work-directory claims using the k3d local-path
storage directory mounted from the macOS state path. Do not delete that state
directory when deleting the cluster.

## Credentials

On first `up`, the provisioner creates a non-default `awp-operator` password
and runner registration secret outside the checkout, under
`~/Library/Application Support/AWPv2/k3d/semaphore`, with directory mode 0700
and file mode 0600. The admin login is in `admin.env`. Keep both files private;
do not copy their contents into shell history, task output, or source control.
To rotate the admin password, update the chart's bootstrap credentials in
that file before rebuilding the cluster; the persisted Semaphore user takes
precedence after initial setup.

## Runner boundary and qualification

The previous native `v2.18.31` runner registered against server app `v2.18.3`,
but polling failed with HTTP 400 `invalid public key`. The `v2.18.31` server
image panicked under chart `16.2.2`'s BoltDB configuration with `unknown store
type` in `pro/db/factory.NewTerraformStore`; the server was restored to
`v2.18.3`, and the failed runner record was removed.

Compatibility was established by building the runner from the official
Semaphore [`v2.18.3` source tag](https://github.com/semaphoreui/semaphore/tree/v2.18.3),
commit `ae88bf937a7635a987271d082c8f62c70691aafa`, which matches the live server
banner. The official GitHub tag has no binary release assets, so the native
runner binary was built from that source and placed outside the checkout at
`~/Library/Application Support/AWPv2/k3d/semaphore/semaphore-v2.18.3`.
SHA-256: `25ab74711fb019767ac97af07f597ffcfceccf4a9b98a09d0137612ab7eb2a71`.
This build is runner-only; it used a temporary placeholder web asset to satisfy
the source tag's embed directive. It must not replace the deployed server image.

The native macOS runner `awp-v2-macos-host` is registered and active in
Semaphore. Its config, registration token, runner token, private key, and
LaunchAgent logs live outside source control with mode `0600`; the containing
state directory is mode `0700`. A user LaunchAgent keeps the local executor
connected across process restarts. Ansible `core 2.21.6` is installed at
`/opt/homebrew/bin/ansible-playbook`. Semaphore task `2147483646` ran the
`Harmless localhost no-op` template through this runner and succeeded with
`ok=1 changed=0 unreachable=0 failed=0`. The output contains only the fixed
no-op marker; the admin password, registration token, and runner token were
absent. The source instructions for remote runners are in the
[official runner guide](https://semaphoreui.com/docs/admin-guide/runners).

No AWP-mutating templates are enabled. A follow-up attempt to call
`stack.py check-ready` from a Semaphore template was stopped and its template
removed after source inspection showed that this command also POSTs
`/internal/deployment/observe` to Grain. The direct invocation made once during
diagnosis returned healthy results for Grain, Pi, broker, gateway, and
scheduler, but it also appended a deployment-observation event. That event is
retained; it has not been rewritten or hidden. The attempted Semaphore task
`2147483645` is stopped, and its child processes were terminated. Before a
readiness task is used through Semaphore, split the observation write from
pure health probing and review the event semantics.

The Kubernetes API is currently ready at loopback `127.0.0.1:6448`; the
Semaphore API responds on `127.0.0.1:9780`. This slice verified the runner's
connection to Semaphore by successful registration, heartbeat, and no-op
execution. It did not verify k3d API access from inside an Ansible task, nor
the separate k3d-pod-to-host bridge. Keep those probes as gates before
deployment or rollback templates are enabled. Host-side Pi/MLX/Gondolin
lifecycle operations are intended to be run through this native runner, but
their playbooks remain disabled until those read-only probes and the approved
host lifecycle inventory are reviewed.

## Slice 46: gateway telemetry and host preflight

The gateway emits bounded response counters and a health gauge over OTLP HTTP
to the v2-only `otel-lgtm.awp-v2.svc.cluster.local:4318` endpoint. Prometheus
returned `awp_v2_gateway_http_requests_total` for the `awp-v2-gateway`
`awp-v2-local` series, and `awp_v2_gateway_ready_ratio` returned `1`. The
counter labels contain only route category, method, and response class. They
do not contain request paths, task/session identifiers, owners, prompts,
credentials, or artifact data. This verifies real gateway instrumentation and
the v2 OTLP/PromQL path; it does not prove that host-native services are
instrumented.

Semaphore template `AWP v2 host runtime read-only probe` ran as task
`2147483565` on the `macos-host` runner. It reported the approved native
LaunchAgent label allowlist, local k3d API readiness, and readiness of Grain,
gateway, Gitea, S3Mock, Oxigraph, and OTEL-LGTM. Its helper reports no PIDs,
process arguments, environment values, or credentials. A separate TCP-only
probe from the gateway pod to `host.docker.internal` at the native inference
broker port connected successfully. This is L4 reachability only; authenticated
broker health still requires an approved credential path.

No native lifecycle task was enabled or executed. Exact service health,
side-effect review, and rollback behavior must be established before adding
those operations. Edge/Flutter, standalone Grafana MCP, and host-native
Prometheus series remain unqualified. v1 and all public/tailnet route state
were left unchanged.

## Deployment evidence (2026-11-09)

- Cluster `awp-v2-ops`, K3s `v1.34.13+k3s1`, chart `16.2.2`, server app
  `v2.18.3`; the host port `9780` is bound only to `127.0.0.1`, and the API
  port `6448` is also loopback-only.
- UI root and `/api/ping` returned HTTP 200. Login returned HTTP 204, and the
  authenticated admin endpoint returned the persisted `awp-operator` identity
  after pod restarts and a server-version rollback rehearsal.
- The BoltDB (1 GiB) and workdir (2 GiB) PVCs remained Bound across restarts.
  K3d local-path storage is mounted from
  `~/Library/Application Support/AWPv2/k3d/storage`.
- Admin and runner registration files remain outside source control with
  private directory/file modes. The native runner and harmless localhost
  no-op are qualified. No v1 or native v2 service was stopped. The direct
  readiness observation and the stopped attempted task are documented above.

## Slice 43: Grain candidate in k3d

Slice 43 stages only Grain. Semaphore remains the operator interface; its
native macOS runner is the intended manager for Pi, MLX, and Gondolin host
services. This stage has no gateway, Edge/Flutter route, support-service plane,
host bridge, or Prometheus/OTLP path. It does not claim product readiness,
owner continuity, PromQL qualification, or cutover readiness. v1 Compose and
the native v2 owner services remain running.

From the repository root, the candidate commands are:

```sh
impl/deployment/k3d/grain.sh up
impl/deployment/k3d/grain.sh status
impl/deployment/k3d/grain.sh restart
```

The image is `awp-v2-grain:slice43`, built for linux/arm64 with image digest
`sha256:dccd26236d8c4fe4303fd7bc91da155747c0abf36e518dbfdf8e0eca67efb7e4`,
from the pinned Clojure base image
`clojure:temurin-21-tools-deps@sha256:142e93f5b4b3a2a93bef227515df452040f408f4ef71853b9f65598b1a625bb7`. Its
source dependencies keep Grain `dbf5b5229aad09785e40f71e96adf65c90f971d5`
and ORC `033f0ab7c2be46fd8bddd5fc94e362d074afac34` unchanged. Grain's pinned
LmdbJava 0.9.2 native `.33-2` failed on Bookworm glibc 2.36; a direct
`org.lmdbjava/native` override to 0.9.33-5 supplies the official Linux arm64
library with broader glibc compatibility while retaining macOS arm64 support.
The override follows the upstream [LmdbJava changelog](https://github.com/lmdbjava/lmdbjava/wiki/Change-Log).
Both the Linux arm64 container and a fresh macOS Grain process passed GET
`/health`; the macOS smoke used the same JVM module flags as the native
supervisor and was stopped afterward.

The `awp-v2` namespace contains one Grain StatefulSet and a headless
`ClusterIP` service on 9447. `grain-0` is 1/1 Ready, and the unauthenticated
payload-free GET `/health` returns `{"status":"ready"}`. A temporary
port-forward binds only to 127.0.0.1 and is closed by the probe script. No
Ingress, NodePort, host port, public route, or tailnet route exists for Grain.
The k3d API and Semaphore remain on their existing loopback-only ports.

The old native v2 stores under `/private/tmp/awp-v2-state` contain session and
deployment events and remain owned by the running native process. They were
not attached to the pod or copied live. The isolated Grain candidate started
with empty domain stores and only candidate deployment observations. Its first
dynamic local-path PVC was stopped and copied cold to the fixed host-backed
directory `~/Library/Application Support/AWPv2/k3d/storage/awp-v2-grain-candidate`.
The source deployment store had 4 events, 2 event tags, 1 tenant with identity
hash prefix `543957a26761e647`; the copied store matched that summary before
rebind. The candidate process then produced one new readiness observation on
startup (2 additional SQLite rows: one `:deployment/fact` and one `:grain/tx`).

The final static PV is `awp-v2-grain-candidate`, hostPath
`/var/lib/rancher/k3s/storage/awp-v2-grain-candidate`, reclaim policy `Retain`,
and node affinity to `k3d-awp-v2-ops-server-0`. The existing k3d server bind
maps that path to the host storage directory above. PVC `grain-data-stable` is
Bound with UID `99491dfc-8d65-4417-b413-a42ee1f87804`. A controlled StatefulSet
restart kept the same PVC UID and PV and returned Grain to 1/1 Ready. The
deployment store then had 8 events, 4 tags, the same tenant hash prefix, and
4 each of `:deployment/fact` and `:grain/tx`; session, tasking, knowledge, song,
and incident stores remained at zero events. The obsolete dynamic PVC and its
Delete PV were removed only after the static claim, copied identity, restart,
and health probe passed. Full k3d cluster deletion/recreation is not rehearsed
here; the fixed hostPath and static PV manifest define the reattachment path,
but it still needs its own recovery qualification.

The init container changes ownership only on the mounted candidate directory;
the Grain application container runs as UID/GID 10001 with a read-only root
filesystem. `/tmp` remains executable because the packaged SQLite/LMDB native
libraries load from there. The candidate is configured at `voice` deployment
level and has no scheduler or owner-facing route. The non-empty native v2
stores require a later quiesced snapshot/replay migration and continuity check
before cutover. No PromQL can yet prove the k3d and macOS services together.

## Slice 45: internal application and data plane

The `awp-v2` namespace now contains the Grain-backed gateway, Gitea, S3Mock,
Oxigraph, and a dedicated v2 OTEL-LGTM stack. Their manifests are
`app-plane.yaml` and `telemetry.yaml`; all Services are `ClusterIP`. The local
gateway image is built from the v2 source with:

```sh
docker buildx build --platform linux/arm64 --load \
  -f impl/deployment/k3d/gateway.Containerfile \
  -t awp-v2-gateway:slice45 .
k3d image import awp-v2-gateway:slice45 -c awp-v2-ops
kubectl --context k3d-awp-v2-ops apply -f impl/deployment/k3d/app-plane.yaml
kubectl --context k3d-awp-v2-ops apply -f impl/deployment/k3d/telemetry.yaml
```

Create `gateway-access` and `grafana-admin` Kubernetes Secrets from generated
credentials; the Grafana password is kept outside the checkout under
`~/Library/Application Support/AWPv2/k3d/grafana/admin-password`. Never put
credential values in manifests or shell output. Gateway reuses only Grain's
owner identity and service token for its Grain authority client; its caller
bearer token is independent.

Gitea, S3Mock, Oxigraph, and OTEL-LGTM each have a StatefulSet claim template.
The Slice 45 plan records claim UIDs, image pins, live readiness and in-cluster
probe evidence. A synthetic OTLP gauge traversed the v2 collector and returned
from Prometheus via PromQL. This confirms the telemetry path, not actual AWP
service instrumentation. Edge/Flutter, standalone Grafana MCP, and Semaphore
mutating/status templates remain gated on a qualified pod-to-host broker path,
reviewed credentials, and runner API connectivity. No public/tailnet route is
configured.
