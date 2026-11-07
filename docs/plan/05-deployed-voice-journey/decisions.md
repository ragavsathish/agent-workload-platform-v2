# Slice 05 decisions and qualification boundary

Cites the [slice plan](plan.md), [deployed voice rules](../../wiki/deployed-voice-journey.md), [slice 04 decisions](../04-live-session-integration/decisions.md), and [pinned testing guide](../../../raw/v1/voice-testing.md).

## D18: live media is required for spoken qualification

The [testing guide](../../../raw/v1/voice-testing.md) defines a timed audio peer that retains recognition and response speech. The [previous slice](../04-live-session-integration/plan.md) exercised streaming conversation protocol without a media peer. This slice requires media both ways before calling a turn spoken. The pruned branch was counting direct text or a streamed model response as proof of voice.

## D19: report capability-specific evidence

The [host requirements](../../../raw/v1/host-requirements.md) distinguish voice, durable task, and full tool environments. A successful public speech journey qualifies only the capabilities actually exercised. The pruned branch was treating a service health check or a mock task socket as proof that a persistent tool environment or deployed task worker booted.

## Open: deployed assets and owner ingress

The [voice environment example](../../../raw/v1/voice-env.example) and [host requirements](../../../raw/v1/host-requirements.md) identify model assets, credentials, owner identity, and service configuration that are external to this repository. The build must record actual availability and refuse a full deployment claim until a fresh public media journey runs against them. Exact setup lives in the [implementation note](../../../impl/deployed-voice-journey-notes.md).
