# Slice 28: iOS owner client qualification

Cites the [owner-client contract](../24-owner-client/plan.md), its [native
shell scenario](../24-owner-client/spec.feature), the [native permission timing
slice](../27-native-voice-permission/plan.md), and the [pinned v1 client
capture](../../raw/v1/client-source-capture.md).

## Goal

Make the iOS native owner client reproducibly buildable from the v2 checkout
and record evidence that distinguishes compilation from simulator, device,
permission-prompt, and owner-journey qualification.

## Boundary

This slice qualifies the iOS target of the existing Flutter owner client. It
does not change session or task authority, the private workspace origin,
microphone permission timing, voice transport, or v1. A successful simulator
build proves compilation for that target only. Launch, OS permission prompt
timing, and an authenticated owner journey require a simulator runtime or
device and remain separate evidence.

## Acceptance

- The pinned Flutter toolchain can build the client for the iOS Simulator
  without code signing.
- Required Flutter plugins resolve through the checked-in Xcode project
  integration; setup does not require changing the host's system Ruby or
  replacing v1 tooling.
- The app declares the microphone usage description and does not request
  camera access.
- The qualification report records the exact command, tool versions, and
  artifact or failure, and does not claim simulator launch, permission prompt,
  or owner journey unless those are exercised.
- Android evidence, workspace origin restrictions, and the slice 27
  permission timing rule remain intact.

## Documents

- [Domain rules](../../wiki/owner-client.md)
- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-ios-build.md)
