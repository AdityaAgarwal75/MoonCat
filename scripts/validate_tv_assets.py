"""Validate data contracts used by the TV discovery UI before compilation."""
import json
import gzip
import pathlib
import re
root = pathlib.Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets/studios'
total = 0
for path in assets.glob('*.json.gz'):
    titles = json.loads(gzip.decompress(path.read_bytes()))
    assert titles and len(titles) == len({x['imdb'] for x in titles}), path
    assert all(re.fullmatch(r'tt\d{7,9}', x['imdb']) and x['type'] in ('movie', 'series') and x['title'] for x in titles), path
    assert (root / f'app/src/main/res/drawable/studio_{path.name.removesuffix(".json.gz")}.webp').exists(), path
    total += len(titles)
assert total > 10000
assert len(json.loads(gzip.decompress((assets / 'wizarding.json.gz').read_bytes()))) == 11
for name in ['splash','mark','word','banner','profiles']:
    assert (root / f'app/src/main/res/drawable/mooncat_{name}_v102.webp').exists()
print(f'Validated 11 studio catalogues with {total} entries and all five MoonCat branding assets')