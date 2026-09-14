"""Check the built bridge APK without a device or third-party Python packages.

Set APK_PATH to inspect a different variant; the default is the release APK.
"""

import os
from pathlib import Path
import unittest
import zipfile


ROOT = Path(__file__).resolve().parents[1]
APK = Path(os.environ.get(
    "APK_PATH", ROOT / "bridge/build/outputs/apk/release/bridge-release-unsigned.apk"
))


class ApkDistributionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.apk = zipfile.ZipFile(APK)
        cls.addClassCleanup(cls.apk.close)

    def test_project_mit_license_is_bundled(self):
        self.assertIn("assets/LICENSE", self.apk.namelist())
        expected = (ROOT / "LICENSE").read_text(encoding="utf-8")
        self.assertIn("MIT License", expected)
        self.assertEqual(self.apk.read("assets/LICENSE").decode("utf-8"), expected)

    def test_upstream_attribution_is_bundled(self):
        self.assertIn("assets/THIRD_PARTY_NOTICES.md", self.apk.namelist())
        expected = (ROOT / "THIRD_PARTY_NOTICES.md").read_text(encoding="utf-8")
        self.assertIn("Copyright (c) 2026 thoricelli", expected)
        self.assertEqual(
            self.apk.read("assets/THIRD_PARTY_NOTICES.md").decode("utf-8"), expected
        )

    def test_only_our_arm64_native_library_is_bundled(self):
        libraries = sorted(name for name in self.apk.namelist() if name.endswith(".so"))
        self.assertEqual(libraries, ["lib/arm64-v8a/libpico_probe.so"])

    def test_no_keys_or_private_evidence_are_bundled(self):
        for name in self.apk.namelist():
            with self.subTest(name=name):
                self.assertFalse(name.endswith((".keystore", ".jks", ".pem", ".p12")))
                self.assertFalse(name.startswith(("assets/evidence/", "assets/reference/")))


if __name__ == "__main__":
    unittest.main()
