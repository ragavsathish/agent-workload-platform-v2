# Slice decisions

Cites the [slice plan](plan.md), [session command identity decision](../03-session-orchestration/decisions.md#d08-explicit-command-identity-and-uncertainty), and [Slice 35](../35-stable-owner-turn-retry/decisions.md).

## D01: the session authority supplies recovery identity

The session projection already owns turn identity and status. The workspace recovers the latest typed receipt from that owner-scoped projection and matches the turn identity to the user entry in the safe transcript. It does not persist prompt text or provider output in browser storage.

## D02: recovery is observation, not replay

Completed answers come from the durable transcript. A still-accepted or uncertain turn is restored as unresolved. Submitting the identical payload with its original ID may query the existing session decision, but never claims a new external effect; changed content cannot reuse the ID.

## D03: receipt privacy and scope

The recovery query returns only fields required to correlate a turn, its outcome, and the selected conversation. It excludes prompt text, payload fingerprints, tool inputs/results, and provider details. Task-routed turns are not part of the orchestrator's typed-turn receipt.
