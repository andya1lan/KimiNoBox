#!/usr/bin/env python3
#
# This file is part of KimiNoBox, a modified version of YumeBox.
#
# YumeBox is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as
# published by the Free Software Foundation, either version 3 of the
# License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
#
# Copyright (c) 2026 KimiNoBox contributors
#
"""Test HTTP server for the KimiNoBox emulator acceptance runs.

The emulator reaches it at http://10.0.2.2:<port>/. Files are served from --root; in addition:

  /status/<code>                  reply with that HTTP status and a short text body
  /slow/<seconds>/<file>          sleep, then serve <file> (read / call timeout cases)
  /empty                          200 with an empty body
  /html                           200 with an HTML error page
  /seq/<name>/<first>/<then>      first request serves <first>, every later one <then>
                                  (one counter per <name>; used for "trial compile passes,
                                  real start fails")
  /reset                          reset all /seq counters
  /slow.yaml                      12 s delay (compatible with the investigation fixtures)
  /cd/<filename>/<file>           serve <file> as a subscription named `filename="<filename>"`
  /cd8/<filename>/<file>          the same with `filename*=UTF-8''<percent-encoded filename>`
  /plain/<file>                   serve <file> without any subscription header

Files whose name starts with "sub" get `subscription-userinfo` and `profile-update-interval`.
Every request is logged with its User-Agent.
"""

import argparse
import http.server
import os
import sys
import threading
import time
import urllib.parse

HTML_PAGE = b"<!DOCTYPE html><html><head><title>502</title></head><body>Bad Gateway</body></html>"
USERINFO = "upload=1073741824; download=2147483648; total=107374182400; expire=1893456000"

counters = {}
counters_lock = threading.Lock()


class Handler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):  # noqa: N802 (http.server naming)
        path = urllib.parse.urlparse(self.path).path
        parts = [urllib.parse.unquote(p) for p in path.split("/") if p]
        if parts[:1] == ["status"] and len(parts) == 2:
            return self.reply(int(parts[1]), b"status %s\n" % parts[1].encode())
        if parts == ["empty"]:
            return self.reply(200, b"")
        if parts == ["html"]:
            return self.reply(200, HTML_PAGE, "text/html; charset=utf-8")
        if parts == ["reset"]:
            with counters_lock:
                counters.clear()
            return self.reply(200, b"reset\n")
        if parts[:1] == ["slow"] and len(parts) == 3:
            time.sleep(float(parts[1]))
            return self.serve_file(parts[2])
        if parts[:1] == ["cd"] and len(parts) == 3:
            return self.serve_file(parts[2], disposition='attachment; filename="%s"' % parts[1])
        if parts[:1] == ["cd8"] and len(parts) == 3:
            encoded = urllib.parse.quote(parts[1], safe="")
            return self.serve_file(parts[2], disposition="attachment; filename*=UTF-8''%s" % encoded)
        if parts[:1] == ["plain"] and len(parts) == 2:
            return self.serve_file(parts[1], subscription=False)
        if parts[:1] == ["seq"] and len(parts) == 4:
            with counters_lock:
                count = counters.get(parts[1], 0)
                counters[parts[1]] = count + 1
            return self.serve_file(parts[2] if count == 0 else parts[3])
        if path == "/slow.yaml":
            time.sleep(12)
        return super().do_GET()

    def serve_file(self, name, disposition=None, subscription=None):
        file = os.path.join(self.directory, os.path.basename(name))
        if not os.path.isfile(file):
            return self.reply(404, b"not found\n")
        with open(file, "rb") as handle:
            body = handle.read()
        if subscription is None:
            subscription = disposition is not None or name.startswith("sub")
        return self.reply(200, body, "text/plain; charset=utf-8", subscription=subscription, disposition=disposition)

    def reply(self, code, body, content_type="text/plain; charset=utf-8", subscription=False, disposition=None):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        if subscription:
            self.send_header("subscription-userinfo", USERINFO)
            self.send_header("profile-update-interval", "12")
        if disposition:
            self.send_header("Content-Disposition", disposition)
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        name = os.path.basename(urllib.parse.urlparse(self.path).path)
        if name.startswith("sub") and not self.path.startswith(("/seq/", "/slow/", "/cd/", "/cd8/", "/plain/")):
            self.send_header("subscription-userinfo", USERINFO)
            self.send_header("profile-update-interval", "12")
        super().end_headers()

    def log_message(self, fmt, *args):
        sys.stderr.write(
            "%s %s UA=%s %s\n"
            % (time.strftime("%H:%M:%S"), self.address_string(), self.headers.get("User-Agent"), fmt % args)
        )


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", required=True, help="directory with the fixtures")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    handler = lambda *a, **k: Handler(*a, directory=os.path.abspath(args.root), **k)  # noqa: E731
    http.server.ThreadingHTTPServer(("0.0.0.0", args.port), handler).serve_forever()


if __name__ == "__main__":
    main()
