# Slice 52: package and route the voice workspace UI

## Goal

Every page and asset referenced by the deployed Flutter voice workspace resolves through its private HTTPS origin. Operations views share the same owner-authenticated Tailscale ingress.

## Scope

- Build the MCP app pages, audio diagnostics, and knowledge UI during the Docker-only k3d bootstrap.
- Include Flutter favicon and PWA icons in the Edge image and serve them at their referenced paths.
- Route Grafana below `/metrics/` and Oxigraph below `/oxigraph/` through the existing owner-authenticated Traefik entry.
- Keep public ingress disabled and avoid exposing Grafana or Oxigraph by Pod IP, ClusterIP, or a new host port.

## Acceptance

See [Gherkin scenarios](spec.feature). The bootstrap-built Edge image must serve each referenced page and icon; Grafana and Oxigraph requests must use the private HTTPS origin and pass through the owner ForwardAuth middleware.

## Implementation notes

The v2 Edge image previously copied only Flutter's `build/web` directory, while the bridge references additional built pages under `/mermaid`, `/marp`, `/trace-explainer`, `/journal-review`, `/excalidraw`, `/diagnostics`, and `/knowledge`. The image also registered `favicon.svg` even though the Flutter page references `favicon.png`, and did not mount PWA icons. The compiled static apps are shipped under the Flutter `web` directory; bootstrap builds Flutter in a Docker container and copies those pages into the Edge image.

## Verification

Record the private in-Pod HTTP status for the root page, favicon aliases, PWA icons, all referenced app pages, Grafana dashboard route, and Oxigraph query route. Verify an unauthorized tailnet identity is refused by the same ForwardAuth boundary. Do not record credentials, dashboard contents, or owner data.
