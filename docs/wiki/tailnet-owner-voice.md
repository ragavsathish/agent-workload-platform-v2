# Tailnet owner voice access

Cites the [EventStorming conversation flow](../../raw/v1/expanded-storming.md), [live owner conversation rules](live-session-integration.md), [deployed voice journey](deployed-voice-journey.md), [owner Edge source](../../raw/v1/edge-bot.py), and [host requirements](../../raw/v1/host-requirements.md).

## Owner access and identity

The owner opens the voice workspace through a private, authenticated network entry. The entry supplies the verified caller identity to the Edge; a caller-supplied identity header is never authority. Edge admits only the configured owner, and Grain continues to enforce owner scope for durable sessions, turns, tasks, and reads. Network membership alone does not grant owner authority.

The browser origin must be the approved secure origin. Microphone access begins only after the owner explicitly starts voice from the page. A route is not usable merely because the page loads: authentication, signaling, bidirectional media, host speech capabilities, turn routing, and the safe owner transcript must all work.

## Live voice boundary

A spoken turn follows the selected conversation and has one accepted identity. It reaches the host voice and inference capabilities through the qualified service boundary, then returns audible response media and the safe transcript for that same turn. The transcript is owner-scoped and remains recoverable after service restart. Network, media, inference, or persistence failures are visible and are never reported as a completed spoken turn.

Private owner access is not public exposure. No public sharing path is implied, and the owner-facing service does not replace the separate operator interface.
