"""Loopback browser for the private review workbench; never logs request bodies."""
from __future__ import annotations

import json
import secrets
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit

from workbench import PrivateWorkbench

HERE = Path(__file__).resolve().parent


def handler_for(workbench: PrivateWorkbench):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, _format, *_args):
            return

        def _headers(self, code: int, content_type: str, length: int):
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(length))
            self.send_header("Cache-Control", "no-store")
            self.send_header("X-Content-Type-Options", "nosniff")
            self.send_header("Referrer-Policy", "no-referrer")
            self.send_header("X-Frame-Options", "DENY")
            self.send_header("Content-Security-Policy",
                             "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; "
                             "img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'")
            self.end_headers()

        def _send(self, code: int, value: dict):
            body = json.dumps(value, ensure_ascii=False).encode()
            self._headers(code, "application/json; charset=utf-8", len(body))
            self.wfile.write(body)

        def _auth(self):
            value = self.headers.get("Authorization", "")
            if not value.startswith("Bearer "):
                return None
            return workbench.authenticate(value.removeprefix("Bearer "))

        def _host_ok(self):
            host = self.headers.get("Host", "")
            return host in {f"127.0.0.1:{self.server.server_port}", f"localhost:{self.server.server_port}"}

        def do_GET(self):
            if not self._host_ok():
                self._send(403, {"error": "forbidden"})
                return
            path = urlsplit(self.path).path
            if path in {"/", "/app.js", "/style.css"}:
                file = HERE / {"/": "index.html", "/app.js": "app.js", "/style.css": "style.css"}[path]
                body = file.read_bytes()
                kind = "text/html; charset=utf-8" if path == "/" else \
                    "text/javascript; charset=utf-8" if path.endswith(".js") else "text/css; charset=utf-8"
                self._headers(200, kind, len(body))
                self.wfile.write(body)
                return
            if not path.startswith("/api/"):
                self._send(404, {"error": "not-found"})
                return
            identity = self._auth()
            if identity is None:
                self._send(401, {"error": "unauthorized"})
                return
            try:
                if path == "/api/summary":
                    self._send(200, {"identity": identity[0], "role": identity[1],
                                     "summary": workbench.summary()})
                elif path == "/api/cases":
                    self._send(200, {"rows": workbench.list_rows("cases")})
                elif path == "/api/metadata":
                    self._send(200, {"rows": workbench.list_rows("metadata")})
                elif path.startswith("/api/cases/"):
                    self._send(200, workbench.row("cases", unquote(path.removeprefix("/api/cases/"))))
                elif path.startswith("/api/metadata/"):
                    self._send(200, workbench.row("metadata", unquote(path.removeprefix("/api/metadata/"))))
                else:
                    self._send(404, {"error": "not-found"})
            except (ValueError, RuntimeError, OSError):
                self._send(409, {"error": "unavailable"})

        def do_POST(self):
            if not self._host_ok() or self.headers.get("Origin") not in {None,
                    f"http://127.0.0.1:{self.server.server_port}",
                    f"http://localhost:{self.server.server_port}"}:
                self._send(403, {"error": "forbidden"})
                return
            identity = self._auth()
            if identity is None:
                self._send(401, {"error": "unauthorized"})
                return
            if self.headers.get("Content-Type", "").split(";", 1)[0] != "application/json":
                self._send(415, {"error": "content-type"})
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
                if length < 2 or length > 32_768:
                    raise ValueError("invalid request size")
                data = json.loads(self.rfile.read(length))
                actor, role = identity
                kind = data.get("kind")
                row_id = data.get("id")
                revision = data.get("revision")
                action_id = data.get("actionId") or secrets.token_hex(16)
                if not isinstance(revision, int) or revision < 0 or not isinstance(row_id, str):
                    raise ValueError("invalid action")
                if kind == "draft" and role == "owner":
                    answer = workbench.save_draft(row_id, data.get("draft", {}))
                elif kind == "family" and role == "owner":
                    answer = workbench.assign_family(row_id, data.get("familyId", ""),
                         data.get("split", ""), revision, action_id, actor)
                elif kind == "privacy" and role == "owner":
                    answer = workbench.privacy_review(row_id, data.get("decision", ""),
                                                      revision, action_id, actor)
                elif kind == "review" and role == "reviewer":
                    answer = workbench.review_case(row_id, data.get("expected"),
                         data.get("decision", ""), revision, action_id, actor,
                         data.get("reviewerRole", "independent"), data.get("note", ""))
                elif kind == "disposition" and role == "owner":
                    answer = workbench.disposition(row_id, data.get("disposition", ""),
                         revision, action_id, actor, data.get("reason", ""),
                         data.get("evidence", ""), data.get("sourceId", ""))
                else:
                    raise ValueError("role or action refused")
                self._send(200, {"result": answer})
            except (ValueError, RuntimeError, OSError, json.JSONDecodeError):
                self._send(409, {"error": "decision-refused"})

    return Handler


def serve(workbench: PrivateWorkbench, port: int = 0) -> None:
    if port < 0 or port > 65535:
        raise ValueError("invalid loopback port")
    server = HTTPServer(("127.0.0.1", port), handler_for(workbench))
    print(json.dumps({"url": f"http://127.0.0.1:{server.server_port}/",
                      "ownerTokenFile": str(workbench.root / "owner-token.txt"),
                      "quality": workbench.summary()["quality"]["gate"],
                      "migration": workbench.summary()["migration"]["gate"]}))
    try:
        server.serve_forever(poll_interval=0.2)
    finally:
        server.server_close()
