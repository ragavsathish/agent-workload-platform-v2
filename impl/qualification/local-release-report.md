# Slice 15: supervised local release qualification

Candidate base: `3fcb8128e91e2cff409569e62c5e531e22bcb7a7`. Private state identity: `awp-v2-slice15-46646374`. The services ran on loopback with independent private task, session, knowledge, song, and incident state. The full evidence ledger is the mode-0600 file `/tmp/awp-v2-slice15-46646374/report.json`; no credential values or owner transcript are committed.

| Scenario | Result | Direct observation |
| --- | --- | --- |
| release-001 readiness | Exercised | Grain, Pi, broker, gateway returned ready; scheduler and local Pipecat edge were live. |
| release-002 voice | Exercised | Two live local aiortc RTP checks passed, including a selected turn after Pi restart and a matching broker dispatch. |
| release-003 task/product | Exercised | Scratch task `5a0fea33-24a6-5ee6-a423-428132b84112` completed; owner downloaded 168-byte workspace ZIP with SHA-256 `7ef54a032a506b1c3dd1189da0dfb45732752a2f68c15ff12c0577e97baa00e2` and the requested marker. |
| release-004 knowledge | Exercised | Native worker yielded a pending journal item; owner accepted it. Two source-backed taxonomy proposals and their relation were accepted through owner routes. A fresh Grain read returned two current concepts and one relation, with two ORC concepts. Pending material was absent from current reads. |
| release-005 song | **Unqualified** | Owner approval, render refusal before approval, ACE-Step render, and private playback worked. The 30-second AAC was 978,289 bytes, SHA-256 `2b115c034bbdf83a2af273fd50c114e5cf42383b14b714ad906e2502525fe959`. The lyric manifest was injected because no durable Pi lyric owner endpoint was configured. |
| release-006 incident | Exercised locally | A loopback managed-alert fixture produced one stable Grain incident, one completed scratch investigation task, and an owner-inspectable bounded finding. This does not qualify deployed Grafana or metric meaning. |
| release-007 restart | Exercised | Pi restart retained the selected turn; full supervisor restart retained task, accepted knowledge, ORC concepts, relation, and incident without duplicate epoch. |
| release-008 recovery | Exercised | A running worker interrupted by scheduler termination became pending with `recovery_required=true`; it stayed there without automatic retry, then completed after explicit owner retry. |
| release-009 isolation | Exercised | A foreign identity received 403 for task, artifact, knowledge, taxonomy, song, audio, and incident owner reads. |
| release-010 honesty | Exercised | The missing lyric owner dependency leaves the song journey unqualified in the ledger and named acceptance check. |
| release-011 scope | Exercised | The report keeps external gates open. |

The opt-in acceptance run has 10 passes and the expected `release-005` failure. It must not be described as a fully qualified release. The earlier red baseline had 11 errors because the report module did not yet exist. The local Clojure test suite passed with 136 tests and 524 assertions after the taxonomy route change.

Separate-device tailnet media, external Gitea/image build, private v1 migration, model quality, deployed Grafana evidence, and pull request qualification remain open. The edge process ran separately on loopback, so this proof does not establish deployed supervision of that component. The v1 deployment was untouched.
