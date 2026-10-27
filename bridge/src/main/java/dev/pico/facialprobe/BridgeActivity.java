package dev.pico.facialprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import static dev.pico.facialprobe.BridgeUi.*;

public final class BridgeActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final View[] pages = new View[3];
    private final Button[] tabs = new Button[3];
    private BridgeUi ui;
    private TextView status, serviceHint, statusDot, modeBadge, logs, eyeRate, faceRate, eyeHealth, faceHealth;
    private TextView avatarClient, avatarPackets, modeText, address, frames, overruns, diagnosticError;
    private TextView gazeState, gazeRate, gazePackets, gazeClient, gazeId, gazeEpoch, gazeLease;
    private TextView gazeData, gazeAge, gazeEyeState, gazeError;
    private Button logButton;
    private SharedPreferences prefs;
    private Context localized;
    private String pending = BridgeService.START;
    private int selectedTab;
    private boolean showingLogs;
    private final Runnable refresh = new Runnable() { public void run() { render(); main.postDelayed(this, 500); } };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = BridgePreferences.get(this); ui = new BridgeUi(this);
        if (saved != null) { selectedTab = saved.getInt("tab"); showingLogs = saved.getBoolean("logs"); }
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        buildUi();
        if (getIntent().getBooleanExtra("autostart", false)) permissionsThen(BridgeService.START);
    }
    private String s(int id, Object... args) { return localized.getString(id, args); }
    private String shown(String value) { return value.isEmpty() ? "—" : value; }

    private void buildUi() {
        localized = BridgePreferences.localized(this);
        Configuration configuration = getResources().getConfiguration();
        boolean shortWindow = configuration.screenHeightDp < 600;
        FrameLayout background = new FrameLayout(this); background.setBackgroundColor(BG);
        LinearLayout shell = ui.column(); shell.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(8));
        int width = ui.dp(Math.min(configuration.screenWidthDp, 1000));
        FrameLayout.LayoutParams shellParams = new FrameLayout.LayoutParams(width, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        if (shortWindow) {
            // A fixed service header would consume most of a short landscape window.
            ScrollView dashboard = new ScrollView(this); dashboard.setFillViewport(true);
            background.addView(dashboard, shellParams); dashboard.addView(shell);
        } else background.addView(shell, shellParams);
        buildHeader(shell);
        ui.gap(shell, 16); buildServiceCard(shell);
        LinearLayout navigation = ui.row(); navigation.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4));
        navigation.setBackground(ui.shape(SURFACE, BORDER, 16)); shell.addView(navigation);
        int[] labels = {R.string.tab_avatar, R.string.tab_gaze, R.string.tab_diagnostics};
        int[] ids = {R.id.tab_avatar, R.id.tab_gaze, R.id.tab_diagnostics};
        for (int i = 0; i < tabs.length; i++) {
            final int page = i;
            tabs[i] = ui.button(navigation, s(labels[i]), () -> selectTab(page));
            tabs[i].setId(ids[i]); ui.equally(tabs[i], 2);
        }
        ui.gap(shell, 12);
        FrameLayout body = new FrameLayout(this);
        shell.addView(body, shortWindow ? new LinearLayout.LayoutParams(-1, -2) : new LinearLayout.LayoutParams(-1, 0, 1));
        int[] pageIds = {R.id.page_avatar, R.id.page_gaze, R.id.page_diagnostics};
        for (int i = 0; i < pages.length; i++) {
            LinearLayout content = ui.column();
            if (shortWindow) {
                pages[i] = content;
                body.addView(content, new FrameLayout.LayoutParams(-1, -2));
            } else {
                ScrollView page = new ScrollView(this); pages[i] = page;
                page.setClipToPadding(false); page.setPadding(0, 0, 0, ui.dp(12));
                body.addView(page, new FrameLayout.LayoutParams(-1, -1)); page.addView(content);
            }
            pages[i].setId(pageIds[i]);
            if (i == 0) buildAvatar(content); else if (i == 1) buildGaze(content); else buildDiagnostics(content);
        }
        setContentView(background); selectTab(selectedTab); render();
    }
    private void buildHeader(LinearLayout parent) {
        boolean narrowWindow = getResources().getConfiguration().screenWidthDp < 480;
        LinearLayout header = ui.row(); parent.addView(header);
        TextView mark = ui.text(header, "PF", 19, ACCENT);
        mark.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); mark.setGravity(Gravity.CENTER);
        mark.setBackground(ui.shape(ACCENT_DIM, 0, 14));
        mark.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        LinearLayout brand = ui.column(); brand.setPadding(ui.dp(12), 0, ui.dp(8), 0);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = ui.text(brand, "PicoFacialBridge", 23, TEXT);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ui.gap(brand, 4); ui.text(brand, s(R.string.ui_subtitle), 11, MUTED);
        LinearLayout languages = ui.row();
        if (narrowWindow) {
            ui.gap(parent, 10); languages.setGravity(Gravity.END | Gravity.CENTER_VERTICAL); parent.addView(languages);
        } else header.addView(languages);
        String language = BridgePreferences.language(this);
        Button zh = ui.button(languages, "中文", () -> setLanguage("zh")); zh.setId(R.id.language_zh);
        Button en = ui.button(languages, "EN", () -> setLanguage("en")); en.setId(R.id.language_en);
        zh.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(60), -2));
        LinearLayout.LayoutParams englishParams = new LinearLayout.LayoutParams(ui.dp(52), -2);
        englishParams.leftMargin = ui.dp(6); en.setLayoutParams(englishParams);
        ui.styleButton(zh, language.equals("zh")); ui.styleButton(en, language.equals("en"));
        zh.setEnabled(!language.equals("zh")); en.setEnabled(!language.equals("en"));
        zh.setContentDescription(s(R.string.language) + ": 中文");
        en.setContentDescription(s(R.string.language) + ": English");
    }
    private void buildServiceCard(LinearLayout parent) {
        LinearLayout card = ui.card(parent);
        LinearLayout heading = ui.row(); card.addView(heading);
        TextView caption = ui.text(heading, s(R.string.ui_service), 12, MUTED); ui.equally(caption, 0);
        modeBadge = ui.text(heading, "", 12, ACCENT);
        modeBadge.setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5));
        modeBadge.setBackground(ui.shape(ACCENT_DIM, 0, 8));
        modeBadge.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        ui.gap(card, 8);
        LinearLayout state = ui.row(); card.addView(state);
        statusDot = ui.text(state, "●", 16, MUTED);
        statusDot.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(24), -2));
        status = ui.text(state, "", 27, TEXT); status.setId(R.id.status);
        ui.equally(status, 0);
        status.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        ui.gap(card, 6); serviceHint = ui.text(card, "", 14, MUTED);
        ui.gap(card, 16);
        LinearLayout actions = ui.row(); card.addView(actions);
        Button start = ui.button(actions, s(R.string.start), () -> permissionsThen(BridgeService.START));
        start.setId(R.id.start_button); ui.equally(start, 3); ui.styleButton(start, true);
        Button stop = ui.button(actions, s(R.string.stop), () -> send(BridgeService.STOP));
        stop.setId(R.id.stop_button); ui.equally(stop, 3);
        Button restart = ui.button(actions, s(R.string.restart), () -> permissionsThen(BridgeService.RESTART));
        restart.setId(R.id.restart_button); ui.equally(restart, 3);
    }
    private void intro(LinearLayout parent, int title, int hint) {
        ui.title(parent, s(title)); ui.gap(parent, 6);
        ui.text(parent, s(hint), 14, MUTED); ui.gap(parent, 18);
    }
    private void buildAvatar(LinearLayout parent) {
        intro(parent, R.string.ui_avatar_title, R.string.ui_avatar_hint);
        boolean columns = getResources().getConfiguration().screenWidthDp >= 720;
        LinearLayout channels = columns ? ui.row() : ui.column(); parent.addView(channels);
        LinearLayout eye = trackingCard(channels, true), face = trackingCard(channels, false);
        if (columns) { ui.equally(eye, 4); ui.equally(face, 4); ui.gap(parent, 12); }
        LinearLayout receiver = ui.card(parent); ui.title(receiver, s(R.string.ui_receiver));
        avatarClient = ui.field(receiver, s(R.string.ui_endpoint), R.id.avatar_client, true);
        avatarPackets = ui.field(receiver, s(R.string.ui_packets), R.id.avatar_packets, false);
        ui.text(parent, s(R.string.rate_hint), 13, MUTED); ui.gap(parent, 10);
        ui.text(parent, s(R.string.selection_hint), 13, MUTED);
    }
    private LinearLayout trackingCard(LinearLayout parent, boolean eye) {
        LinearLayout card = ui.card(parent);
        ui.text(card, s(eye ? R.string.ui_eye_title : R.string.ui_face_title), 12, eye ? ACCENT : BLUE);
        ui.gap(card, 12);
        TextView rate = ui.text(card, "", 38, TEXT); rate.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        rate.setId(eye ? R.id.eye_rate : R.id.face_rate);
        ui.gap(card, 6); TextView health = ui.text(card, "", 14, MUTED);
        health.setId(eye ? R.id.eye_health : R.id.face_health);
        ui.gap(card, 16); addSwitch(card, eye ? R.id.switch_eye : R.id.switch_face,
                eye ? R.string.transmit_eye : R.string.transmit_face, eye ? BridgePreferences.EYE : BridgePreferences.FACE);
        ui.gap(card, 8); ui.text(card, s(eye ? R.string.ui_eye_hint : R.string.ui_face_hint), 13, MUTED);
        if (eye) { eyeRate = rate; eyeHealth = health; } else { faceRate = rate; faceHealth = health; }
        return card;
    }
    private void buildGaze(LinearLayout parent) {
        intro(parent, R.string.ui_gaze_title, R.string.ui_gaze_hint);
        LinearLayout delivery = ui.card(parent);
        gazeState = ui.text(delivery, "", 23, ACCENT); gazeState.setId(R.id.gaze_status);
        ui.gap(delivery, 18); ui.text(delivery, s(R.string.ui_gaze_rate), 12, MUTED);
        ui.gap(delivery, 8); gazeRate = ui.text(delivery, "", 38, TEXT); gazeRate.setId(R.id.gaze_rate);
        gazeRate.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        gazePackets = ui.field(delivery, s(R.string.ui_gaze_packets), R.id.gaze_packets, false);
        LinearLayout subscription = ui.card(parent); ui.title(subscription, s(R.string.ui_subscription));
        gazeClient = ui.field(subscription, s(R.string.ui_endpoint), R.id.gaze_client, true);
        gazeId = ui.field(subscription, s(R.string.ui_subscription_id), R.id.gaze_id, true);
        gazeEpoch = ui.field(subscription, s(R.string.ui_epoch), R.id.gaze_epoch, true);
        gazeLease = ui.field(subscription, s(R.string.ui_lease), R.id.gaze_lease, false);
        LinearLayout sample = ui.card(parent); ui.title(sample, s(R.string.ui_sample));
        gazeData = ui.field(sample, s(R.string.ui_sample_state), R.id.gaze_data, false);
        gazeAge = ui.field(sample, s(R.string.ui_sample_age), R.id.gaze_age, false);
        gazeEyeState = ui.field(sample, s(R.string.ui_eye_gate), R.id.gaze_eye_state, false);
        ui.gap(sample, 16); ui.text(sample, s(R.string.ui_gaze_switch_hint), 13, MUTED);
        ui.gap(sample, 12);
        ui.button(sample, s(R.string.ui_eye_settings), () -> selectTab(0)).setId(R.id.gaze_eye_settings);
        LinearLayout error = ui.card(parent); ui.title(error, s(R.string.ui_last_error));
        ui.gap(error, 10); gazeError = ui.text(error, "", 14, MUTED); gazeError.setId(R.id.gaze_error);
        gazeError.setTextIsSelectable(true);
        ui.text(parent, s(R.string.ui_gaze_note), 13, MUTED);
    }
    private void buildDiagnostics(LinearLayout parent) {
        LinearLayout runtime = ui.card(parent); ui.title(runtime, s(R.string.ui_runtime));
        ui.gap(runtime, 12); modeText = ui.text(runtime, "", 15, TEXT); modeText.setId(R.id.mode);
        address = ui.field(runtime, s(R.string.ui_address), R.id.diagnostic_address, true);
        frames = ui.field(runtime, s(R.string.ui_source_frames), R.id.diagnostic_frames, false);
        overruns = ui.field(runtime, s(R.string.ui_overruns), R.id.diagnostic_overruns, false);
        LinearLayout error = ui.card(parent); ui.title(error, s(R.string.ui_last_error));
        ui.gap(error, 10); diagnosticError = ui.text(error, "", 14, MUTED);
        diagnosticError.setId(R.id.diagnostic_error); diagnosticError.setTextIsSelectable(true);
        LinearLayout logCard = ui.card(parent); ui.title(logCard, s(R.string.ui_logs));
        ui.gap(logCard, 8); ui.text(logCard, s(R.string.logs_hint), 13, MUTED); ui.gap(logCard, 14);
        LinearLayout buttons = ui.row(); logCard.addView(buttons);
        logButton = ui.button(buttons, "", () -> { showingLogs = !showingLogs; render(); });
        logButton.setId(R.id.show_logs); ui.equally(logButton, 3);
        Button copy = ui.button(buttons, s(R.string.ui_copy_logs), this::copyLogs);
        copy.setId(R.id.copy_logs); ui.equally(copy, 3);
        ui.gap(logCard, 12); logs = ui.text(logCard, "", 12, MUTED); logs.setId(R.id.diagnostic_logs);
        logs.setTypeface(Typeface.MONOSPACE); logs.setTextIsSelectable(true);
        ui.text(parent, s(R.string.usage_hint), 13, MUTED);
    }
    private void selectTab(int index) {
        selectedTab = index;
        for (int i = 0; i < pages.length; i++) {
            pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(i == index); ui.styleButton(tabs[i], i == index);
        }
        render();
    }
    private void setLanguage(String language) {
        prefs.edit().putString(BridgePreferences.LANGUAGE, language).apply(); buildUi();
    }
    private void addSwitch(LinearLayout parent, int id, int label, String key) {
        Switch toggle = new Switch(this); toggle.setId(id); toggle.setText(s(label));
        toggle.setTextSize(17); toggle.setTextColor(TEXT); toggle.setMinHeight(ui.dp(48));
        ColorStateList tint = new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{ACCENT, MUTED});
        toggle.setThumbTintList(tint);
        toggle.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0xff326b60, BORDER}));
        toggle.setChecked(prefs.getBoolean(key, true)); parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        toggle.setOnCheckedChangeListener((view, checked) -> { prefs.edit().putBoolean(key, checked).apply(); render(); });
    }
    private void copyLogs() {
        String value = BridgeState.logs();
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("PicoFacialBridge", value));
        Toast.makeText(this, s(R.string.ui_logs_copied), Toast.LENGTH_SHORT).show();
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
        long nowNanos = SystemClock.elapsedRealtimeNanos(), nowMs = SystemClock.elapsedRealtime();
        int mask = BridgePreferences.selection(prefs);
        boolean sendEye = (mask & 1) != 0, sendFace = (mask & 2) != 0;
        double[] hz = BridgeState.rates.hz(nowNanos);
        eyeRate.setText(s(R.string.ui_value_hz, sendEye && BridgeState.udp ? hz[0] : 0d));
        faceRate.setText(s(R.string.ui_value_hz, sendFace && BridgeState.udp ? hz[1] : 0d));
        eyeHealth.setText(s(!sendEye ? R.string.disabled : BridgeState.eye ? R.string.live : R.string.no_data));
        faceHealth.setText(s(!sendFace ? R.string.disabled : BridgeState.face ? R.string.live : R.string.no_data));
        eyeHealth.setTextColor(sendEye && BridgeState.eye ? ACCENT : MUTED);
        faceHealth.setTextColor(sendFace && BridgeState.face ? BLUE : MUTED);
        status.setText(s(BridgeState.status)); serviceHint.setText(s(BridgeState.detail));
        statusDot.setTextColor(BridgeState.status == R.string.status_running ? ACCENT :
                BridgeState.status == R.string.status_error ? RED : BridgeState.status == R.string.status_starting ? AMBER : MUTED);
        avatarClient.setText(BridgeState.client.isEmpty() ? s(BridgeState.clientMessage) : BridgeState.client);
        avatarPackets.setText(s(R.string.ui_value_count, BridgeState.packets));
        TrackingMode.Info mode = BridgeState.mode;
        modeBadge.setText(s(mode.enhance ? R.string.ui_mode_enhanced : R.string.ui_mode_normal));
        modeText.setText(s(R.string.mode_line, mode.enhance ? s(R.string.mode_enhance, mode.plugin) : s(R.string.mode_normal)));
        address.setText(BridgeState.udp ? BridgeState.address : s(R.string.status_stopped));
        frames.setText(s(R.string.ui_value_frames, BridgeState.eyeFrames, BridgeState.faceFrames));
        overruns.setText(s(R.string.ui_value_count, BridgeState.ringOverruns));
        diagnosticError.setText(BridgeState.error.isEmpty() ? s(R.string.ui_no_error) : BridgeState.error);
        diagnosticError.setTextColor(BridgeState.error.isEmpty() ? MUTED : RED);
        renderGaze(sendEye, nowMs, nowNanos);
        logButton.setText(s(showingLogs ? R.string.hide_logs : R.string.show_logs));
        logs.setVisibility(showingLogs ? View.VISIBLE : View.GONE);
        if (showingLogs && selectedTab == 2) {
            String value = BridgeState.logs(); if (value.isEmpty()) value = s(R.string.ui_logs_empty);
            if (!value.contentEquals(logs.getText())) logs.setText(value);
        }
    }
    private void renderGaze(boolean eyeEnabled, long nowMs, long nowNanos) {
        GazeStatus snapshot = BridgeState.gaze;
        GazeStatus.Link link = snapshot.connection(nowMs);
        int linkLabel;
        switch (link) {
            case SUBSCRIBED: linkLabel = R.string.ui_link_subscribed; break;
            case UNSUBSCRIBED: linkLabel = R.string.ui_link_unsubscribed; break;
            case EXPIRED: linkLabel = R.string.ui_link_expired; break;
            case FAILED: linkLabel = R.string.ui_link_failed; break;
            case STOPPED: linkLabel = R.string.status_stopped; break;
            default: linkLabel = R.string.ui_link_waiting;
        }
        gazeState.setText(s(linkLabel));
        gazeState.setTextColor(link == GazeStatus.Link.SUBSCRIBED ? ACCENT : link == GazeStatus.Link.FAILED ? RED : MUTED);
        gazeRate.setText(s(R.string.ui_value_hz, eyeEnabled && BridgeState.udp ? BridgeState.gazeRates.hz(nowNanos)[0] : 0d));
        gazePackets.setText(s(R.string.ui_value_count, BridgeState.gazePackets));
        gazeClient.setText(shown(snapshot.endpoint)); gazeId.setText(shown(snapshot.id));
        gazeEpoch.setText(snapshot.epoch == 0 ? "—" : String.format(Locale.ROOT, "%016x", snapshot.epoch));
        gazeLease.setText(link == GazeStatus.Link.SUBSCRIBED ? s(R.string.ui_value_seconds, snapshot.leaseRemainingMs(nowMs) / 1000d) : "—");
        GazeStatus.Data data = snapshot.data(eyeEnabled, nowMs, nowNanos);
        int dataLabel = data == GazeStatus.Data.OFF ? R.string.disabled : data == GazeStatus.Data.FRESH ? R.string.ui_data_fresh :
                data == GazeStatus.Data.INVALID ? R.string.ui_data_invalid : data == GazeStatus.Data.STALE ? R.string.ui_data_stale : R.string.ui_data_waiting;
        gazeData.setText(s(dataLabel)); gazeData.setTextColor(data == GazeStatus.Data.FRESH ? ACCENT :
                data == GazeStatus.Data.INVALID || data == GazeStatus.Data.STALE ? AMBER : MUTED);
        long age = snapshot.sourceAgeNanos(nowNanos);
        gazeAge.setText(age < 0 ? "—" : s(R.string.ui_value_ms, age / 1_000_000d));
        gazeEyeState.setText(s(eyeEnabled ? R.string.ui_enabled : R.string.ui_disabled));
        gazeEyeState.setTextColor(eyeEnabled ? ACCENT : MUTED);
        gazeError.setText(snapshot.error.isEmpty() ? s(R.string.ui_no_error) : snapshot.error);
        gazeError.setTextColor(snapshot.error.isEmpty() ? MUTED : RED);
    }
    @Override public void onSaveInstanceState(Bundle saved) {
        saved.putInt("tab", selectedTab); saved.putBoolean("logs", showingLogs); super.onSaveInstanceState(saved);
    }
    @Override public void onResume() { super.onResume(); main.post(refresh); }
    @Override public void onPause() { main.removeCallbacks(refresh); super.onPause(); }
}
