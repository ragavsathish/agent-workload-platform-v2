# Slice 05: deployed owner voice journey

Cites [deployed voice rules](../../wiki/deployed-voice-journey.md), [live conversation rules](../../wiki/live-session-integration.md), [slice 04 decisions](../04-live-session-integration/decisions.md), and the [pinned voice testing guide](../../../raw/v1/voice-testing.md).

## Goal and boundary

Prove an authenticated owner's final spoken utterance travels as live media through the public voice connection, becomes one accepted turn for the selected conversation, and returns audible speech plus a safe transcript. After a fresh service start, the owner can resume and read that turn. Include a following turn routed to an attached eligible task only when the deployed task capability is present.

The boundary begins at owner access and the live media offer, crosses recognition, the bounded conversation path, selected history, and speech output, and ends at owner-visible audio and saved public read. A protocol peer may stand in for the physical microphone and browser. It must send timed audio and receive response media. Text injected after recognition cannot satisfy the spoken scenario. The [acceptance scenarios](spec.feature) define the journey; [implementation notes](../../../impl/deployed-voice-journey-notes.md) record setup and topology.

## Tickets

- [Ticket 01](ticket-01-public-media.md): live media turn and owner response.
- [Ticket 02](ticket-02-recovery-and-task.md): restart, task attachment, and interruption at the same public boundary.
- [Ticket 03](ticket-03-deployed-qualification.md): reproducible deployment setup, fresh journey evidence, and capability-specific qualification.

## Exit evidence

Each scenario receives a named failing acceptance run before implementation. A passing local protocol journey can qualify routing and media only for the providers it actually ran. Deployed qualification requires a fresh public media journey with real recognition and speech output, a persistent service restart and read, and a separately observed task or tool execution if claimed. Record the selected identity, accepted turn identity, provider configuration without secrets, response media, transcript, task identity where relevant, and failure diagnostics. A configuration inspection alone is not journey evidence. See [deployed voice rules](../../wiki/deployed-voice-journey.md).
