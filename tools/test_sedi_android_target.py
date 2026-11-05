import subprocess
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from sedi_android_target import EMULATOR_PACKAGE, PHYSICAL_PACKAGE, select_package, verify_physical_target


def response(stdout="", returncode=0):
    return SimpleNamespace(stdout=stdout, returncode=returncode)


class AndroidTargetTest(unittest.TestCase):
    def test_modes_require_explicit_physical_opt_in(self):
        self.assertEqual(select_package("emulator-5554", False), EMULATOR_PACKAGE)
        self.assertEqual(select_package("SERIAL", True), PHYSICAL_PACKAGE)
        for serial, physical in (("SERIAL", False), ("emulator-5554", True), ("", True)):
            with self.subTest(serial=serial, physical=physical), self.assertRaises(ValueError):
                select_package(serial, physical)

    def test_physical_checks_only_the_isolated_package(self):
        runner = f"instrumentation:{PHYSICAL_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner (target={PHYSICAL_PACKAGE})"
        results = [response("device"), response("SERIAL"), response("0"),
                   response("package:/data/app/test.apk"), response(runner), response("uid=10001")]
        with patch("sedi_android_target.subprocess.run", side_effect=results) as run:
            verify_physical_target(["adb", "-s", "SERIAL"], "SERIAL")
        calls = [call.args[0] for call in run.call_args_list]
        self.assertIn(["adb", "-s", "SERIAL", "shell", "run-as", PHYSICAL_PACKAGE, "id"], calls)
        self.assertNotIn(["adb", "-s", "SERIAL", "shell", "run-as", EMULATOR_PACKAGE, "id"], calls)

    def test_rejects_wrong_serial_emulator_or_missing_isolation(self):
        runner = f"instrumentation:{PHYSICAL_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner (target={PHYSICAL_PACKAGE})"
        cases = [
            ([response("device"), response("OTHER")], "serial"),
            ([response("device"), response("SERIAL"), response("1")], "emulator"),
            ([response("device"), response("SERIAL"), response("0"), response(returncode=1)], "not installed"),
            ([response("device"), response("SERIAL"), response("0"), response("package:/test.apk"),
              response(runner.replace(f"target={PHYSICAL_PACKAGE}", f"target={EMULATOR_PACKAGE}"))], "instrumentation"),
        ]
        for results, message in cases:
            with self.subTest(message=message), patch("sedi_android_target.subprocess.run", side_effect=results):
                with self.assertRaisesRegex(RuntimeError, message):
                    verify_physical_target(["adb", "-s", "SERIAL"], "SERIAL")

    def test_rejects_non_debuggable_target(self):
        runner = f"instrumentation:{PHYSICAL_PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner (target={PHYSICAL_PACKAGE})"
        results = [response("device"), response("SERIAL"), response("0"), response("package:/test.apk"),
                   response(runner), subprocess.CalledProcessError(1, "run-as")]
        with patch("sedi_android_target.subprocess.run", side_effect=results), self.assertRaises(subprocess.CalledProcessError):
            verify_physical_target(["adb", "-s", "SERIAL"], "SERIAL")


if __name__ == "__main__":
    unittest.main()
