"""录制日志例外的边界回归：OSHI采集失败不阻塞，其他运行错误继续阻塞。"""

import sys
import unittest

sys.dont_write_bytecode = True
from assemble import classify_log_errors


def record(logger: str, message: str, level: str = "ERROR") -> str:
    return f"[2026-10-23 12:00:00.000] [main/{level}] [{logger}/]: {message}"


class LogFilterTest(unittest.TestCase):
    def test_oshi_registry_error_owns_its_jna_trace(self):
        lines = [record("oshi.driver.windows.registry.HkeyPerformanceDataUtil", "Perflib 009"),
                 "com.sun.jna.platform.win32.Win32Exception: invalid parameter",
                 "\tat oshi.driver.windows.registry.HkeyPerformanceDataUtil.mapCounterIndicesFromRegistry(x:1)"]
        original = lines.copy()
        bad, ignored = classify_log_errors(lines)
        self.assertEqual(bad, [])
        self.assertEqual(len(ignored), 2)
        self.assertEqual(lines, original)

    def test_known_system_report_null_process_queries(self):
        for title, method in [("Process Loads", "getUserTime"), ("Process Virtual Size (MiB)", "getVirtualSize"),
                              ("Process Resident Size (MiB)", "getResidentSetSize")]:
            with self.subTest(method=method):
                bad, ignored = classify_log_errors([
                    record("net.minecraft.SystemReport", f"Failed to get system info for {title}", "WARN"),
                    f'java.lang.NullPointerException: Cannot invoke "oshi.software.os.OSProcess.{method}()" because "process" is null'])
                self.assertEqual(bad, [])
                self.assertEqual(len(ignored), 1)

    def test_new_record_resets_oshi_context(self):
        problem = record("work.nekow.primalspells.Spell", "spell failed")
        bad, ignored = classify_log_errors([
            record("oshi.util.platform.windows.PerfCounterQuery", "counter unavailable"),
            problem, "java.lang.IllegalStateException: broken spell"])
        self.assertEqual(bad, [problem, "java.lang.IllegalStateException: broken spell"])
        self.assertEqual(len(ignored), 1)

    def test_jna_and_oshi_mentions_are_not_blanket_exceptions(self):
        for lines in [[record("other.mod.Client", "oshi integration failed"), "com.sun.jna.platform.win32.Win32Exception: access denied"],
                      ['java.lang.NullPointerException: Cannot invoke "oshi.software.os.OSProcess.getUserTime()" because "process" is null'],
                      [record("net.minecraft.SystemReport", "Failed to get system info for Process Loads", "WARN"), "java.lang.NullPointerException: unrelated"]]:
            with self.subTest(lines=lines):
                bad, ignored = classify_log_errors(lines)
                self.assertTrue(bad)
                self.assertEqual(ignored, [])

    def test_fatal_is_never_ignored(self):
        line = record("oshi.driver.windows.registry.HkeyPerformanceDataUtil", "fatal failure", "FATAL")
        self.assertEqual(classify_log_errors([line]), ([line], []))


if __name__ == "__main__":
    unittest.main()
