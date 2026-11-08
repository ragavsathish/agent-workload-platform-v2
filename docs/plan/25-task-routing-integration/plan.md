# Slice 25: owner task routing

Cites the [task routing rules](../../wiki/task-routing.md), the [session contract](../03-session-orchestration/spec.feature), the [owner client contract](../24-owner-client/spec.feature), and the [captured v1 inventory](../../../raw/v1/manifest.md).

## Goal

Let the owner attach an eligible durable task to the active conversation and route later turns to it. The task keeps its own identity and history while the selected conversation remains unchanged. The owner can see the current route and explicitly detach it.

## Boundary

The slice starts at the owner task control and ends with an owner-visible routing decision, a routed task turn, a return to the selected conversation, or a safe uncertain result. Both conversation and task ownership are checked. Attachment does not move task turns into the selected conversation's saved history.

The conversation event record and live task socket are separate authorities; they do not commit atomically. The implementation must fail closed when they disagree, preserve enough command identity to reconcile retries, and never claim that the two effects are one transaction. Selection and service shutdown release temporary routing. Restart reports detached routing rather than recreating a live socket.

## Acceptance

- An owner can attach and detach an eligible task from the task view.
- A foreign or terminal task cannot be attached.
- The task identity recorded by the conversation authority matches the live task destination before a turn is dispatched.
- Retrying a completed command with the same identity and input converges; a conflicting reuse is refused.
- Failed or unknown effects remain uncertain, and following turns are refused until routing is reconciled.
- Selection, voice shutdown, and restart release temporary routing without changing task identity or task history.
- The selected conversation transcript excludes task turns, while the live public transcript shows the attached task reply.

## Verification

Exercise commands through the owner, gateway, and task-session boundaries with local protocol peers. Include foreign-owner and terminal-task refusals, lost-response retry, conflicting command identity, failed effect recovery, selection release, shutdown, and clean restart. Do not use a live deployment as local acceptance evidence.

## Documents

- [Behavior scenarios](spec.feature)
- [Decision record](decisions.md)
- [Single vertical ticket](ticket-01-routed-owner-turn.md)
