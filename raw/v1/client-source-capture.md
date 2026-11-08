# Owner client source capture

The [client snapshot](client/) captures the v1 Flutter library, unit tests,
package metadata, bundled fonts, browser bridge source and tests, presentation
sandbox tests, and bridge build script from commit
`d8afdfc31ea3ac7d74eef625c8863f5954b75fcd`. The source client path had no
worktree changes at capture time. Generated build and dependency caches are
excluded. File checksums are in [client/SHA256SUMS](client/SHA256SUMS).

The configured private tailnet host was replaced by `voice.example.invalid` in
four copied files: `client/lib/platform/embedded_navigation_policy.dart`,
`client/README.md`, `client/test/transcript_view_test.dart`, and
`client/bridge/voice_bridge.test.mjs`. The snapshots preserve behavior and the
navigation boundary while omitting that environment-specific identifier.
