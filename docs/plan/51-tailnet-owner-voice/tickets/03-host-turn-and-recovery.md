# Ticket 03: host-backed spoken turn and recovery

Cites [Slice 51 scenarios](../spec.feature), [live owner conversation rules](../../../wiki/live-session-integration.md), and [deployed voice journey](../../../wiki/deployed-voice-journey.md).

Implement `@voice-tailnet-005`, `@voice-tailnet-007`, and `@voice-tailnet-008`. Carry the authenticated owner and one accepted turn through Edge to the existing host Pi/inference runtime and Grain-selected conversation. Return audible response media and the matching safe transcript. Show host/media failures without claiming success, and recover the settled turn after restart without replay.
