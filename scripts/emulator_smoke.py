"""Release startup/remote-navigation smoke. Does not certify device speed or provider playback."""
import pathlib
import subprocess
import time

out = pathlib.Path('smoke-results')
out.mkdir(exist_ok=True)
package = 'dev.mooncat.tv.preview'

def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True).stdout

def capture(name):
    (out / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    adb('shell', 'uiautomator', 'dump', f'/sdcard/{name}.xml', check=False)
    (out / f'{name}.xml').write_bytes(adb('shell', 'cat', f'/sdcard/{name}.xml', check=False))

adb('logcat', '-c')
apk = next(pathlib.Path('app/build/outputs/apk/full/release').glob('*.apk'))
adb('install', '-r', str(apk))
launch = adb('shell', 'am', 'start', '-W', '-n', f'{package}/com.nuvio.tv.MainActivity')
(out / 'launch.txt').write_bytes(launch)
assert b'Error:' not in launch, launch.decode(errors='replace')
time.sleep(15)
capture('01-startup')
adb('shell', 'input', 'keyevent', 'KEYCODE_DPAD_CENTER')
time.sleep(15)
capture('02-profile-home')
for key in ['KEYCODE_DPAD_DOWN', 'KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_RIGHT',
            'KEYCODE_DPAD_LEFT', 'KEYCODE_DPAD_UP', 'KEYCODE_BACK']:
    adb('shell', 'input', 'keyevent', key)
    time.sleep(0.3)
capture('03-navigation')
logs = adb('logcat', '-d', '-v', 'threadtime').decode(errors='replace')
(out / 'logcat.txt').write_text(logs)
(out / 'memory.txt').write_bytes(adb('shell', 'dumpsys', 'meminfo', package))
assert 'FATAL EXCEPTION' not in logs, 'Android runtime crash: inspect smoke-results/logcat.txt'
assert 'ANR in ' + package not in logs, 'App not responding during smoke'
assert adb('shell', 'pidof', package).strip(), 'App process exited during remote navigation'
print('Optimized release launched and survived profile/Home remote-navigation smoke; screenshots and logs saved.')
