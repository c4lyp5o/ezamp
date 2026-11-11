#!/usr/bin/env python3
"""Tap a UI node by its exact text and return the resulting screen text.

Guessing coordinates cost us several misfires; the dump already knows the bounds.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

DEV = sys.argv[1] if len(sys.argv) > 1 else None


def adb(*args, timeout=30):
    cmd = ["adb"] + (["-s", DEV] if DEV else []) + list(args)
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout).stdout


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    adb("pull", "/sdcard/ui.xml", "/tmp/ui.xml")
    root = ET.parse("/tmp/ui.xml").getroot()
    out = []
    for n in root.iter("node"):
        t = (n.get("text") or "").strip()
        if not t:
            continue
        b = [int(v) for v in re.findall(r"-?\d+", n.get("bounds") or "")]
        out.append((t, (b[0] + b[2]) // 2, (b[1] + b[3]) // 2))
    return out


def tap_text(label):
    for t, x, y in dump():
        if t == label or t.startswith(label):
            adb("shell", "input", "tap", str(x), str(y))
            return t, x, y
    return None


if __name__ == "__main__":
    adb("shell", "am", "start", "-n", "one.calypsocloud.ezamp/.MainActivity")
    import time

    time.sleep(2.5)
    print("tap", tap_text(sys.argv[2] if len(sys.argv) > 2 else "SET"))
    time.sleep(2.5)
    for t, x, y in dump():
        if "OFFLINE" in t or "MB ON DEVICE" in t or t in ("DELETE", "SET"):
            print("  ", t)
