#!/usr/bin/env python3
"""Sign GitHub-built MoonCat unsigned APKs on the trusted VPS.

This script never prints keys or stores signing secrets in source control.
Usage:
  python3 scripts/sign_mooncat_release.py TV_UNSIGNED.apk MOBILE_UNSIGNED.apk \
    --properties /home/ubuntu/mooncat-research/MoonCat/local.properties \
    --output /srv/mooncat-updates
"""
import argparse
import hashlib
import os
import pathlib
import re
import subprocess
import tempfile

ORIGINAL_CERT_SHA256 = "6341008335cc94f1dba060680dbedafd59ba5d5ef8017d09ff8757cccdd0ec0c"
BUILD_TOOLS = pathlib.Path("/home/ubuntu/Documents/Codex/2026-10-19/no/work/toolchain/android-sdk/build-tools/36.0.0")
TARGETS = [
    ("tv", "dev.mooncat.tv", "MoonCat-v1.0.3-FireTV.apk"),
    ("mobile", "dev.mooncat.mobile", "MoonCat-v1.0.3-Mobile.apk"),
]


def run(*args, env=None, **kwargs):
    return subprocess.run(
        [str(a) for a in args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=env,
        **kwargs,
    ).stdout


def props_from_file(file_path):
    props = {}
    for line in file_path.read_text().splitlines():
        if "=" in line and not line.lstrip().startswith(("#", "!")):
            key, value = line.split("=", 1)
            props[key.strip()] = value.strip()
    return props


def verify_package(apk, expected_name):
    info = run(BUILD_TOOLS / "aapt", "dump", "badging", apk)
    package = next(x for x in info.splitlines() if x.startswith("package:"))
    if "name='" + expected_name + "'" not in package or "versionCode='10004'" not in package:
        raise ValueError("Unexpected package or version: " + package)
    return package


def sign_one(unsigned, out_path, props, sign_env):
    verify_package(unsigned, next(p for name, p, dest in TARGETS if dest == out_path.name))
    with tempfile.TemporaryDirectory(prefix="mooncat-sign-") as tmp:
        aligned = pathlib.Path(tmp) / "aligned.apk"
        run(BUILD_TOOLS / "zipalign", "-f", "-p", "4", unsigned, aligned)
        run(
            BUILD_TOOLS / "apksigner", "sign",
            "--ks", props["NUVIO_RELEASE_STORE_FILE"],
            "--ks-key-alias", props["NUVIO_RELEASE_KEY_ALIAS"],
            "--ks-pass", "env:MC_STORE",
            "--key-pass", "env:MC_KEY",
            "--out", out_path, aligned,
            env=sign_env,
        )
    info = run(BUILD_TOOLS / "apksigner", "verify", "--verbose", "--print-certs", out_path)
    match = re.search(r"Signer #1 certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", info)
    if not match or match.group(1).lower() != ORIGINAL_CERT_SHA256:
        out_path.unlink(missing_ok=True)
        raise ValueError("MoonCat original signing certificate mismatch")
    verify_package(out_path, next(p for name, p, dest in TARGETS if dest == out_path.name))
    out_path.chmod(0o644)
    return hashlib.sha256(out_path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("tv_unsigned", type=pathlib.Path)
    parser.add_argument("mobile_unsigned", type=pathlib.Path)
    parser.add_argument("--properties", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    props = props_from_file(args.properties)
    for key in ("NUVIO_RELEASE_STORE_FILE", "NUVIO_RELEASE_STORE_PASSWORD", "NUVIO_RELEASE_KEY_PASSWORD", "NUVIO_RELEASE_KEY_ALIAS"):
        if not props.get(key):
            raise ValueError("Missing private signing configuration key: " + key)
    key_file = pathlib.Path(props["NUVIO_RELEASE_STORE_FILE"])
    if not key_file.is_absolute():
        key_file = args.properties.parent / key_file
    if not key_file.is_file():
        raise FileNotFoundError("Original MoonCat keystore unavailable")
    props["NUVIO_RELEASE_STORE_FILE"] = str(key_file)
    sign_env = os.environ.copy()
    sign_env.update(MC_STORE=props["NUVIO_RELEASE_STORE_PASSWORD"], MC_KEY=props["NUVIO_RELEASE_KEY_PASSWORD"])
    args.output.mkdir(parents=True, exist_ok=True)
    sums = []
    for unsigned, (_, package, filename) in zip(
        (args.tv_unsigned, args.mobile_unsigned), TARGETS
    ):
        if not unsigned.is_file() or unsigned.stat().st_size == 0:
            raise FileNotFoundError(unsigned)
        dest = args.output / filename
        digest = sign_one(unsigned, dest, props, sign_env)
        sums.append(digest + "  " + filename)
        print("VERIFIED", filename, package, unsigned.stat().st_size, digest)
    (args.output / "MoonCat-v1.0.3-SHA256SUMS").write_text("\n".join(sums) + "\n")
    print("Both signed APKs verified using original MoonCat release identity.")


if __name__ == "__main__":
    main()
