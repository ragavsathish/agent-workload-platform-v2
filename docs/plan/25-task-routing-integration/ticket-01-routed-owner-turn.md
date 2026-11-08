# Ticket 01: route an owner turn to a durable task

Cites the [slice plan](plan.md), [scenarios](spec.feature), the [session contract](../03-session-orchestration/spec.feature), the [owner client contract](../24-owner-client/spec.feature), and the [captured v1 source inventory](../../../raw/v1/manifest.md).

Expose an owner task attach, route inspection, and detach operation through the existing authenticated surfaces. Validate task ownership and eligibility before changing the route. Coordinate the live task connection with the recorded conversation route, retain retry identity, fail closed if they diverge, and release on selection and service shutdown. Add end-to-end protocol evidence for every scenario in this slice and include a restart check that reports detached routing.

Keep artifact access separate from routing. Do not include task messages in the selected conversation's saved history. Do not imply that a route fact and a socket change commit atomically.
