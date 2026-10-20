"""AAPT expands source *.json.gz assets into packaged *.json assets."""
import json
import sys
import zipfile
with zipfile.ZipFile(sys.argv[1]) as apk:
    names = [x for x in apk.namelist() if x.startswith('assets/studios/') and x.endswith('.json')]
    assert len(names) == 11, names
    titles = [entry for path in names for entry in json.loads(apk.read(path))]
    assert len(titles) > 10000
    marvel = json.loads(apk.read('assets/studios/marvel.json'))
    assert any(x['imdb'] == 'tt0371746' for x in marvel)
    print('Verified packaged catalogue contract:', len(names), 'studios,', len(titles), 'entries')
