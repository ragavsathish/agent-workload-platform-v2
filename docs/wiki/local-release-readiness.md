# Supervised local release readiness

Cites the [pinned owner workday](../../raw/v1/owner-workday-walk.md),
[source decision wall](../../raw/v1/expanded-storming.md),
[source service lifecycle](../../raw/v1/voice-makefile),
[conversation rules](session-orchestration.md),
[deployed voice rules](deployed-voice-journey.md),
[task delivery rules](task-execution-delivery.md),
[knowledge rules](personal-knowledge.md),
[taxonomy rules](knowledge-taxonomy.md),
[song rules](personal-song.md),
[incident rules](operational-incidents.md), and
[public deployment rules](public-deployment.md).

## One supervised owner journey

A local release is a particular version of independently running services with
private state and an inspectable owner route. A readiness result identifies the
version, services actually observed, source revision, state identity, time, and
the scope of the observation. A status response is an availability observation;
it cannot stand in for a completed owner action. The owner can choose a saved
conversation, complete a spoken turn, submit delegated work, review knowledge,
approve and play a song, and inspect an incident. These are separate journeys:
one passing journey never silently qualifies another. A release report records
each with an exercised, failed, unavailable, or unqualified result and safe
evidence references.

The selected conversation owns each accepted turn. The owner can read the same
safe turn after the conversation service restarts. A completed utterance is not
sent twice. The owner hears non-silent response media for a claimed spoken
success. An attached task receives only its own selected turn. This follows the
[source conversation path](../../raw/v1/voice-agent.ts) and
[source media probe](../../raw/v1/smallwebrtc_voice.py).

An accepted delegated task keeps one request and task identity from admission
through scheduling, execution, result capture, and owner download. The worker
acts in the selected workspace; a restart cannot replay an uncertain effect.
The owner sees a bounded recovery reason or chooses an explicit retry. A result
may have an answer and declared work product. A missing product is an explicit
retrieval failure and does not rewrite the task's terminal result. See the
[source task owner](../../raw/v1/durable-owner.ts) and
[source artifact store](../../raw/v1/task-artifacts.py).

A completed turn can produce a reviewable personal knowledge suggestion.
Unreviewed and rejected suggestions stay out of current owner knowledge.
Accepted categories and relations must remain attributable after a restart;
revocation changes current reads without erasing review history. An owner's
audio generation requires review of the exact complete lyric version before a
separate render. A qualified private song is playable by that owner only. A
selected managed alert may start one bounded investigation; the finding or
failure remains inspectable and grants no authority to change the product.
See [source knowledge review](../../raw/v1/c3-personal-knowledge.feature),
[source song behavior](../../raw/v1/song-service.py), and
[source incident behavior](../../raw/v1/c4-operational-incidents.feature).

## Evidence and authority

The local report uses actual process, state, and owner-interface evidence.
Each journey names its identity and source revision, what was invoked, the
observed outcome, and any missing dependency. Safe hashes and bounded excerpts
can link evidence without copying private speech, prompts, credentials, or
personal facts into the report. A foreign caller must be refused at all owner
reads and actions. A service that is absent or unhealthy is unavailable, not
passing. A test fixture is useful diagnostic evidence but cannot be promoted
to a supervised owner journey.

Local release readiness does not establish separate-device private-network
access, externally built execution images, private historical knowledge
cutover, reviewed model accuracy, subjective song quality, or publication to a
real pull request. Each has its own [public deployment](public-deployment.md),
[task execution](task-execution-delivery.md),
[knowledge continuity](knowledge-extraction-migration.md),
[taxonomy](knowledge-taxonomy.md), or
[pull request soundtrack](pr-soundtrack.md) qualification. The
[source decision wall](../../raw/v1/expanded-storming.md) also marks broader
cross-path change selection and component regeneration as proposed. A local
readiness report must preserve those maturity boundaries.
