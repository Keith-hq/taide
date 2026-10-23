import contextlib
import http.server
import json
from pathlib import Path
import sys
import threading
import unittest
import urllib.error

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from check_llm import check_connection


class Handler(http.server.BaseHTTPRequestHandler):
    status = 200
    payload = b'{"choices":[{"message":{"content":"TAIDE_OK"}}]}'
    received = None

    def do_POST(self):
        type(self).received = {
            "path": self.path,
            "authorization": self.headers.get("Authorization"),
            "user_agent": self.headers.get("User-Agent"),
            "body": json.loads(self.rfile.read(int(self.headers["Content-Length"]))),
        }
        self.send_response(type(self).status)
        if type(self).status == 302:
            self.send_header("Location", "http://127.0.0.1:1/redirect-target")
        self.end_headers()
        self.wfile.write(type(self).payload)

    def log_message(self, format, *args):
        pass


@contextlib.contextmanager
def server(status=200, payload=None):
    Handler.status = status
    Handler.payload = payload or b'{"choices":[{"message":{"content":"TAIDE_OK"}}]}'
    Handler.received = None
    with http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler) as instance:
        thread = threading.Thread(target=instance.serve_forever, daemon=True)
        thread.start()
        try:
            yield f"http://127.0.0.1:{instance.server_port}/v1"
        finally:
            instance.shutdown()
            thread.join()


class CheckLlmTests(unittest.TestCase):
    def test_valid_request_uses_only_synthetic_probe(self):
        with server() as url:
            result = check_connection(url, "test-model", "test-only-key")
        self.assertEqual({"status": "ok", "model": "test-model"}, result)
        self.assertEqual("/v1/chat/completions", Handler.received["path"])
        self.assertEqual("Bearer test-only-key", Handler.received["authorization"])
        self.assertEqual("TAIDE-connectivity-check/0.1", Handler.received["user_agent"])
        self.assertEqual("Reply exactly with TAIDE_OK.", Handler.received["body"]["messages"][0]["content"])
        self.assertNotIn("test-only-key", json.dumps(result))

    def test_rejects_invalid_configuration(self):
        for url in ("http://example.com", "file:///tmp/key", "https://user:pass@example.com", "https://example.com?key=x"):
            with self.subTest(url=url), self.assertRaises(ValueError):
                check_connection(url, "model", "key")
        with self.assertRaises(ValueError):
            check_connection("https://example.com", "model", "")

    def test_rejects_redirect_and_authentication_error(self):
        for status in (302, 401, 429, 500):
            with self.subTest(status=status), server(status=status) as url, self.assertRaises(urllib.error.HTTPError):
                check_connection(url, "model", "test-only-key")

    def test_rejects_invalid_response(self):
        for payload in (b"not json", b"{}", b'{"choices":[]}', b'{"choices":[{"message":{"content":null}}]}'):
            with self.subTest(payload=payload), server(payload=payload) as url, self.assertRaises(ValueError):
                check_connection(url, "model", "test-only-key")


if __name__ == "__main__":
    unittest.main()
