# Owner client source capture

The [client snapshot](client/) captures the v1 Flutter library, its unit tests,
package metadata, and bundled font assets from commit
`d8afdfc31ea3ac7d74eef625c8863f5954b75fcd`. The source client path had no
worktree changes at capture time. Generated build and dependency caches are
excluded. File checksums are in [client/SHA256SUMS](client/SHA256SUMS).

The configured private tailnet host was replaced by `voice.example.invalid` in
three copied files: `client/lib/platform/embedded_navigation_policy.dart`,
`client/README.md`, and `client/test/transcript_view_test.dart`. The snapshots
therefore preserve behavior and the navigation boundary while omitting that
environment-specific identifier.
