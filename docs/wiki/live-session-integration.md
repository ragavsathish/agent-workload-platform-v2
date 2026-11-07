# Live owner conversation

Cites the [conversation rules](session-orchestration.md), [pinned source inventory](../../raw/v1/manifest.md), [owner edge](../../raw/v1/edge-bot.py), [broker](../../raw/v1/broker-http.py), [service routes](../../raw/v1/voice-http-server.ts), [service entry point](../../raw/v1/voice-agent-service.ts), [orchestrator](../../raw/v1/voice-agent.ts), [task attachment](../../raw/v1/remote-task-session.ts), and [source journey](../../raw/v1/voice-service.test.mjs).

## Public path

The authenticated owner uses the existing conversation catalog, history, fresh, resume, transcript, typed message, stop, and context controls. A browser voice connection supplies final spoken turns through the same selected conversation and receives streamed assistant speech and presentation. A typed message receives a bounded spoken and presentation result. The browser connection can end while the selected conversation remains available. The outer owner authority and inner service authority are separate. Failed or unknown inner operations do not become successful public replies. See the [owner edge](../../raw/v1/edge-bot.py), [broker](../../raw/v1/broker-http.py), [service routes](../../raw/v1/voice-http-server.ts), and [conversation rules](session-orchestration.md).

## One selected history and one tool environment

The current selection decides where an unattached turn goes. Saved conversation history supplies the resumed turn's context; the public transcript is a safe projection of observed user, assistant, and tool status. Fresh and resume change the selected history and release temporary task routing. The persistent voice tool environment belongs to the service lifetime, not a conversation or voice connection. A turn observes one immutable routing decision even if another control arrives during streaming. See the [orchestrator](../../raw/v1/voice-agent.ts), [service entry point](../../raw/v1/voice-agent-service.ts), and [conversation rules](session-orchestration.md).

## Task routing and interruption

The owner can attach a currently eligible durable task through the conversation's capability. Its following turns use the task's own conversation, and task text may appear in the live transcript. The orchestrator's saved history does not acquire that turn. Selection or voice disconnection releases the temporary attachment without cancelling or renaming the task. Stop and stream disconnection request interruption of the currently routed turn; a lost or uncertain outcome must be inspected before another external dispatch. See the [task capability](../../raw/v1/pi-session.ts), [attachment transport](../../raw/v1/remote-task-session.ts), [orchestrator](../../raw/v1/voice-agent.ts), [service route](../../raw/v1/voice-http-server.ts), and [conversation rules](session-orchestration.md).

## Observable completion

A successful owner turn has an accepted routing identity, safe streamed or final observations, and a settled result. A failure after acceptance remains inspectable with its uncertainty and never silently resubmits the effect. Owner reads after a restart recover the selected identity and saved public history; a lost live task connection is reported as detached. See the [slice 03 decisions](../plan/03-session-orchestration/decisions.md) and [source service tests](../../raw/v1/voice-service.test.mjs).
