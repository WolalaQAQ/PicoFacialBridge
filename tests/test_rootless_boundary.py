"""The optional module configuration must not give Bridge root responsibilities."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]


class RootlessBoundaryTest(unittest.TestCase):
    def test_bridge_does_not_execute_root_or_module_configuration_commands(self):
        for path in (ROOT/'bridge/src').rglob('*.java'):
            with self.subTest(path=path):
                source = path.read_text(encoding='utf-8')
                self.assertNotIn('ProcessBuilder("su"', source)
                self.assertNotIn('picoet-config', source)
                self.assertNotIn('reload-processing', source)

    def test_no_processing_editor_or_root_preview_is_packaged(self):
        manifest = ET.parse(ROOT/'bridge/src/main/AndroidManifest.xml')
        for activity in manifest.findall('application/activity'):
            self.assertNotIn('PostProcessing', activity.get('{http://schemas.android.com/apk/res/android}name', ''))
        self.assertNotIn('processingPreview', (ROOT/'bridge/build.gradle').read_text())


if __name__ == '__main__':
    unittest.main()
