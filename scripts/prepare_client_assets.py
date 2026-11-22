#!/usr/bin/env python3
"""Build compact-client assets from the same catalogues, logos and channels as TV."""

import argparse
import gzip
import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TV = ROOT / "app/src/main"
TV_UI = TV / "java/com/nuvio/tv/ui/screens"


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")


def copy_drawable(output, name):
    source, = [p for p in (TV / "res/drawable").glob(f"{name}.*") if p.suffix != ".xml"]
    relative = Path("logos") / source.name
    shutil.copyfile(source, output / relative)
    return relative.as_posix()


def prepare(output):
    shutil.copytree(ROOT / "clients/web", output, dirs_exist_ok=True)
    (output / "logos").mkdir(exist_ok=True)
    (output / "studios").mkdir(exist_ok=True)
    shutil.copyfile(ROOT / "branding/mooncat-logo.png", output / "logos/mooncat.png")

    source = (TV_UI / "collection/MooncatStudiosScreen.kt").read_text()
    studios = []
    for key, name, logo in re.findall(r'MooncatStudio\("([^"]+)", "([^"]+)", R.drawable.(\w+)\)', source):
        with gzip.open(TV / "assets/studios" / f"{key}.json.gz", "rt", encoding="utf-8") as stream:
            titles = json.load(stream)
        unique = {item["imdb"]: item for item in reversed(titles) if re.fullmatch(r"tt\d+", item["imdb"])}
        write_json(output / "studios" / f"{key}.json", list(reversed(unique.values())))
        studios.append({"id": key, "name": name, "logo": copy_drawable(output, logo), "count": len(unique)})

    source = (TV_UI / "news/MooncatNewsScreen.kt").read_text()
    channels = []
    pattern = r'MooncatChannel\("([^"]+)", "([^"]+)", "([^"]+)", R.drawable.(\w+), (.*)\),?'
    for name, language, url, logo, headers in re.findall(pattern, source):
        channels.append({
            "name": name, "language": language, "url": url,
            "logo": copy_drawable(output, logo),
            "headers": dict(re.findall(r'"([^"]+)" to "([^"]+)"', headers)),
        })
    if len(studios) != 11 or len(channels) != 20:
        raise ValueError(f"Check Alpha 4 catalogue declarations: {len(studios)} studios, {len(channels)} channels")
    write_json(output / "catalogues.json", {"studios": studios, "channels": channels})


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("output", type=Path)
    prepare(parser.parse_args().output)
