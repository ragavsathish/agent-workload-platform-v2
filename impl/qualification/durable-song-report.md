# Slice 16: real durable owner song qualification

Cites the [slice 16 plan](../../docs/plan/16-durable-song-owner/plan.md), [release-005 scenario](../../docs/plan/15-local-release-readiness/spec.feature), and [focused qualifier](qualify_durable_song.py).

The fresh supervised state `/tmp/awp-v2-slice16-real-004` used a private owner ledger, task and song streams, Pi sessions, and audio directory. The mode-0600 evidence ledger is `report.json` in that state. It contains no credentials or lyric text. The owner request was `slice16-lyric-request` in the `scratch` workspace.

| Boundary | Direct observation |
| --- | --- |
| Durable lyric task | Task `471fe476-d7b8-3f86-902e-d9fdb9dbf049` was accepted, claimed once, and completed in the owner ledger. Its result named the matching generation and owner and supplied the structured manifest. |
| Review | Owner read the complete manifest and digest `520723b2c9553dca3db50c99f26b3266661f7965af7b523ad50aede715baee02`; no audio path existed then. Render before approval was refused. |
| Later decision | A separate owner request recorded approval ID `30efb0f3-8b86-47d2-9a41-d1552070ce86` against the exact digest. |
| Render and playback | A separate render task `1a5078c9-e36c-3bba-8578-68b677ff4963` completed. Owner playback returned 708,039 bytes with SHA-256 `4d9084d6945ca17346e042ac91979596ea4970564d097a94014151e94f5329a7`; a foreign caller received 403. |
| Media | AAC, 48 kHz stereo, 30.0 seconds, mean volume −15.7 dB. The technical non-silence check passed. |

The focused `release-005` acceptance test independently read the owner ledger and audio bytes from the same state and passed. A prior fresh run also completed the same real sequence; its evidence is under `/tmp/awp-v2-slice16-real-003`. The slice 15 report remains a historical unqualified run because that run supplied the manifest directly. The slice 16 report qualifies the **local song journey**; it does not convert unrelated slice 15 journeys into one new selected release.

Subjective listening, lyric intelligibility, external tailnet playback, and a real pull request remain separately unqualified. The owner protocol and Grain song authority now refuse result injection through their HTTP surfaces; the local test helper remains available only inside the module test process.
