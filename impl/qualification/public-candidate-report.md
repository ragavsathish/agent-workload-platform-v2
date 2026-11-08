# Slice 22 isolated public candidate: local qualification record

Cites the [acceptance scenarios](../../docs/plan/22-public-v2-candidate/spec.feature),
[candidate build record](../deployment/public-candidate-preparation.md), and
[host inventory](../../raw/v1/public-candidate-host-observation.md).

Status: **prepared-only**. No Tailscale node was authenticated and no public
route was opened. The owner and candidate origin in the private rehearsal
configuration are synthetic placeholders, not verified tailnet identities.

| Evidence | Observed result |
| --- | --- |
| Acceptance red baseline | Eleven `candidate-001` through `candidate-011` checks failed with `NotImplementedError` before implementation. |
| Candidate identity | Compose project `awp-v2-public-candidate-22`; node name `awp-v2-candidate-22`; private state outside both checkouts. |
| Resource preflight | Native ports 9747, 9746, 9743, 9744 free; v1 container IDs, health, and published ports recorded. |
| Image preflight | Pinned Tailscale image ran `tailscale version` as 1.104.4 with Docker network disabled. |
| Isolated dependency | Candidate-only Gitea healthy on loopback 9750; scoped user and repository API each returned HTTP 200; anonymous read of initialization ref succeeded. The repository is only an initialized lifecycle fixture. |
| Local owner route | `voice-006` passed through isolated same-host entry; no untrusted direct-owner bypass was accepted. |
| Local spoken journey | `voice-001` passed with real local WebRTC, Pi, Grain, broker, Parakeet STT, and Kokoro TTS on candidate ports. This is same-host evidence only. |
| Native lifecycle | Two supervised starts reached `stack.py check-ready` using the same candidate state. Both foreground stops released native ports. |
| Existing service continuity | V1 container IDs, essential health, and port bindings matched before, during, and after both native candidate starts. |
| Rollback | The named `candidate-009` check exercised `VersionStore` activation and rollback on disposable release paths; prior active release and owner IDs were preserved. No production release pointer was switched. |
| Repository gate | `harness/verify.sh` passed, including the fourteen slice 22 checks and documentation, hash, runtime topology, and public module checks. |

The release decision remains **prepared-only**. Public qualification needs an
authenticated candidate Tailscale node, its actual MagicDNS origin, the exact
forwarded owner login, policy restricted to that owner device, a separate owner
device that completes media and private Grain comparison, and a foreign or
identity-less peer that is denied while the owner path remains healthy. The
candidate Gitea dependency remains running in its own project for later
qualification; the native candidate and public sidecar are stopped.
