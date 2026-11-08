# Slice decisions

Cites the [plan](plan.md), [owner workspace rules](../../wiki/owner-client.md), and [public entry rules](../../wiki/public-deployment.md).

## D01: the edge image owns the browser build

The image build receives the generated Flutter web bundle and places it at the directory consumed by the Edge page and asset routes. Runtime health alone cannot qualify owner entry when the bundle is absent.

## D02: preparation fails before candidate start

The candidate preflight checks that the web output exists and that the edge image declares the matching copy. Missing build inputs are reported as a prerequisite failure before Compose starts.

## D03: local page checks remain local evidence

A loopback page and owner API check proves local packaging and route wiring only. It does not establish Tailscale identity, public origin, separate-device media, or foreign denial.

## Red evidence recorded before implementation

The named check `test_candidate_012_edge_image_contains_flutter_web_bundle` failed because the edge build file had no browser-bundle copy rule. The live loopback check also returned 503 for `/` with `client build is missing`, while `/api/features` and the owner APIs returned 200. These observations establish the packaging defect without qualifying public access.
