#!/usr/bin/env python3
"""Prepare the embedded Win32 client and verify the pinned HLS dependency."""

import base64
import hashlib
import io
import shutil
import struct
import subprocess
import tarfile
from pathlib import Path

DESKTOP = Path(__file__).resolve().parent
ROOT = DESKTOP.parent
HLS = "https://registry.npmjs.org/hls.js/-/hls.js-1.6.15.tgz"
HLS_SHA512 = "E3a5VwgXimGHwpRGV+WxRTKeSp2DW5DI5MWv34ulL3t5UNmyJWCQ1KmLEHbYzcfThfXG8amBL+fCYPneGHC4VA=="


def write_icon(destination):
    drawables = ROOT / "app/src/main/res"
    images = [(drawables / f"mipmap-{density}/ic_launcher.png").read_bytes()
              for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")]
    contents = bytearray(struct.pack("<HHH", 0, 1, len(images)))
    offset = len(contents) + 16 * len(images)
    for image in images:
        width, height = struct.unpack(">II", image[16:24])
        contents.extend(struct.pack("<BBBBHHII", width, height, 0, 0, 1, 32, len(image), offset))
        offset += len(image)
    destination.write_bytes(contents + b"".join(images))


def main():
    destination = DESKTOP / "web"
    shutil.rmtree(destination, ignore_errors=True)
    subprocess.run(["python3", str(ROOT / "scripts/prepare_client_assets.py"), str(destination)], check=True)
    archive = subprocess.check_output(["curl", "-fsSL", "--retry", "2", "--max-time", "60", HLS])
    if base64.b64encode(hashlib.sha512(archive).digest()).decode() != HLS_SHA512:
        raise ValueError("hls.js package did not match the pinned SHA-512")
    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as package:
        for source, target in [("dist/hls.min.js", "hls.js"), ("LICENSE", "HLS-LICENSE.txt")]:
            with package.extractfile("package/" + source) as resource:
                (destination / target).write_bytes(resource.read())
    index = destination / "index.html"
    index.write_text(index.read_text().replace(
        '<script defer src="app.js"></script>',
        '<script defer src="hls.js"></script>\n    <script defer src="app.js"></script>',
    ))
    write_icon(DESKTOP / "mooncat.ico")


if __name__ == "__main__":
    main()
