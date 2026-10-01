package dev.pico.facialprobe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

public final class BridgeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status, details, logs, eyeRate, faceRate, modeText;
    private Button logButton;
    private SharedPreferences prefs;
    private Context localized;
    private String pending = BridgeService.START;
    private boolean showingLogs;
    private final Runnable refresh = new Runnable() { public void run() { render(); main.postDelayed(this, 500); } };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = BridgePreferences.get(this);
        buildUi();
        if (getIntent().getBooleanExtra("autostart", false)) permissionsThen(BridgeService.START);
    }
    private String s(int id, Object... args) { return localized.getString(id, args); }
    private void buildUi() {
        localized = BridgePreferences.localized(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(32, 24, 32, 24);
        content.setBackgroundColor(Color.rgb(18, 24, 31)); scroll.addView(content);
        text(content, "PicoFacialBridge", 30, Color.WHITE);
        text(content, s(R.string.tagline), 13, Color.rgb(90, 209, 178));
        modeText = text(content, "", 15, Color.rgb(158, 174, 190)); modeText.setId(R.id.mode);
        LinearLayout languages = new LinearLayout(this); content.addView(languages);
        text(languages, s(R.string.language), 15, Color.WHITE);
        button(languages, "中文", () -> setLanguage("zh")).setId(R.id.language_zh);
        button(languages, "English", () -> setLanguage("en")).setId(R.id.language_en);
        languages.findViewById(BridgePreferences.language(this).equals("zh") ? R.id.language_zh : R.id.language_en).setEnabled(false);
        status = text(content, "", 26, Color.WHITE); status.setId(R.id.status);
        addSwitch(content, R.id.switch_eye, R.string.transmit_eye, BridgePreferences.EYE);
        eyeRate = text(content, "", 22, Color.rgb(90, 209, 178)); eyeRate.setId(R.id.eye_rate);
        addSwitch(content, R.id.switch_face, R.string.transmit_face, BridgePreferences.FACE);
        faceRate = text(content, "", 22, Color.rgb(90, 209, 178)); faceRate.setId(R.id.face_rate);
        text(content, s(R.string.rate_hint), 14, Color.rgb(158, 174, 190));
        LinearLayout buttons = new LinearLayout(this); content.addView(buttons);
        button(buttons, s(R.string.start), () -> permissionsThen(BridgeService.START)).setId(R.id.start_button);
        button(buttons, s(R.string.stop), () -> send(BridgeService.STOP)).setId(R.id.stop_button);
        button(buttons, s(R.string.restart), () -> permissionsThen(BridgeService.RESTART)).setId(R.id.restart_button);
        details = text(content, "", 17, Color.rgb(204, 214, 225));
        text(content, s(R.string.selection_hint), 14, Color.rgb(158, 174, 190));
        logButton = button(content, "", () -> { showingLogs = !showingLogs; render(); });
        text(content, s(R.string.logs_hint), 13, Color.rgb(158, 174, 190));
        text(content, s(R.string.usage_hint), 15, Color.rgb(158, 174, 190));
        logs = text(content, "", 13, Color.rgb(154, 190, 171)); logs.setTextIsSelectable(true);
        setContentView(scroll);
        render();
    }
    private void setLanguage(String language) {
        prefs.edit().putString(BridgePreferences.LANGUAGE, language).apply();
        buildUi();
    }
    private void addSwitch(LinearLayout parent, int id, int label, String key) {
        Switch toggle = new Switch(this); toggle.setId(id); toggle.setText(s(label));
        toggle.setTextSize(20); toggle.setTextColor(Color.WHITE); toggle.setPadding(0, 12, 0, 4);
        toggle.setChecked(prefs.getBoolean(key, true)); parent.addView(toggle);
        toggle.setOnCheckedChangeListener((view, checked) -> {
            prefs.edit().putBoolean(key, checked).apply(); render();
        });
    }
    private TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setPadding(0, 8, 0, 8); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, String value, Runnable action) {
        Button button = new Button(this); button.setText(value); button.setOnClickListener(v -> action.run()); parent.addView(button); return button;
    }
    private void permissionsThen(String action) {
        pending = action;
        String[] permissions = {"com.picovr.permission.EYE_TRACKING", "com.picovr.permission.FACE_TRACKING"};
        for (String permission : permissions) if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) { requestPermissions(permissions, 1); return; }
        send(action);
    }
    private void send(String action) { startForegroundService(new Intent(this, BridgeService.class).setAction(action)); }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] granted) {
        super.onRequestPermissionsResult(code, permissions, granted);
        if (code != 1 || granted.length != 2) return;
        for (int grant : granted) if (grant != PackageManager.PERMISSION_GRANTED) {
            BridgeState.status = R.string.status_error; BridgeState.detail = R.string.detail_permission; BridgeState.error = ""; render(); return;
        }
        send(pending);
    }
    private void render() {
        if (status == null) return;
        int mask = BridgePreferences.selection(prefs);
        boolean sendEye = (mask & 1) != 0, sendFace = (mask & 2) != 0;
        double[] hz = BridgeState.rates.hz(SystemClock.elapsedRealtimeNanos());
        eyeRate.setText(s(R.string.eye_rate, sendEye && BridgeState.udp ? hz[0] : 0d));
        faceRate.setText(s(R.string.face_rate, sendFace && BridgeState.udp ? hz[1] : 0d));
        TrackingMode.Info mode = BridgeState.mode;
        modeText.setText(s(R.string.mode_line, mode.enhance ? s(R.string.mode_enhance, mode.plugin) : s(R.string.mode_normal)));
        status.setText(s(BridgeState.status));
        status.setTextColor(BridgeState.status == R.string.status_running ? Color.rgb(90, 209, 178) : Color.WHITE);
        String client = BridgeState.client.isEmpty() ? s(BridgeState.clientMessage) : BridgeState.client;
        details.setText(s(BridgeState.detail) + (BridgeState.error.isEmpty() ? "" : "\n" + BridgeState.error) + "\n"
                + s(R.string.tracking_details,
                s(!sendEye ? R.string.disabled : BridgeState.eye ? R.string.live : R.string.no_data),
                s(!sendFace ? R.string.disabled : BridgeState.face ? R.string.live : R.string.no_data),
                BridgeState.udp ? s(R.string.listening, BridgeState.address) : s(R.string.status_stopped),
                client, BridgeState.eyeFrames, BridgeState.faceFrames, BridgeState.packets));
        logButton.setText(s(showingLogs ? R.string.hide_logs : R.string.show_logs));
        logs.setVisibility(showingLogs ? View.VISIBLE : View.GONE);
        if (showingLogs) logs.setText(BridgeState.logs());
    }
    @Override public void onResume() { super.onResume(); main.post(refresh); }
    @Override public void onPause() { main.removeCallbacks(refresh); super.onPause(); }
}
