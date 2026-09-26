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
"""Minimal SOCKS5 proxy (no auth, CONNECT only) for the emulator acceptance runs.

A node `{type: socks5, server: 10.0.2.2, port: 1080}` in a test profile is then a real,
working node: traffic through it leaves from the host. Listens on 127.0.0.1 only, which the
emulator reaches as 10.0.2.2. Each CONNECT is logged with its target.
"""

import argparse
import socket
import struct
import sys
import threading
import time


def pipe(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    finally:
        for sock in (src, dst):
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def read_exact(sock, size):
    data = b""
    while len(data) < size:
        chunk = sock.recv(size - len(data))
        if not chunk:
            raise ConnectionError("short read")
        data += chunk
    return data


def handle(client):
    try:
        version, methods = read_exact(client, 2)
        read_exact(client, methods)
        if version != 5:
            return
        client.sendall(b"\x05\x00")
        _, command, _, address_type = read_exact(client, 4)
        if address_type == 1:
            host = socket.inet_ntoa(read_exact(client, 4))
        elif address_type == 3:
            host = read_exact(client, read_exact(client, 1)[0]).decode()
        elif address_type == 4:
            host = socket.inet_ntop(socket.AF_INET6, read_exact(client, 16))
        else:
            return
        port = struct.unpack("!H", read_exact(client, 2))[0]
        if command != 1:
            client.sendall(b"\x05\x07\x00\x01" + b"\x00" * 6)
            return
        try:
            upstream = socket.create_connection((host, port), timeout=10)
        except OSError:
            client.sendall(b"\x05\x05\x00\x01" + b"\x00" * 6)
            return
        upstream.settimeout(None)
        sys.stderr.write("%s CONNECT %s:%d\n" % (time.strftime("%H:%M:%S"), host, port))
        client.sendall(b"\x05\x00\x00\x01" + b"\x00" * 6)
        threading.Thread(target=pipe, args=(upstream, client), daemon=True).start()
        pipe(client, upstream)
    except (OSError, ConnectionError):
        pass
    finally:
        client.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--port", type=int, default=1080)
    args = parser.parse_args()
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", args.port))
    server.listen(64)
    while True:
        client, _ = server.accept()
        threading.Thread(target=handle, args=(client,), daemon=True).start()


if __name__ == "__main__":
    main()
