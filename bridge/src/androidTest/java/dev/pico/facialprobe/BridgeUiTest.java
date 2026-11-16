package dev.pico.facialprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.Switch;
import android.widget.EditText;
import android.widget.TextView;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;

/** Device UI smoke test using platform instrumentation only, no downloaded test frameworks. */
public final class BridgeUiTest extends Instrumentation {
    private BridgeActivity activity;
    private ExpressionActivity monitorActivity;
    private int checks;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args); start();
    }
    private void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private Activity screen() { return monitorActivity == null ? activity : monitorActivity; }
    private String text(int id) { return ((TextView) screen().findViewById(id)).getText().toString(); }
    private void click(int id) { screen().findViewById(id).performClick(); }
    private void screenshot(String name) {
        runOnMainSync(() -> {
            android.view.View view = screen().getWindow().getDecorView();
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { bitmap.recycle(); }
        });
    }
    private BridgeActivity open() {
        ActivityMonitor monitor = addMonitor(BridgeActivity.class.getName(), null, false);
        getTargetContext().startActivity(new Intent(getTargetContext(), BridgeActivity.class)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Activity started = waitForMonitorWithTimeout(monitor, 10000);
        removeMonitor(monitor);
        if (started == null) throw new IllegalStateException("Bridge Activity was not launched within 10 seconds");
        return (BridgeActivity) started;
    }
    private void openMonitor() {
        runOnMainSync(() -> click(R.id.tab_diagnostics));
        ActivityMonitor monitor = addMonitor(ExpressionActivity.class.getName(), null, false);
        runOnMainSync(() -> click(R.id.show_expressions));
        Activity started = waitForMonitorWithTimeout(monitor, 10000);
        removeMonitor(monitor);
        if (started == null) throw new IllegalStateException("Expression inspector did not open within 10 seconds");
        monitorActivity = (ExpressionActivity) started; waitForIdleSync();
    }
    private void inspectorTest() {
        openMonitor();
        runOnMainSync(() -> {
            check(monitorActivity.findViewById(R.id.expression_screen) != null, "Dedicated child screen");
            check(monitorActivity.findViewById(R.id.start_button) == null, "Service controls do not crowd inspector");
            ExpressionStatus.Accumulator readings = new ExpressionStatus.Accumulator();
            byte[] sample = new byte[384]; java.nio.ByteBuffer source = TrackingData.bytes(sample);
            source.putLong(0, SystemClock.elapsedRealtimeNanos()); source.putFloat(296, 1); source.putFloat(300, 1);
            for (int shape : TrackingData.EYE_SHAPES) source.putFloat(8 + shape * 4, .35f);
            readings.accept(java.util.Collections.singletonList(sample), 1);
            BridgeState.expressions = readings.snapshot(); BridgeState.udp = true;
            byte[] eye = new byte[200]; java.nio.ByteBuffer nativeSource = TrackingData.bytes(eye);
            nativeSource.putLong(168, SystemClock.elapsedRealtimeNanos());
            nativeSource.putInt(0, 0x904); nativeSource.putInt(4, 0x904); nativeSource.putInt(8, 2);
            nativeSource.putFloat(84, 1); nativeSource.putFloat(88, 1);
            nativeSource.putFloat(92, 35); nativeSource.putFloat(96, 42);
            NativeEyeStatus.Accumulator nativeReadings = new NativeEyeStatus.Accumulator();
            nativeReadings.accept(java.util.Collections.singletonList(eye), 1);
            BridgeState.nativeEye = nativeReadings.snapshot(true);
            monitorActivity.render();
            check(monitorActivity.findViewById(R.id.expression_eyes).isSelected(), "Default tab is eye expressions");
            check(text(R.id.expression_summary).contains("19 项有效"), "Normal eye-only source has 19 valid expressions");
            int[] regions = {R.id.expression_eyes, R.id.expression_face, R.id.expression_mouth, R.id.expression_native};
            android.view.ViewGroup tabs = (android.view.ViewGroup) monitorActivity.findViewById(regions[0]).getParent();
            for (int i = 0; i < regions.length; i++) {
                check(tabs.getChildAt(i).getId() == regions[i], "Native gaze is last; expression order is unchanged");
                ExpressionPanel original = new ExpressionPanel(monitorActivity);
                original.findViewById(regions[i]).performClick();
                Bundle saved = new Bundle(); original.saveState(saved);
                check(saved.getInt("region_id") == regions[i], "Region state stores its control ID, not position");
                ExpressionPanel restored = new ExpressionPanel(monitorActivity);
                restored.restoreState(saved);
                check(restored.findViewById(regions[i]).isSelected(), "Each region restores to the same tab");
            }
            click(R.id.expression_native);
            check(text(R.id.expression_summary).contains("13/13"), "Last tab covers all native floats");
            check(monitorActivity.findViewById(R.id.native_readouts).getContentDescription().toString().contains("3.5"), "Pupil is displayed in millimeters");
            click(R.id.expression_mouth);
            check(text(R.id.expression_summary).contains("0 项有效"), "Mouth tab respects capture channels");
            click(R.id.expression_eyes);
            EditText search = monitorActivity.findViewById(R.id.expression_search);
            check(search.getVisibility() == View.GONE, "Search stays out of the default layout");
            search.setText("does-not-exist");
            check(monitorActivity.findViewById(R.id.expression_empty).getVisibility() == View.VISIBLE, "Search empty state");
            search.setText("EyeBlinkR");
            check(monitorActivity.findViewById(R.id.expression_empty).getVisibility() == View.GONE, "Right-side search finds its bilateral row");
            search.setText("");
            check(text(R.id.expression_freeze).equals("冻结"), "Compact Chinese freeze action");
            click(R.id.expression_freeze);
            check(text(R.id.expression_summary).contains("已冻结"), "Frozen state marked");
            BridgeState.expressions = ExpressionStatus.empty(); monitorActivity.render();
            check(text(R.id.expression_summary).contains("19 项有效"), "Freeze holds values");
            BridgeState.nativeEye = NativeEyeStatus.empty(); monitorActivity.render();
            click(R.id.expression_native);
            check(text(R.id.expression_summary).contains("13/13"), "Freeze also retains native fields");
            click(R.id.expression_eyes);
            check(BridgeState.gaze.id.equals("0123456789abcdef"), "Inspector leaves gaze subscription unchanged");
            click(R.id.expression_freeze);
            check(text(R.id.expression_summary).contains("0 项有效"), "Resume reads current snapshot");
        });
        waitForIdleSync(); screenshot("ui-expressions.png");
        runOnMainSync(() -> click(R.id.expression_back)); waitForIdleSync(); monitorActivity = null;
        runOnMainSync(() -> {
            check(activity.findViewById(R.id.page_diagnostics).getVisibility() == View.VISIBLE, "Return preserves diagnostics");
            click(R.id.language_en);
        });
        openMonitor();
        runOnMainSync(() -> {
            check(text(R.id.expression_freeze).equals("Freeze"), "English inspector");
            check(monitorActivity.findViewById(R.id.expression_eyes).isSelected(), "Reopening defaults to eye expressions in English");
            click(R.id.expression_back);
        });
        waitForIdleSync(); monitorActivity = null;
    }
    @Override public void onStart() {
        SharedPreferences prefs = getTargetContext().getSharedPreferences("bridge", 0);
        Map<String, ?> saved = prefs.getAll(); Bundle result = new Bundle(); int resultCode = Activity.RESULT_CANCELED;
        GazeStatus previousGaze = BridgeState.gaze;
        ExpressionStatus previousExpressions = BridgeState.expressions;
        NativeEyeStatus previousNative = BridgeState.nativeEye;
        OutputStatus previousOutputs = BridgeState.outputs;
        boolean previousUdp = BridgeState.udp;
        long previousPackets = BridgeState.gazePackets;
        try {
            prefs.edit().putBoolean("transmit_eye", true).putBoolean("transmit_face", true)
                    .putString("language", "en").commit();
            activity = open();
            waitForIdleSync(); screenshot("ui-en.png");
            runOnMainSync(() -> {
                check(text(R.id.switch_eye).equals("Transmit eye tracking"), "English eye label");
                check(text(R.id.switch_face).equals("Transmit face tracking"), "English face label");
                click(R.id.switch_face);
                check(BridgePreferences.selection(prefs) == 1, "Eye-only preference applied");
                click(R.id.switch_eye); click(R.id.switch_face);
                check(BridgePreferences.selection(prefs) == 2, "Face-only preference applied");
                click(R.id.switch_eye);
                check(BridgePreferences.selection(prefs) == 3, "Both streams selectable");
                click(R.id.switch_eye); click(R.id.switch_face);
                check(BridgePreferences.selection(prefs) == 0, "Both streams can be paused");
                check(text(R.id.eye_rate).contains("0.0 Hz") && text(R.id.face_rate).contains("0.0 Hz"), "Disabled UI rates zero");
                click(R.id.language_zh);
                check(text(R.id.switch_eye).equals("传输眼追"), "Chinese eye label");
                check(text(R.id.switch_face).equals("传输面捕"), "Chinese face label");
                check(text(R.id.start_button).equals("启动"), "Chinese action label");
                check(!((Switch) activity.findViewById(R.id.switch_eye)).isChecked(), "Language switch keeps transmission selection");
            });
            waitForIdleSync(); screenshot("ui-zh.png");
            runOnMainSync(() -> activity.finish());
            waitForIdleSync(); activity = open();
            runOnMainSync(() -> {
                check(text(R.id.switch_eye).equals("传输眼追"), "Language persists on reopen");
                check(!((Switch) activity.findViewById(R.id.switch_face)).isChecked(), "Switch persists on reopen");
                click(R.id.language_en);
                check(text(R.id.stop_button).equals("Stop"), "Switch back to English");
                check(text(R.id.eye_rate).contains("Hz") && text(R.id.face_rate).contains("Hz"), "Separate rate displays");
                click(R.id.tab_gaze);
                check(activity.findViewById(R.id.page_gaze).getVisibility() == View.VISIBLE, "Dedicated gaze page");
                check(activity.findViewById(R.id.page_avatar).getVisibility() == View.GONE, "Only the selected page is visible");
                long ms = SystemClock.elapsedRealtime(), ns = SystemClock.elapsedRealtimeNanos();
                BridgeState.gaze = new GazeStatus(GazeStatus.Link.SUBSCRIBED, "192.0.2.10:50001",
                        "0123456789abcdef", 7, ms + 3000, ns, true, "");
                BridgeState.gazePackets = 12;
                click(R.id.tab_gaze);
                check(text(R.id.gaze_status).equals("Subscribed"), "Subscription is distinct from local transmission");
                check(text(R.id.gaze_data).equals("Transmission off"), "Shared eye switch is respected");
                check(text(R.id.gaze_client).equals("192.0.2.10:50001"), "Gaze endpoint");
                check(text(R.id.gaze_id).equals("0123456789abcdef"), "Subscription ID");
                check(text(R.id.gaze_epoch).equals("0000000000000007"), "Epoch");
                check(text(R.id.gaze_packets).equals("12"), "Gaze packet count");
                click(R.id.gaze_eye_settings);
                check(activity.findViewById(R.id.page_avatar).getVisibility() == View.VISIBLE, "Gaze settings links to the shared switch");
                click(R.id.switch_eye);
                ns = SystemClock.elapsedRealtimeNanos(); ms = SystemClock.elapsedRealtime();
                BridgeState.gaze = new GazeStatus(GazeStatus.Link.SUBSCRIBED, "192.0.2.10:50001",
                        "0123456789abcdef", 7, ms + 3000, ns, false, "");
                click(R.id.tab_gaze);
                check(text(R.id.gaze_data).equals("Fresh sample · invalid gaze"), "Invalid gaze is not reported as valid");
                BridgeState.gaze = new GazeStatus(GazeStatus.Link.SUBSCRIBED, "192.0.2.10:50001",
                        "0123456789abcdef", 7, ms - 1, ns, true, "");
                click(R.id.tab_gaze);
                check(text(R.id.gaze_status).equals("Subscription expired"), "Lease expiration visible without another packet");
                click(R.id.language_zh);
                check(activity.findViewById(R.id.page_gaze).getVisibility() == View.VISIBLE, "Language change preserves the selected page");
                check(text(R.id.gaze_status).equals("订阅已过期"), "Chinese subscription state");
                click(R.id.tab_diagnostics);
                click(R.id.show_logs);
                check(activity.findViewById(R.id.diagnostic_logs).getVisibility() == View.VISIBLE, "Logs have their own diagnostics page");
                click(R.id.show_logs);
                check(activity.findViewById(R.id.diagnostic_logs).getVisibility() == View.GONE, "Logs can be collapsed");
            });
            waitForIdleSync(); screenshot("ui-diagnostics.png");
            runOnMainSync(() -> click(R.id.tab_gaze));
            waitForIdleSync(); screenshot("ui-gaze.png");
            inspectorTest();
            result.putString("stream", "PASS: " + checks + " bilingual UI/persistence/selection checks\n");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
        } finally {
            // Only restore test-touched preferences; never clear grants or unrelated settings.
            SharedPreferences.Editor edit = prefs.edit();
            for (String key : new String[]{"transmit_eye", "transmit_face", "language"}) {
                Object value = saved.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean)value);
                else if (value instanceof String) edit.putString(key, (String)value);
                else edit.remove(key);
            }
            edit.commit();
            BridgeState.gaze = previousGaze; BridgeState.gazePackets = previousPackets;
            BridgeState.expressions = previousExpressions; BridgeState.udp = previousUdp;
            BridgeState.nativeEye = previousNative; BridgeState.outputs = previousOutputs;
            if (monitorActivity != null) runOnMainSync(() -> monitorActivity.finish());
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(resultCode, result);
    }
}
