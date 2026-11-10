# Slice 52 tickets

- [x] Find the v1 sources and build scripts for UI assets omitted from v2.
- [x] Build the MCP, diagnostics, and knowledge bundles during bootstrap.
- [x] Serve the Flutter favicon and PWA icons at their referenced paths.
- [x] Route Grafana and Oxigraph below the owner-authenticated private ingress.
- [ ] Rebuild/reconcile the Edge and verify the private route matrix.
- [ ] Record the deployed revision and route results.
- [x] Rebuild Edge image `awp-v2-voice-edge:slice52`, import into `k3d-awp-v2-ops`, and reconcile Flux revision `main@sha1:1bd00cb527ebc4834ccb574826ab4c15bff3df71`; `voice-edge` rolled out with 3/3 containers ready.
- [x] Probe the deployed Edge container: `/`, `/favicon.png`, `/favicon.ico`, `/icons/Icon-192.png`, `/manifest.json`, `/mermaid/index.html`, `/marp/marp.html`, `/trace-explainer/main.html`, `/journal-review/main.html`, `/excalidraw/mcp-app.html`, `/diagnostics/`, and `/knowledge/` all returned HTTP 200.
- [x] Check Grafana subpath behavior: `/api/health` returned 200 and `/metrics/` redirected to `/metrics/login` (302), confirming the route no longer returns 404. Its router retains `owner-only` ForwardAuth.
- [x] Query the Edge HTTP status metric in Prometheus. The historical generic 404 and `/favicon.svg` 503 are from the pre-fix Edge process. The remaining `/api/pi-task-routing` 404 comes from the host broker contract; `/api/pi-turn/latest` is also unavailable at that host boundary (502). These are outside the static UI routing fix and remain separately visible.
