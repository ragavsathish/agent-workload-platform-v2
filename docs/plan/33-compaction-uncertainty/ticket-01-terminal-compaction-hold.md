# Make unproven compaction visible without unlocking it

Use the [Slice 33 plan](plan.md), [scenarios](spec.feature), [decisions](decisions.md),
[session behavior model](../../wiki/session-orchestration.md),
[session command implementation](../../../impl/src/awp_v2/session/commands.clj),
[Pi runtime](../../../impl/runtime/products/voice-agent/apps/pi-agent/voice-agent/voice-agent.ts),
and [owner client](../../../impl/runtime/products/voice-agent/apps/client/lib/models.dart).

Add a durable `uncertain` compaction state and bounded owner-visible reason.
Mark unresolved pending work uncertain during restart recovery. Permit retries
to reconcile from the Pi branch without claiming or invoking a new effect.
Allow a proven completion to settle from uncertain state. Keep the owner lock
held on every unproven outcome; there is no override or blind release.

Run focused session acceptance tests, the Pi live protocol suite, the
owner client tests for the changed view/model, formal contract checks, docs and
link gates, and whitespace checks. Verify no second Pi effect and no cross-owner
leak. Preserve current external authorities and do not modify v1.
