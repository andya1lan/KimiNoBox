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
"""Tiny uiautomator driver for the KimiNoBox emulator runs.

  ui.py dump                     list visible nodes with text / content-desc and bounds
  ui.py tap-text TEXT [INDEX]    tap the INDEX-th node (default 0) whose text or desc contains TEXT
  ui.py tap-exact TEXT [INDEX]   same, but the node text must equal TEXT (desc is ignored)
  ui.py wait-text TEXT [SECS]    poll until a node containing TEXT is visible (exit 1 on timeout)
  ui.py tap-edit INDEX           tap the INDEX-th text field of the current screen
  ui.py tap X Y                  tap raw screen coordinates
  ui.py type TEXT                type text into the focused field (in small chunks)
  ui.py clear                    select all (Ctrl+A) and delete in the focused field
  ui.py key KEYCODE              send a key event (e.g. BACK, ENTER)
  ui.py shot PATH                save a screenshot to PATH

ADB can be overridden with the ADB environment variable.
"""

import os
import re
import subprocess
import sys
import time

ADB = os.environ.get("ADB", os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"))
NODE = re.compile(r"<node [^>]*>")


def adb(*args, capture=True):
    return subprocess.run([ADB, *args], check=False, capture_output=capture, text=capture)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/kimi-ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/kimi-ui.xml").stdout or ""
    found = []
    for match in NODE.finditer(xml):
        raw = match.group(0)

        def attr(name):
            m = re.search(name + r'="([^"]*)"', raw)
            return m.group(1) if m else ""

        bounds = re.findall(r"\d+", attr("bounds"))
        if len(bounds) != 4:
            continue
        x1, y1, x2, y2 = map(int, bounds)
        found.append(
            {
                "class": attr("class"),
                "text": attr("text"),
                "desc": attr("content-desc"),
                "clickable": attr("clickable") == "true",
                "center": ((x1 + x2) // 2, (y1 + y2) // 2),
                "bounds": (x1, y1, x2, y2),
            }
        )
    return found


def matching(text, exact=False):
    if exact:
        return [n for n in nodes() if n["text"] == text]
    return [n for n in nodes() if text in n["text"] or text in n["desc"]]


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    command, rest = argv[0], argv[1:]
    if command == "dump":
        for n in nodes():
            if n["text"] or n["desc"]:
                flag = "*" if n["clickable"] else " "
                print(flag, repr(n["text"])[:70], repr(n["desc"])[:40], n["bounds"])
        return 0
    if command in ("tap-text", "tap-exact"):
        index = int(rest[1]) if len(rest) > 1 else 0
        hits = matching(rest[0], exact=command == "tap-exact")
        if len(hits) <= index:
            print("not found:", rest[0], file=sys.stderr)
            return 1
        x, y = hits[index]["center"]
        adb("shell", "input", "tap", str(x), str(y))
        print("tapped", rest[0], (x, y))
        return 0
    if command == "wait-text":
        deadline = time.time() + (float(rest[1]) if len(rest) > 1 else 20)
        while time.time() < deadline:
            if matching(rest[0]):
                print("visible:", rest[0])
                return 0
            time.sleep(1)
        print("timeout waiting for", rest[0], file=sys.stderr)
        return 1
    if command == "tap-edit":
        # Layouts shift when the IME opens: resolve the INDEX-th text field right before tapping.
        edits = [n for n in nodes() if n["class"].endswith("EditText")]
        index = int(rest[0])
        if len(edits) <= index:
            print("no text field", index, file=sys.stderr)
            return 1
        x, y = edits[index]["center"]
        adb("shell", "input", "tap", str(x), str(y))
        print("tapped field", index, (x, y))
        return 0
    if command == "tap":
        adb("shell", "input", "tap", rest[0], rest[1])
        return 0
    if command == "type":
        # Small chunks: EditText-backed Compose fields drop characters on bulk input.
        text = rest[0]
        for start in range(0, len(text), 6):
            chunk = text[start : start + 6].replace(" ", "%s")
            adb("shell", "input", "text", "'" + chunk.replace("'", "'\\''") + "'")
            time.sleep(0.25)
        return 0
    if command == "clear":
        # Ctrl+A, then delete the selection.
        adb("shell", "input", "keycombination", "113", "29")
        time.sleep(0.3)
        adb("shell", "input", "keyevent", "DEL")
        return 0
    if command == "key":
        adb("shell", "input", "keyevent", rest[0])
        return 0
    if command == "shot":
        with open(rest[0], "wb") as out:
            out.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], check=False, capture_output=True).stdout)
        print(rest[0])
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
