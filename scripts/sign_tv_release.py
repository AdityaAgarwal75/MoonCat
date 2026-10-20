#!/usr/bin/env python3
"""Sign a GitHub-built TV APK on the existing trusted signing host."""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import tempfile

CERT = '6341008335cc94f1dba060680dbedafd59ba5d5ef8017d09ff8757cccdd0ec0c'

def run(*args, env=None):
    return subprocess.check_output([str(x) for x in args], env=env, stderr=subprocess.STDOUT, text=True)

def main():
    p = argparse.ArgumentParser()
    p.add_argument('unsigned', type=Path)
    p.add_argument('--properties', type=Path, required=True)
    p.add_argument('--tools', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--version-code', type=int, required=True)
    a = p.parse_args()
    props = {}
    for line in a.properties.read_text().splitlines():
        if '=' in line and not line.lstrip().startswith(('#','!')):
            k,v = line.split('=',1); props[k.strip()] = v.strip()
    key = Path(props['NUVIO_RELEASE_STORE_FILE'])
    if not key.is_absolute(): key = a.properties.parent/key
    env = os.environ.copy()
    env.update(MC_STORE=props['NUVIO_RELEASE_STORE_PASSWORD'], MC_KEY=props['NUVIO_RELEASE_KEY_PASSWORD'])
    info = run(a.tools/'aapt','dump','badging',a.unsigned)
    assert "name='dev.mooncat.tv'" in info and f"versionCode='{a.version_code}'" in info
    a.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='mooncat-tv-sign-') as tmp:
        aligned = Path(tmp)/'aligned.apk'; signed = Path(tmp)/'signed.apk'
        run(a.tools/'zipalign','-f','-p','4',a.unsigned,aligned)
        run(a.tools/'apksigner','sign','--ks',key,'--ks-key-alias',props['NUVIO_RELEASE_KEY_ALIAS'],
            '--ks-pass','env:MC_STORE','--key-pass','env:MC_KEY','--out',signed,aligned,env=env)
        result = run(a.tools/'apksigner','verify','--verbose','--print-certs',signed)
        assert CERT in result, 'Original signing certificate mismatch'
        # Move only the completely verified APK into the served directory.
        import shutil
        shutil.copyfile(signed, a.output)
        a.output.chmod(0o644)
    digest = hashlib.sha256(a.output.read_bytes()).hexdigest()
    print('VERIFIED', a.output.name, a.output.stat().st_size, 'versionCode', a.version_code, 'sha256', digest)

if __name__ == '__main__': main()
