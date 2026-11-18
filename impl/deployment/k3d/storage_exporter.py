"""Expose measured usage of k3d local-path storage to Prometheus."""

import http.server
import os
import re
import stat
import threading
import time


STORAGE_ROOT = "/storage"
DIRECTORY_NAME = re.compile(r"^[A-Za-z0-9_.-]+(?:_[A-Za-z0-9_.-]+)*$")
snapshot = "awp_k3d_storage_scan_success 0\n"
lock = threading.Lock()


def allocated_bytes(path):
    total = 0
    pending = [path]
    while pending:
        current = pending.pop()
        with os.scandir(current) as entries:
            for entry in entries:
                metadata = entry.stat(follow_symlinks=False)
                total += metadata.st_blocks * 512
                if stat.S_ISDIR(metadata.st_mode):
                    pending.append(entry.path)
    return total


def scan():
    lines = ["# TYPE awp_k3d_storage_directory_bytes gauge"]
    with os.scandir(STORAGE_ROOT) as entries:
        directories = sorted(
            entry.name for entry in entries
            if entry.is_dir(follow_symlinks=False) and DIRECTORY_NAME.fullmatch(entry.name)
        )
    total = 0
    for name in directories:
        size = allocated_bytes(os.path.join(STORAGE_ROOT, name))
        total += size
        parts = name.split("_", 2)
        if len(parts) == 3 and parts[0].startswith("pvc-"):
            namespace, claim = parts[1], parts[2]
        else:
            namespace, claim = "awp-v2", name
        lines.append(
            f'awp_k3d_storage_directory_bytes{{directory="{name}",'
            f'namespace="{namespace}",claim="{claim}"}} {size}'
        )
    lines.extend(("# TYPE awp_k3d_storage_used_bytes gauge",
                  f"awp_k3d_storage_used_bytes {total}",
                  "# TYPE awp_k3d_storage_scan_success gauge",
                  "awp_k3d_storage_scan_success 1"))
    return "\n".join(lines) + "\n"


def refresh():
    global snapshot
    while True:
        try:
            result = scan()
        except OSError as error:
            print(f"storage scan failed: {error}", flush=True)
            result = "awp_k3d_storage_scan_success 0\n"
        with lock:
            snapshot = result
        time.sleep(60)


class MetricsHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path != "/metrics":
            self.send_error(404)
            return
        with lock:
            body = snapshot.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        pass


threading.Thread(target=refresh, daemon=True).start()
http.server.ThreadingHTTPServer(("0.0.0.0", 9464), MetricsHandler).serve_forever()
