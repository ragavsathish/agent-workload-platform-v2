"""Protocol check for the broker's production Pi forwarding routes; @routing-001 @routing-004."""

import asyncio
import json
import os

import httpx

os.environ.setdefault("INFERENCE_BROKER_TOKEN", "test-broker-token-with-at-least-32-bytes")
os.environ.setdefault("AWP_OWNER_IDENTITY", "owner@example.test")
os.environ.setdefault("OLLAMA_MODEL", "not-used-by-pi")
os.environ.setdefault("ORCHESTRATOR_ALLOWED_USERS", "owner@example.test")
os.environ.setdefault("AGENT_GATEWAY_TOKEN", "test-gateway-token-with-at-least-32-bytes")

from voice_agent.speech.presentation import http as broker  # noqa: E402


async def main() -> None:
    forwarded: list[httpx.Request] = []

    def host(request: httpx.Request) -> httpx.Response:
        forwarded.append(request)
        if request.url.path == "/v1/turn":
            return httpx.Response(200, json={"text": "Pi answered"})
        if request.url.path == "/v1/sessions/new":
            return httpx.Response(201, json={"id": "selected"})
        if request.url.path == "/v1/context":
            return httpx.Response(200, json={"tokens": 19, "canCompact": False})
        if request.url.path == "/v1/context/compact":
            return httpx.Response(200, json={"compactionStatus": "queued"})
        if request.url.path == "/v1/tasks/attach":
            return httpx.Response(200, json={"taskId": "4b860912-01a3-455b-9fa4-7a71c93500ee"})
        if request.url.path == "/v1/tasks/routing":
            return httpx.Response(200, json={"taskId": "4b860912-01a3-455b-9fa4-7a71c93500ee"})
        if request.url.path == "/v1/tasks/detach":
            return httpx.Response(200, json={"status": "detached"})
        return httpx.Response(404, json={"detail": "unexpected route"})

    broker.app.state.http = httpx.AsyncClient(transport=httpx.MockTransport(host))
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=broker.app), base_url="http://broker.test"
    ) as client:
        headers = {"authorization": f"Bearer {broker.BROKER_TOKEN}",
                   "x-awp-turn-id": "same-turn", "x-awp-origin": "typed"}
        response = await client.post("/v1/pi/chat/completions", headers=headers, json={
            "model": "pi-agent", "stream": False,
            "messages": [{"role": "system", "content": "Be concise"},
                         {"role": "user", "content": "hello"}],
        })
        assert response.status_code == 200, response.text
        assert response.json()["choices"][0]["message"]["content"] == "Pi answered"
        turn = forwarded[-1]
        assert turn.headers["authorization"] == f"Bearer {broker.BROKER_TOKEN}"
        assert turn.headers["x-awp-owner"] == "owner@example.test"
        assert turn.headers["x-awp-turn-id"] == "same-turn"
        assert turn.headers["x-awp-origin"] == "typed"
        assert json.loads(turn.content)["messages"][-1]["content"] == "hello"

        selected = await client.post("/v1/pi/sessions/new", headers={
            "authorization": f"Bearer {broker.BROKER_TOKEN}",
            "x-awp-command-id": "same-command",
        })
        assert selected.status_code == 201
        assert selected.json()["id"] == "selected"
        assert forwarded[-1].headers["x-awp-command-id"] == "same-command"

        context = await client.get("/v1/pi/context", headers={
            "authorization": f"Bearer {broker.BROKER_TOKEN}",
        })
        assert context.status_code == 200
        assert context.json()["tokens"] == 19
        assert forwarded[-1].headers["x-awp-owner"] == "owner@example.test"

        compacted = await client.post("/v1/pi/context/compact", headers={
            "authorization": f"Bearer {broker.BROKER_TOKEN}",
            "x-awp-command-id": "stable-compaction-command",
        })
        assert compacted.status_code == 200
        assert compacted.json()["compactionStatus"] == "queued"
        assert forwarded[-1].url.path == "/v1/context/compact"
        assert forwarded[-1].headers["x-awp-command-id"] == "stable-compaction-command"
        assert forwarded[-1].headers["x-awp-owner"] == "owner@example.test"

        task_id = "4b860912-01a3-455b-9fa4-7a71c93500ee"
        attached = await client.post("/v1/pi/tasks/attach", headers={
            "authorization": f"Bearer {broker.BROKER_TOKEN}",
            "x-awp-command-id": "route-command",
        }, json={"taskId": task_id})
        assert attached.status_code == 200, attached.text
        assert attached.json()["taskId"] == task_id
        assert forwarded[-1].url.path == "/v1/tasks/attach"
        assert forwarded[-1].headers["x-awp-command-id"] == "route-command"
        assert forwarded[-1].headers["x-awp-owner"] == "owner@example.test"
        assert json.loads(forwarded[-1].content) == {"taskId": task_id}

        routing = await client.get("/v1/pi/tasks/routing", headers={
            "authorization": f"Bearer {broker.BROKER_TOKEN}",
        })
        assert routing.status_code == 200
        assert routing.json()["taskId"] == task_id
        assert forwarded[-1].url.path == "/v1/tasks/routing"

        detached = await client.post("/v1/pi/tasks/detach", headers={
            "authorization": f"Bearer {broker.BROKER_TOKEN}",
            "x-awp-command-id": "route-detach",
        })
        assert detached.status_code == 200
        assert detached.json()["status"] == "detached"
        assert forwarded[-1].url.path == "/v1/tasks/detach"
        assert forwarded[-1].headers["x-awp-command-id"] == "route-detach"

        denied = await client.post("/v1/pi/chat/completions", json={})
        assert denied.status_code == 401
        assert len(forwarded) == 7

    await broker.app.state.http.aclose()


if __name__ == "__main__":
    asyncio.run(main())
