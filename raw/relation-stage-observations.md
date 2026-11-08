# Pinned relation proposal observations, 2026-11-08

This records synthetic, owner-safe observations at the bounded model and proposal-to-review boundary. Source text, owner identity, provider responses, and trace payloads are omitted. The source paths are `impl/qualification/grounded-relations-report.md`, `/tmp/awp-v2-slice19-real-diagnostic.log`, `/tmp/awp-v2-slice19-real-smoke.log`, `/tmp/awp-v2-slice19-real-smoke2.log`, and the slice 20 synthetic diagnostic logs. Temporary logs are local observations, not committed evidence.

| Run | Boundary | Model result | Durable attempt | Pending | Current | Elapsed |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| 19-A | isolated model sheet | completed, one candidate | 0 | 0 | 0 | 122.6 s |
| 19-B | full proposal call | failed | 1 | 0 | 0 | 80.8 s |
| 19-C | full proposal call | failed | 1 | 0 | 0 | 56.4 s |
| 20-A | full proposal call | completed, one candidate; staged | 1 | 1 | 0 | live run |
| 20-B | full proposal call | completed, one candidate; staged | 1 | 1 | 0 | live run |

The slice 19 full proposal attempts had trace references but no safe recorded failure category. Slice 20 replayed the failed response at the actual proposal boundary: `{:status :failed, :attempt-count 1, :pending-count 0, :published-count 0}`. The same boundary with a grounded completed response produced `{:status :staged, :attempt-count 1, :pending-count 1, :published-count 0}`. These replay responses are synthetic fixtures; they cannot establish model reliability. One slice 20 live process retained background activity after it printed the completed result and required interruption. The underlying cause of the slice 19 failures is not established by these observations.
