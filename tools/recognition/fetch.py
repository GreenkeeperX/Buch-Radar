#!/usr/bin/env python3
"""Fetch the explicitly licensed public benchmark images, never private pictures."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument("destination", type=Path, help="Local fixture directory, outside the repository")
args = parser.parse_args()
manifest_file = Path(__file__).with_name("manifest.json")
manifest = json.loads(manifest_file.read_text())
args.destination.mkdir(parents=True, exist_ok=True)
credits = ["# Image credits", "", "Original images, unchanged. Runtime test rotations/resizing are local derivatives.", ""]
for entry in manifest["images"]:
    target = args.destination / entry["file"]
    if target.exists():
        data = target.read_bytes()
    else:
        request = urllib.request.Request(entry["url"], headers={
            "User-Agent": "BuecherRadar recognition evaluation (https://github.com/GreenkeeperX/Buch-Radar)"
        })
        with urllib.request.urlopen(request, timeout=45) as response:
            data = response.read(25_000_001)
        if len(data) > 25_000_000:
            raise ValueError("Image exceeds 25 MB limit")
    if hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError(f"Checksum mismatch: {entry['file']}; source may have changed")
    if not target.exists():
        target.write_bytes(data)
    credits.append(f"- [{entry['file']}]({entry['source']}): {entry['credit']}; [{entry['license']}]({entry['licenseUrl']}).")
    print(f"Verified: {entry['file']}")
(args.destination / "manifest.json").write_text(manifest_file.read_text())
(args.destination / "CREDITS.md").write_text("\n".join(credits) + "\n")
