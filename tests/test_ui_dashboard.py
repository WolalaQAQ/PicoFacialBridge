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
        for path in JAVA.glob('*.java'):
            source = path.read_text(encoding='utf-8')
            self.assertFalse(set(re.findall(r'(?<!android\.)R\.string\.(\w+)', source)) - strings)
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
        self.assertNotIn('R.id.tab_expressions', self.activity)
        self.assertNotIn('R.id.page_expressions', self.activity)

    def test_expression_monitor_is_a_diagnostics_child_screen(self):
        diagnostics = self.activity.split('private void buildDiagnostics(', 1)[1].split('private void selectTab(', 1)[0]
        self.assertIn('new Intent(this, ExpressionActivity.class)', diagnostics)
        self.assertIn('R.id.show_expressions', diagnostics)
        self.assertLess(diagnostics.index('R.id.show_expressions'), diagnostics.index('R.string.ui_runtime'))
        self.assertNotIn('new ExpressionPanel', self.activity)
        inspector = (JAVA / 'ExpressionActivity.java').read_text(encoding='utf-8')
        self.assertIn('new ExpressionPanel(this)', inspector)
        self.assertIn('choreographer.removeFrameCallback(refresh)', inspector)
        self.assertNotIn('buildServiceCard', inspector)
        manifest = ET.parse(ROOT / 'bridge/src/main/AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        child = next(item for item in manifest.findall('application/activity')
                     if item.get(android + 'name').endswith('.ExpressionActivity'))
        self.assertEqual(child.get(android + 'exported'), 'false')
        panel = (JAVA / 'ExpressionPanel.java').read_text(encoding='utf-8')
        self.assertIn('new Button[4]', panel)
        self.assertIn('new PopupMenu', panel)
        self.assertIn('row.left', panel)
        self.assertIn('row.right', panel)
        self.assertIn('R.id.expression_native', panel)
        self.assertIn('outputs.describe()', panel)
        self.assertIn('nativeEye.accept(eyes, active ? captureSelection : 0)', self.service)
        self.assertLess(self.service.index('sendingSocket.send(new DatagramPacket(payload'),
                        self.service.index('outputs.sent(payload, false)'))
        self.assertLess(self.service.index('socket.send(new DatagramPacket(gazePacket'),
                        self.service.index('outputs.sent(gazePacket, true)'))

    def test_inspector_keeps_native_eye_last_and_restores_by_id(self):
        panel = (JAVA / 'ExpressionPanel.java').read_text(encoding='utf-8')
        ids = panel.split('int[] ids = {', 1)[1].split('};', 1)[0]
        labels = panel.split('int[] labels = {', 1)[1].split('};', 1)[0]
        self.assertEqual(re.findall(r'R\.id\.(\w+)', ids),
                         ['expression_eyes', 'expression_face', 'expression_mouth', 'expression_native'])
        self.assertEqual(re.findall(r'R\.string\.(\w+)', labels),
                         ['expressions_region_eyes', 'expressions_region_face', 'expressions_region_mouth', 'native_region'])
        self.assertIn('selectRegion(0)', panel)
        self.assertIn('NATIVE_REGION = 3', panel)
        self.assertIn('ExpressionCatalog.GROUPS[region]', panel)
        self.assertNotIn('GROUPS[region - 1]', panel)
        self.assertIn('saved.putInt("region_id", groups[region].getId())', panel)
        self.assertIn('saved.getInt("region_id", R.id.expression_eyes)', panel)
        self.assertIn('groups[i].getId() == selected', panel)

    def test_boot_startup_is_not_part_of_the_app(self):
        manifest = ET.parse(ROOT / 'bridge/src/main/AndroidManifest.xml').getroot()
        android = '{http://schemas.android.com/apk/res/android}'
        self.assertNotIn('android.permission.RECEIVE_BOOT_COMPLETED',
                         {entry.get(android + 'name') for entry in manifest.findall('uses-permission')})
        self.assertFalse(manifest.findall('application/receiver'))
        self.assertFalse((JAVA / 'BootReceiver.java').exists())
        for path in list(JAVA.glob('*.java')) + list(RES.glob('values*/strings.xml')):
            self.assertNotIn('start_on_boot', path.read_text(encoding='utf-8'))
            self.assertNotIn('autostart', path.read_text(encoding='utf-8'))
        self.assertNotIn('test_boot_startup.py', (ROOT / '.github/workflows/android.yml').read_text())

    def test_manual_start_reports_missing_wifi_without_waiting(self):
        setup = self.service.split('private void runBridge() throws Exception {', 1)[1].split('BridgeState.log("BEGIN', 1)[0]
        self.assertIn('NetworkInterface.getByName("wlan0")', setup)
        self.assertIn('if (network == null || !network.isUp()) throw', setup)
        self.assertIn('if (own == null) throw', setup)
        self.assertIn('candidate instanceof Inet4Address', setup)
        for text in ('while (', 'SystemClock.sleep', 'detail_wifi', 'acquire()'):
            self.assertNotIn(text, setup)
        self.assertNotIn('detail_wifi', self.service)
        self.assertLess(self.service.index('if (own == null) throw'), self.service.index('new TrackingSession'))

    def test_expression_display_is_read_only_and_follows_display_frames(self):
        panel = (JAVA / 'ExpressionPanel.java').read_text(encoding='utf-8')
        for command in ('startForegroundService', 'DatagramSocket', 'MulticastSocket', '.edit()', 'new TrackingSession'):
            self.assertNotIn(command, panel)
        self.assertIn('if (frozen) return;', panel)
        inspector = (JAVA / 'ExpressionActivity.java').read_text(encoding='utf-8')
        self.assertIn('Choreographer.FrameCallback', inspector)
        self.assertIn('choreographer.postFrameCallback(this)', inspector)
        self.assertNotIn('postDelayed', inspector)
        self.assertIn('expressions.accept(faces, active ? primaryCaptureSelection : 0)', self.service)
        self.assertNotIn('nextExpressionsUi', self.service)
        self.assertIn('BridgeState.expressions = expressions.snapshot()', self.service)
        self.assertIn('BridgeState.nativeEye = nativeEye.snapshot(', self.service)
        self.assertIn('ExpressionStatusTest', (ROOT / 'tests/run-host-tests.sh').read_text())

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
        self.assertIn('python3 tests/test_inspector_frames.py', workflow)
        self.assertIn('python3 tests/test_rootless_boundary.py', workflow)
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
