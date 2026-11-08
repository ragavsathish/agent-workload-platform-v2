"""Loopback managed-alert fixture for a supervised local incident check."""

import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.endswith('/api/v1/alerts'):
            body = {'status': 'success', 'data': {'alerts': [{
                'labels': {'rule_uid': 'awp_core_telemetry_missing',
                           'auto_investigate': 'true',
                           'managed_by': 'agent-workload-platform'},
                'annotations': {'summary': 'Local qualification fixture'},
                'state': 'Alerting', 'activeAt': '2026-11-08T00:00:00Z',
                'fingerprint': 'local-release-slice15'}]}}
        elif self.path.startswith('/api/v1/query?'):
            body = {'status': 'success', 'data': {'resultType': 'vector', 'result': []}}
        else:
            self.send_error(404)
            return
        data = json.dumps(body).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, _format, *_args):
        pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', type=int, default=9550)
    args = parser.parse_args()
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
