#!/usr/bin/env python3
"""Headless Linux CEF smoke test: prove an HTTP page renders and emits CEF events."""
import json
import os
import select
import subprocess
import tempfile
import threading
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

HOST = os.environ.get("KASPABROWSER_CEF_HOST", "build/linux-cef/bin/kaspabrowser-cef")
TITLE = "KaspaBrowser CEF Smoke Test"


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


def main():
    executable = Path(HOST).resolve()
    if not executable.is_file():
        raise SystemExit(
            f"CEF host not found: {executable}; build it first and set KASPABROWSER_CEF_HOST"
        )

    with tempfile.TemporaryDirectory(prefix="kb-cef-smoke-") as temp:
        root = Path(temp)
        (root / "index.html").write_text(
            f"<!doctype html><html><head><title>{TITLE}</title></head>"
            "<body><h1>KaspaBrowser Linux CEF rendering check</h1></body></html>",
            encoding="utf-8",
        )
        handler = lambda *args, **kwargs: QuietHandler(
            *args, directory=str(root), **kwargs
        )
        server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        url = f"http://127.0.0.1:{server.server_port}/index.html"
        proc = subprocess.Popen(
            [str(executable), f"--url={url}"],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=None,
            bufsize=0,
        )
        seen_title = False
        seen_load = False
        pending = b""
        deadline = time.monotonic() + 25
        try:
            while time.monotonic() < deadline and proc.poll() is None:
                ready, _, _ = select.select([proc.stdout], [], [], 0.25)
                if not ready:
                    continue
                chunk = os.read(proc.stdout.fileno(), 4096)
                if not chunk:
                    break
                pending += chunk
                while b"\\n" in pending:
                    raw, pending = pending.split(b"\\n", 1)
                    try:
                        event = json.loads(raw.decode("utf-8", errors="replace"))
                    except (UnicodeDecodeError, json.JSONDecodeError):
                        continue
                    if event.get("event") == "title_changed" and event.get("title") == TITLE:
                        seen_title = True
                    if event.get("event") == "load_finished" and event.get("url", "").startswith(url):
                        seen_load = True
                if seen_title and seen_load:
                    break
            if not (seen_title and seen_load):
                raise SystemExit(
                    f"CEF render smoke test failed: title_event={seen_title}, "
                    f"load_finished_event={seen_load}, exit={proc.poll()}"
                )
            print(f"PASS: CEF rendered {url} and emitted title/load-finished events")
        finally:
            if proc.poll() is None:
                try:
                    proc.stdin.write(b"QUIT\\n")
                    proc.stdin.flush()
                    proc.wait(timeout=4)
                except Exception:
                    proc.kill()
                    proc.wait()
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    main()
