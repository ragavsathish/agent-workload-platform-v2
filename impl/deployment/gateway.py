"""Single-owner, bearer-protected v2 task gateway backed by Grain."""
from __future__ import annotations

import json
import os
import uuid
import urllib.request
import urllib.error
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

from deployment import GrainTaskClient


def task_public(task: dict) -> dict:
    task_id = str(task.get("task-id", task.get("task_id", "")))
    progress = task.get("progress") or []
    return {"id": task_id, "task_id": task_id,
            "instruction": task.get("instruction", ""),
            "workspace": task.get("workspace", ""),
            "status": task.get("status", "pending"),
            "recovery_required": task.get("recovery-required?", False),
            "progress": [{"id": item.get("cursor", 0),
                          "spoken": item.get("summary", "Progress updated.")}
                         for item in progress],
            "result": task.get("result")}


class GatewayHandler(BaseHTTPRequestHandler):
    server: "GatewayServer"

    def log_message(self, format, *args):
        # Task paths and payloads do not enter logs.
        pass

    def _send(self, code: int, value: object) -> None:
        body = json.dumps(value).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _send_audio(self, body: bytes) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "audio/mp4")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self) -> dict:
        length = int(self.headers.get("Content-Length", "0"))
        if length < 0 or length > 131_072:
            raise ValueError("request too large")
        value = json.loads(self.rfile.read(length) or b"{}")
        if not isinstance(value, dict):
            raise ValueError("invalid body")
        return value

    def _knowledge(self, parts: list[str], body: dict) -> None:
        operation = None
        payload: dict = {}
        if parts == ["v1", "knowledge", "suggestions"] and self.command == "GET":
            operation = "suggestions"
        elif parts == ["v1", "knowledge", "profile"] and self.command == "GET":
            operation = "profile"
        elif len(parts) == 4 and parts[:3] == ["v1", "knowledge", "items"] and self.command == "GET":
            operation, payload = "item", {"item-id": parts[3]}
        elif len(parts) == 5 and parts[:3] == ["v1", "knowledge", "items"] and parts[4] == "review" and self.command == "POST":
            operation = "review"
            payload = {"item-id": parts[3], "action-id": body.get("actionId"),
                       "expected-revision": body.get("expectedRevision"),
                       "decision": body.get("decision"), "text": body.get("text")}
        elif parts == ["v1", "knowledge", "daily-accounts"] and self.command == "GET":
            operation = "accounts"
        elif parts == ["v1", "knowledge", "daily-accounts"] and self.command == "POST":
            operation = "daily-request"
            payload = {"journal-date": body.get("journalDate"), "time-zone": body.get("timeZone"),
                       "sources": [{"source-ref": item.get("sourceRef"), "speaker": item.get("speaker"),
                                    "captured-at": item.get("capturedAt"), "transcript": item.get("transcript")}
                                   for item in body.get("sources", [])]}
        elif len(parts) == 4 and parts[:3] == ["v1", "knowledge", "daily-accounts"] and self.command == "GET":
            operation, payload = "account", {"account-id": parts[3]}
        elif len(parts) == 5 and parts[:3] == ["v1", "knowledge", "sources"] and parts[4] == "tasks" and self.command == "GET":
            operation, payload = "tasks", {"source-id": parts[3]}
        if operation is None:
            self._send(404, {"detail": "not found"})
            return
        request = urllib.request.Request(
            f"{self.server.grain.base_url}/internal/knowledge/{operation}",
            data=json.dumps(payload).encode(), method="POST",
            headers={"Authorization": f"Bearer {self.server.grain.token}",
                     "X-AWP-Owner": self.server.owner, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                self._send(response.status, json.load(response))
        except urllib.error.HTTPError as error:
            try:
                value = json.load(error)
            except (ValueError, OSError):
                value = {"detail": "knowledge request failed"}
            self._send(error.code, value)
        except OSError:
            self._send(503, {"detail": "knowledge authority unavailable"})

    def _song(self, parts: list[str], body: dict) -> None:
        operation = None
        payload: dict = {}
        if parts == ["v1", "music", "generations"] and self.command == "POST":
            operation = "create"
            payload = {"request-id": self.headers.get("Idempotency-Key") or str(uuid.uuid4()),
                       "brief": body.get("brief"), "style": body.get("style"),
                       "duration-seconds": body.get("durationSeconds"),
                       "workspace": body.get("workspace") or os.environ.get("AWP_DEFAULT_WORKSPACE")}
        elif len(parts) >= 4 and parts[:3] == ["v1", "music", "generations"]:
            payload = {"generation-id": parts[3]}
            if len(parts) == 4 and self.command == "GET": operation = "get"
            elif len(parts) == 5 and parts[4] == "render" and self.command == "POST": operation = "render"
            elif len(parts) == 5 and parts[4] == "cancel" and self.command == "POST": operation = "cancel"
            elif len(parts) == 5 and parts[4] == "approve" and self.command == "POST":
                operation = "decide"
                payload.update({"decision-id": body.get("decisionId"),
                                "manifest-digest": body.get("manifestDigest"),
                                "choice": body.get("choice")})
            elif len(parts) == 5 and parts[4] == "audio" and self.command == "GET": operation = "audio"
        if operation is None:
            self._send(404, {"detail": "not found"})
            return
        request = urllib.request.Request(
            f"{self.server.grain.base_url}/internal/song/{operation}",
            data=json.dumps(payload).encode(), method="POST",
            headers={"Authorization": f"Bearer {self.server.grain.token}",
                     "X-AWP-Owner": self.server.owner, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                if operation == "audio":
                    audio = response.read(128 * 1024 * 1024 + 1)
                    if len(audio) > 128 * 1024 * 1024: raise ValueError("audio too large")
                    self._send_audio(audio)
                else:
                    self._send(response.status, json.load(response))
        except urllib.error.HTTPError as error:
            try: value = json.load(error)
            except (ValueError, OSError): value = {"detail": "song request failed"}
            self._send(error.code, value)
        except OSError:
            self._send(503, {"detail": "song authority unavailable"})

    def _incident(self, parts: list[str]) -> None:
        if parts == ["v1", "incidents"] and self.command == "GET":
            operation, payload = "list", {}
        elif len(parts) == 3 and parts[:2] == ["v1", "incidents"] and self.command == "GET":
            operation, payload = "get", {"epoch-id": parts[2]}
        else:
            self._send(404, {"detail": "not found"})
            return
        request = urllib.request.Request(
            f"{self.server.grain.base_url}/internal/incident/{operation}",
            data=json.dumps(payload).encode(), method="POST",
            headers={"Authorization": f"Bearer {self.server.grain.token}",
                     "X-AWP-Owner": self.server.owner, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                self._send(response.status, json.load(response))
        except urllib.error.HTTPError as error:
            try: value = json.load(error)
            except (ValueError, OSError): value = {"detail": "incident request failed"}
            self._send(error.code, value)
        except OSError:
            self._send(503, {"detail": "incident authority unavailable"})

    def _handle(self) -> None:
        if self.path == "/health":
            self._send(200, {"status": "ready"})
            return
        if self.headers.get("Authorization") != f"Bearer {self.server.token}":
            self._send(401, {"detail": "unauthorized"})
            return
        parsed = urlsplit(self.path)
        parts = parsed.path.strip("/").split("/")
        if parts[0] != "v1" or len(parts) < 2 or parts[1] not in {"tasks", "knowledge", "music", "incidents"}:
            self._send(404, {"detail": "not found"})
            return
        query = parse_qs(parsed.query)
        body = self._body() if self.command == "POST" else {}
        identity = body.get("identity") or query.get("identity", [None])[0]
        if identity != self.server.owner:
            self._send(403, {"detail": "owner denied"})
            return
        if parts[1] == "knowledge":
            self._knowledge(parts, body)
            return
        if parts[1] == "music":
            self._song(parts, body)
            return
        if parts[1] == "incidents":
            self._incident(parts)
            return
        client = self.server.grain
        if len(parts) == 2 and self.command == "POST":
            instruction, workspace = body.get("instruction"), body.get("workspace")
            if not isinstance(instruction, str) or not isinstance(workspace, str):
                raise ValueError("instruction and workspace required")
            key = self.headers.get("Idempotency-Key")
            request_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{identity}/{key}")) if key else str(uuid.uuid4())
            result = client.call("submit", {"request-id": request_id,
                                            "instruction": instruction, "workspace": workspace})
            if "task-id" not in result:
                self._send(409, {"detail": "task admission refused"})
            else:
                task = client.call("read", {"task-id": result["task-id"]})
                self._send(201, task_public(task))
            return
        if len(parts) == 2 and self.command == "GET":
            tasks = [task_public(item) for item in client.call("owned-list")["tasks"]]
            name = query.get("session_name", [None])[0]
            if name:
                tasks = [task for task in tasks if task.get("session_name") == name]
            limit = query.get("limit", [None])[0]
            self._send(200, tasks[:max(1, min(100, int(limit)))] if limit else tasks)
            return
        if len(parts) >= 3:
            task_id = parts[2]
            try:
                uuid.UUID(task_id)
            except ValueError:
                raise ValueError("invalid task identity")
            if len(parts) == 3 and self.command == "GET":
                task = client.call("read", {"task-id": task_id})
                self._send(404 if "error" in task else 200, task_public(task) if "error" not in task else {"detail": "not found"})
                return
            if len(parts) == 4 and parts[3] == "progress" and self.command == "GET":
                after = int(query.get("after", [0])[0])
                entries = client.call("owned-progress", {"task-id": task_id, "after": after, "limit": 100})
                self._send(200, [{"id": item["cursor"], "spoken": item["summary"]} for item in entries])
                return
            if len(parts) == 4 and parts[3] in {"cancel", "retry"} and self.command == "POST":
                result = client.call("abort" if parts[3] == "cancel" else "retry", {"task-id": task_id})
                self._send(200, result)
                return
        self._send(404, {"detail": "not found"})

    def do_GET(self) -> None:
        try: self._handle()
        except (ValueError, KeyError): self._send(400, {"detail": "invalid task request"})
        except Exception: self._send(503, {"detail": "task authority unavailable"})

    def do_POST(self) -> None:
        self.do_GET()


class GatewayServer(ThreadingHTTPServer):
    def __init__(self, address: tuple[str, int], grain: GrainTaskClient,
                 token: str, owner: str):
        super().__init__(address, GatewayHandler)
        self.grain, self.token, self.owner = grain, token, owner


def main() -> None:
    token = os.environ["AGENT_GATEWAY_TOKEN"]
    owner = os.environ["AWP_OWNER_IDENTITY"]
    grain = GrainTaskClient(os.environ.get("AWP_GRAIN_SESSION_URL", "http://127.0.0.1:9447"),
                            os.environ["AWP_GRAIN_SESSION_TOKEN"], owner)
    with GatewayServer(("127.0.0.1", int(os.environ.get("AGENT_GATEWAY_PORT", "9444"))),
                       grain, token, owner) as server:
        server.serve_forever()


if __name__ == "__main__":
    main()
