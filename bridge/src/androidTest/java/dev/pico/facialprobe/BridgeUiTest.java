package dev.pico.facialprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Map;
import java.io.File;
import java.io.FileOutputStream;

/** Device UI smoke test using platform instrumentation only, no downloaded test frameworks. */
public final class BridgeUiTest extends Instrumentation {
    private BridgeActivity activity;
    private int checks;
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    private void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    private String text(int id) { return ((TextView) activity.findViewById(id)).getText().toString(); }
    private void click(int id) { activity.findViewById(id).performClick(); }
    private void screenshot(String name) {
        runOnMainSync(() -> {
            android.view.View view = activity.getWindow().getDecorView();
            Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getFilesDir(), name))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
            finally { bitmap.recycle(); }
        });
    }
    private BridgeActivity open() {
        return (BridgeActivity) startActivitySync(new Intent(getTargetContext(), BridgeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    @Override public void onStart() {
        SharedPreferences prefs = getTargetContext().getSharedPreferences("bridge", 0);
        Map<String, ?> saved = prefs.getAll(); Bundle result = new Bundle(); int resultCode = Activity.RESULT_CANCELED;
        try {
            prefs.edit().putBoolean("transmit_eye", true).putBoolean("transmit_face", true).putString("language", "en").commit();
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
            });
            result.putString("stream", "PASS: " + checks + " bilingual UI/persistence/selection checks\n");
            resultCode = Activity.RESULT_OK;
        } catch (Throwable failure) {
            result.putString("stream", "FAIL: " + failure + "\n");
        } finally {
            // Only restore the three test-touched preferences; never clear grants or unrelated settings.
            SharedPreferences.Editor edit = prefs.edit();
            for (String key : new String[]{"transmit_eye", "transmit_face", "language"}) {
                Object value = saved.get(key);
                if (value instanceof Boolean) edit.putBoolean(key, (Boolean)value);
                else if (value instanceof String) edit.putString(key, (String)value);
                else edit.remove(key);
            }
            edit.commit();
            if (activity != null) runOnMainSync(() -> activity.finish());
        }
        finish(resultCode, result);
    }
}
