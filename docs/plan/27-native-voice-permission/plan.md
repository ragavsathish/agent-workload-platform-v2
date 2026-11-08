# Slice 27: native voice permission timing

Cites the [owner workspace rules](../../wiki/owner-client.md), the
[owner-client acceptance scenarios](../24-owner-client/spec.feature), and the
[pinned v1 client source capture](../../../raw/v1/client-source-capture.md).

## Goal

Ask the operating system for microphone access only when the private workspace
requests microphone media as the owner starts spoken interaction.

## Boundary

The native shell may mediate an origin-validated WebView microphone request.
Workspace reload, navigation retry, startup, and camera requests do not ask for
microphone access. Existing HTTPS origin and same-origin navigation rules remain
in force. This slice does not change the browser permission flow, voice
transport, or server authority.

## Acceptance

- Opening or reloading the native workspace does not request microphone access.
- A workspace-originated microphone request may request OS microphone access
  and is granted only when OS access is granted.
- Camera or mixed camera/microphone requests are denied without requesting OS
  microphone access.
- Retrying after a load or permission error reloads the workspace without
  requesting OS microphone access; a later voice-start request may request it.
- Navigation remains restricted to the configured HTTPS workspace origin.

## Verification

Test that the microphone requester is not invoked before a page microphone
request or for a camera/mixed request. Exercise the load-retry path and confirm
it only reloads. Retain the native client build and launch evidence as a
separate qualification concern; it does not prove real-device prompt timing.

## Documents

- [Domain rules](../../wiki/owner-client.md)
- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-permission-timing.md)
