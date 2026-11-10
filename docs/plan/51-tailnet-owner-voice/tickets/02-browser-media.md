# Ticket 02: browser media path

Cites [Slice 51 scenarios](../spec.feature), [deployed voice journey](../../../wiki/deployed-voice-journey.md), and the [Slice 51 decisions](../decisions.md).

Implement `@voice-tailnet-004` and `@voice-tailnet-006`. Preserve explicit microphone permission timing. Establish browser signaling and bidirectional media from a separate authorized tailnet device to the Edge's Tailscale network interface. Qualify that ICE selects a direct tailnet candidate and that both audio directions work. A page load or signaling response without media is a failure, not completion.
