# Build and record the iOS target

Cites the [slice plan](plan.md), [scenarios](spec.feature), and [native
client report](../../../impl/qualification/native-client-report.md).

Build the iOS Simulator target without signing through the pinned Flutter
wrapper. Record Flutter/Xcode versions, build result, and artifact location in
the qualification report. Inspect the generated app metadata for the
microphone usage description and absence of camera permission. Keep runtime,
permission-prompt, and authenticated owner-journey claims open until observed
on a simulator or device.
