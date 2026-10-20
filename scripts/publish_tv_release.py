"""Fetch an originally signed TV APK and verify it before publishing through CI."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.request

manifest = json.loads(Path('mooncat/tv-release.json').read_text())
assert re.fullmatch(r'v\d+\.\d+\.\d+', manifest['tag'])
assert manifest['url'].startswith('https://mooncat.15-252-234-187.sslip.io/MoonCat-')
assert re.fullmatch(r'[0-9a-f]{64}', manifest['sha256'])
out = Path('dist'); out.mkdir(exist_ok=True)
apk = out / f"MoonCat-{manifest['tag']}-FireTV-armeabi-v7a.apk"
with urllib.request.urlopen(manifest['url'], timeout=60) as response, apk.open('wb') as target:
    while data := response.read(1024 * 1024): target.write(data)
assert hashlib.sha256(apk.read_bytes()).hexdigest() == manifest['sha256']
tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
certs = subprocess.check_output([str(tools/'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
assert '6341008335cc94f1dba060680dbedafd59ba5d5ef8017d09ff8757cccdd0ec0c' in certs
badging = subprocess.check_output([str(tools/'aapt'), 'dump', 'badging', str(apk)], text=True)
assert "name='dev.mooncat.tv'" in badging and f"versionCode='{manifest['versionCode']}'" in badging
(out/'SHA256SUMS').write_text(manifest['sha256']+'  '+apk.name+'\n')
with open(os.environ['GITHUB_OUTPUT'], 'a') as stream: stream.write('tag='+manifest['tag']+'\n')
