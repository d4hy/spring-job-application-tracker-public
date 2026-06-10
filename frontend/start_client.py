#!/usr/bin/env python3
"""
Start the frontend static server and open the app in a browser automatically.
"""

from __future__ import annotations

import argparse
import pathlib
import threading
import time
import urllib.request
import webbrowser
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer


def wait_until_reachable(url: str, timeout_seconds: float = 12.0) -> bool:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=0.75):
                return True
        except Exception:
            time.sleep(0.2)
    return False


def open_browser_when_ready(url: str) -> None:
    if wait_until_reachable(url):
        webbrowser.open(url)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Serve frontend files and open the app in your browser."
    )
    parser.add_argument("--host", default="localhost", help="Bind host (default: localhost)")
    parser.add_argument("--port", type=int, default=5173, help="Bind port (default: 5173)")
    parser.add_argument("--no-open", action="store_true", help="Do not auto-open the browser")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    frontend_dir = pathlib.Path(__file__).resolve().parent
    # Serve files relative to the frontend folder so / maps to index.html.
    handler = lambda *h_args, **h_kwargs: SimpleHTTPRequestHandler(  # noqa: E731
        *h_args, directory=str(frontend_dir), **h_kwargs
    )
    server = ThreadingHTTPServer((args.host, args.port), handler)
    url = f"http://{args.host}:{args.port}/"

    print(f"Frontend server running at {url}")
    print("Press Ctrl+C to stop.")

    if not args.no_open:
        threading.Thread(target=open_browser_when_ready, args=(url,), daemon=True).start()

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        print("Frontend server stopped.")


if __name__ == "__main__":
    main()
