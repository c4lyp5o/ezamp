#!/usr/bin/env python3
"""Tap a labelled control inside the SET sheet, then report the sheet state.

Coordinates are read from the dump, never guessed — guessing has cost several misfires.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

DEV = sys.argv[1]
LABEL = sys.argv[2]


def adb(*args, timeout=30):
    return subprocess.run(["adb", "-s", DEV] + list(args), capture_output=True, text=True,
                          timeout=timeout).stdout


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", "/tmp/ui.xml")
    root = ET.parse("/tmp/ui.xml").getroot()
    out = []
    for n in root.iter("node"):
        t = (n.get("text") or "").strip()
        b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds") or "")]
        if t and len(b) == 4:
            out.append((t, (b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
    return out


adb("shell", "am", "start", "-n", "one.calypsocloud.ezamp/.MainActivity")
time.sleep(2)
for t, x, y in nodes():
    if t == "SET":
        adb("shell", "input", "tap", str(x), str(y))
        break
time.sleep(2)

for t, x, y in nodes():
    if t.startswith(LABEL):
        adb("shell", "input", "tap", str(x), str(y))
        print(f"tapped {t!r} at {x},{y}")
        break
else:
    print("label not found")
    sys.exit(1)

time.sleep(2)
for t, x, y in nodes():
    if t in ("Direct", "Saver") or "●" in t or "○" in t:
        print("  ", t)

adb("shell", "input", "keyevent", "4")
