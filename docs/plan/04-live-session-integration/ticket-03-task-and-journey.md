# Ticket 03: task routing and full journey

Cites the [slice plan](plan.md), [scenarios](spec.feature), [task capability](../../../raw/v1/pi-session.ts), [task transport](../../../raw/v1/remote-task-session.ts), [service entry point](../../../raw/v1/voice-agent-service.ts), and [orchestrator](../../../raw/v1/voice-agent.ts).

Attach an eligible owned durable task and route following turns to its conversation while retaining the orchestrator selection and saved history boundary. Selection, voice disconnect, and service shutdown release the live attachment without changing task identity. Keep one tool environment per service lifetime across fresh and resume. Complete the owner journey from public control and voice connection to saved read after a fresh start. Prove `live-004`, `live-005`, and the tool ownership assertion in `live-001`; run the independent full-path gate.
