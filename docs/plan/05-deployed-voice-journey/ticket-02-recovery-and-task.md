# Ticket 02: restart, interruption, and task routing

Cites the [slice plan](plan.md), [scenarios voice-002, voice-003, and voice-004](spec.feature), [deployed voice rules](../../wiki/deployed-voice-journey.md), and [source orchestrator](../../../raw/v1/voice-agent.ts).

After a real spoken turn, start the service fresh and read the selected identity and safe history through public controls. Interrupt a streaming turn by ending its live connection, then verify the same identity cannot prompt twice. With an eligible owned task, route a following spoken turn to that task's conversation and retain the saved orchestrator history boundary. Record failing then passing acceptance runs for `voice-002`, `voice-003`, and `voice-004`.
