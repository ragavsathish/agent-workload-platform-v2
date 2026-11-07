# Ticket 01: owner entry and external media

Cites the [slice plan](plan.md), [public deployment rules](../../wiki/public-deployment.md), [scenarios](spec.feature), [source owner routes](../../../raw/v1/traefik-routes.yml), and [host contract](../../../raw/v1/host-requirements.md).

Implement the one-owner public entry and protected voice route. Resolve owner authority from a trusted connection before signaling and reads; reject caller-supplied identity, local-access markers, and use of inner credentials as owner proof. Establish a reachable live media path for a separate device. Record signaling and media outcomes independently. Acceptance: `deploy-001` through `deploy-004`, including timed incoming audio, non-silent outgoing audio, a selected turn, and an explicit media failure.

The build first records named red results. A local route may exercise authorization logic and failure handling; the separate-device public media pass needs the external prerequisites listed in the [implementation note](../../../impl/public-deployment-notes.md).
