import unittest
from smoke_assertions import assert_app_runtime_healthy

class SmokeAssertionsTest(unittest.TestCase):
    memory = '** MEMINFO in pid 3722 [dev.mooncat.tv.preview] **'
    def test_system_tv_crash_does_not_fail_healthy_mooncat(self):
        assert_app_runtime_healthy('AndroidRuntime: FATAL EXCEPTION: DVR\nAndroidRuntime: Process: com.android.tv, PID: 10', self.memory)
    def test_mooncat_java_and_native_crashes_are_rejected(self):
        for logs in ['AndroidRuntime: FATAL EXCEPTION: main\nAndroidRuntime: Process: dev.mooncat.tv.preview, PID: 3722', '>>> dev.mooncat.tv.preview <<<']:
            with self.assertRaises(AssertionError):
                assert_app_runtime_healthy(logs, self.memory)
    def test_anr_or_missing_process_is_rejected(self):
        for logs, memory in [('ANR in dev.mooncat.tv.preview', self.memory), ('', 'No process found')]:
            with self.assertRaises(AssertionError):
                assert_app_runtime_healthy(logs, memory)
