#!/usr/bin/env python3
"""Configure the public upstream account service for a MoonCat TV build."""

import base64
import json
import re
import sys
import urllib.request
from pathlib import Path

OFFICIAL = "https://api.nuvio.tv"
DISCOVERY = OFFICIAL + "/.well-known/nuvio"


class NoRedirects(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def is_publishable(key):
    if not re.fullmatch(r"[A-Za-z0-9_.-]{16,2048}", key):
        return False
    if key.startswith("sb_publishable_"):
        return True
    segments = key.split(".")
    if len(segments) != 3:
        return False
    try:
        payload = segments[1] + "=" * (-len(segments[1]) % 4)
        return json.loads(base64.urlsafe_b64decode(payload))["role"] == "anon"
    except (TypeError, ValueError, KeyError):
        return False


def prepare(output):
    opener = urllib.request.build_opener(NoRedirects)
    with opener.open(DISCOVERY, timeout=18) as response:
        document = json.loads(response.read(16_384))
    if document.get("service") != "nuvio" or document.get("version") != 1 or document.get("backend_url") != OFFICIAL:
        raise ValueError("The official account discovery document does not match MoonCat's configuration")
    capabilities = document.get("capabilities") or {}
    key = document.get("publishable_key") or ""
    if capabilities.get("tv_login") is not True or not is_publishable(key):
        raise ValueError("The account server did not advertise TV login with a publishable client key")
    with Path(output).open("a", encoding="utf-8") as properties:
        properties.write(f"\nNUVIO_SUPABASE_URL={OFFICIAL}\nNUVIO_SUPABASE_ANON_KEY={key}\n")


if __name__ == "__main__":
    if len(sys.argv) > 2:
        sys.exit("Usage: prepare_official_account_config.py [path/to/local.properties]")
    prepare(sys.argv[1] if len(sys.argv) == 2 else "local.properties")
