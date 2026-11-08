# Slice 38: package the owner browser workspace in the edge image

Cites the [owner workspace rules](../../wiki/owner-client.md), the [public entry rules](../../wiki/public-deployment.md), the [candidate deployment plan](../22-public-v2-candidate/plan.md), and the [deployment runbook](../../../impl/deployment/README.md).

## Goal

Make the authenticated entry serve the complete owner workspace and refuse candidate preparation when its required browser bundle is absent.

## Boundary

The slice begins with the generated browser bundle and ends with the owner page and its required assets served by the candidate. A candidate without the complete workspace is unavailable; health checks and API routes alone do not qualify browser entry. Candidate preparation checks both the source bundle and its packaging contract.

## Acceptance

- The candidate package includes the browser build in the directory served by the owner entry.
- The owner page and its required browser bootstrap assets return successfully from the deployed edge.
- Candidate preflight reports a missing web build or missing image packaging rule before starting the candidate.
- The deployment runbook names the required browser build step before package creation.
- No public exposure or owner qualification is inferred from a loopback page check.

## Verification

Record a named red check for the current missing asset copy. Verify packaging and preflight rejection, build the candidate package when available, and check the served page and required assets through the loopback-only owner route. Run the candidate checks, documentation, formal specifications, local links, and whitespace gates. External identity and separate-device media remain separate public gates.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-package-owner-browser.md)
