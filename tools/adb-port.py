#!/usr/bin/env python3
"""Find the phone's rotating wireless-debugging port via mDNS and connect adb to it."""
import subprocess, sys, time
from zeroconf import ServiceBrowser, Zeroconf

found = []

class L:
    def add_service(self, zc, type_, name):
        info = zc.get_service_info(type_, name)
        if info and info.parsed_addresses():
            found.append((info.parsed_addresses()[0], info.port))
    def update_service(self, *a): pass
    def remove_service(self, *a): pass

zc = Zeroconf()
ServiceBrowser(zc, "_adb-tls-connect._tcp.local.", L())
time.sleep(3)
zc.close()
if not found:
    print("no _adb-tls-connect service seen (is Wireless debugging on?)"); sys.exit(1)
for host, port in found:
    target = f"{host}:{port}"
    print("service:", target)
    print(subprocess.run(["adb", "connect", target], capture_output=True, text=True).stdout.strip())
print(subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout.strip())
