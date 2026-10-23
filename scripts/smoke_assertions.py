"""Package-scoped runtime checks: Android system app crashes are separate evidence."""
import re

PACKAGE = 'dev.mooncat.tv.preview'

def assert_app_runtime_healthy(logs, memory, package=PACKAGE):
    process = re.escape(package)
    assert not re.search(r'AndroidRuntime: Process:\s*' + process + r'(?:[:,\s]|$)', logs), 'MoonCat Java runtime crash'
    assert not re.search(r'>>>\s*' + process + r'\s*<<<', logs), 'MoonCat native runtime crash'
    assert 'ANR in ' + package not in logs, 'MoonCat app not responding'
    assert re.search(r'MEMINFO in pid \d+ \[' + process + r'\]', memory), 'MoonCat process was not alive at final capture'
