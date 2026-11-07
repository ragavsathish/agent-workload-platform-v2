# Ticket 01: public selection and query path

Cites the [slice plan](plan.md), [scenarios](spec.feature), [owner edge](../../../raw/v1/edge-bot.py), [broker](../../../raw/v1/broker-http.py), [service routes](../../../raw/v1/voice-http-server.ts), and [selection rules](../../wiki/session-orchestration.md).

Implement the owner-facing fresh, resume, catalog, historical read, active transcript, context read, and maintenance controls through the selected conversation authority. Preserve the public path and bounded response shapes, owner authentication, separate inner credential, missing and busy answers, and the selected identity after restart. Prevent a successful selection response when the chosen saved context cannot be opened. Prove `live-001`, `live-006`, the control portion of `live-008`, and `live-009` through public operations.
