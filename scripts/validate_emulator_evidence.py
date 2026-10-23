"""Validate captured release smoke evidence without substituting a different APK."""
import pathlib
import sys
import xml.etree.ElementTree as ET
from smoke_assertions import assert_app_runtime_healthy

def validate(path):
    logs = (path/'logcat.txt').read_text()
    memory = (path/'memory.txt').read_text()
    assert_app_runtime_healthy(logs, memory)
    launch = (path/'launch.txt').read_text()
    assert 'Status: ok' in launch and 'dev.mooncat.tv.preview/com.nuvio.tv.MainActivity' in launch
    for name, expected in [('01-startup', 'Home'), ('02-profile-home', 'Play'), ('03-navigation', 'Home')]:
        nodes = list(ET.parse(path/f'{name}.xml').getroot().iter('node'))
        assert any(expected in (node.get('text'), node.get('content-desc')) for node in nodes), (name, expected)
        assert (path/f'{name}.png').stat().st_size > 1000, name
    print('PASS: optimized MoonCat release launched, rendered Home/title Play page, returned Home, and remained alive without a MoonCat Java/native crash or ANR.')
    if 'Process: com.android.tv,' in logs:
        print('Separate emulator system-app failure recorded: com.android.tv DVR database. Not a MoonCat process failure.')
    print('This verifies captured startup/navigation evidence, not sustained playback or physical Fire TV frame times.')

if __name__ == '__main__':
    validate(pathlib.Path(sys.argv[1]))
