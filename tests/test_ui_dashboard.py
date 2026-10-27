"""Static UI contract checks; no Android runtime, network or device access."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'bridge/src/main/java/dev/pico/facialprobe'
RES = ROOT / 'bridge/src/main/res'


class DashboardSourceTest(unittest.TestCase):
    def setUp(self):
        self.activity = (JAVA / 'BridgeActivity.java').read_text(encoding='utf-8')
        self.service = (JAVA / 'BridgeService.java').read_text(encoding='utf-8')

    def test_every_ui_string_and_id_is_defined(self):
        strings = {entry.attrib['name'] for entry in ET.parse(RES / 'values/strings.xml').getroot()}
        ids = {entry.attrib['name'] for entry in ET.parse(RES / 'values/ids.xml').getroot()}
        for source in (self.activity, (JAVA / 'BridgeUi.java').read_text(encoding='utf-8')):
            self.assertFalse(set(re.findall(r'R\.string\.(\w+)', source)) - strings)
            self.assertFalse(set(re.findall(r'(?<!android\.)R\.id\.(\w+)', source)) - ids)

    def test_three_independent_pages_and_accessible_controls(self):
        for name in ('avatar', 'gaze', 'diagnostics'):
            self.assertIn('R.id.tab_' + name, self.activity)
            self.assertIn('R.id.page_' + name, self.activity)
        self.assertIn('i == index ? View.VISIBLE : View.GONE', self.activity)
        self.assertIn('saved.putInt("tab", selectedTab)', self.activity)
        self.assertIn('selectTab(selectedTab)', self.activity)
        tokens = (JAVA / 'BridgeUi.java').read_text(encoding='utf-8')
        self.assertIn('setMinHeight(dp(48))', tokens)
        self.assertIn('getDisplayMetrics().density', tokens)

    def test_gaze_fields_are_not_aliases_of_avatar_counters(self):
        for name in ('gaze_status', 'gaze_client', 'gaze_id', 'gaze_epoch', 'gaze_lease', 'gaze_data', 'gaze_age', 'gaze_error'):
            self.assertIn('R.id.' + name, self.activity)
        self.assertIn('GazeStatus snapshot = BridgeState.gaze', self.activity)
        self.assertIn('BridgeState.gazePackets', self.activity)
        self.assertIn('BridgeState.gazeRates.hz(nowNanos)', self.activity)
        self.assertIn('snapshot.connection(nowMs)', self.activity)
        self.assertIn('snapshot.sourceAgeNanos(nowNanos)', self.activity)

    def test_short_and_narrow_windows_keep_controls_reachable(self):
        self.assertIn('configuration.screenHeightDp < 600', self.activity)
        self.assertIn('dashboard.setFillViewport(true)', self.activity)
        self.assertIn('dashboard.addView(shell)', self.activity)
        self.assertIn('if (shortWindow) {\n                pages[i] = content;', self.activity)
        self.assertIn('shortWindow ? new LinearLayout.LayoutParams(-1, -2)', self.activity)
        self.assertIn('screenWidthDp < 480', self.activity)
        self.assertIn('languages.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); parent.addView(languages)', self.activity)
        self.assertIn('ui.dp(Math.min(configuration.screenWidthDp, 1000))', self.activity)

    def test_dashboard_checks_are_wired_into_ci(self):
        workflow = (ROOT / '.github/workflows/android.yml').read_text(encoding='utf-8')
        host_tests = (ROOT / 'tests/run-host-tests.sh').read_text(encoding='utf-8')
        self.assertIn('python3 tests/test_ui_dashboard.py', workflow)
        self.assertIn('GazeStatusTest', host_tests)

    def test_ui_does_not_send_subscribe_or_unsubscribe_commands(self):
        for command in ('PXR_GAZE_SUB', 'PXR_GAZE_STOP', 'new GazeSubscription', 'DatagramSocket', 'MulticastSocket'):
            self.assertNotIn(command, self.activity)
        self.assertIn('gazeError = error.toString()', self.service)
        self.assertIn('gaze.sourceNanos(), gaze.sourceValid()', self.service)
        self.assertIn('nextGazeUi = uiNow + 100', self.service)

    def test_language_resources_match_and_have_unique_keys(self):
        resources = []
        for folder in ('values', 'values-zh'):
            entries = ET.parse(RES / folder / 'strings.xml').getroot()
            names = [entry.attrib['name'] for entry in entries]
            self.assertEqual(len(names), len(set(names)))
            resources.append({entry.attrib['name']: entry.text for entry in entries})
        self.assertEqual(resources[0].keys(), resources[1].keys())
        for name in resources[0]:
            self.assertEqual(re.findall(r'%\d+\$[\d.]*[sdf]', resources[0][name]),
                             re.findall(r'%\d+\$[\d.]*[sdf]', resources[1][name]))


if __name__ == '__main__':
    unittest.main()
