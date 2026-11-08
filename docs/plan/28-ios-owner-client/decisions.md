# Decisions

Cites the [owner-client rules](../../wiki/owner-client.md), [native
permission timing](../27-native-voice-permission/plan.md), and [source client
capture](../../raw/v1/client-source-capture.md).

## D110 — Simulator compilation is separate from runtime qualification

An iOS Simulator build proves only that the target compiles and links. It does
not prove the app launches, displays the configured private origin, requests
microphone permission at voice start, avoids camera access at runtime, or
completes an authenticated owner journey. Each stronger claim needs its own
observed evidence.

## D111 — Use the project’s declared plugin integration

The iOS project resolves Flutter plugins through its generated Swift package
integration. Build qualification follows that project configuration; it does
not introduce a CocoaPods requirement or replace the host's system Ruby.
