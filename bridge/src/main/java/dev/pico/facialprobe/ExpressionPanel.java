package dev.pico.facialprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static dev.pico.facialprobe.BridgeUi.*;

/** A compact, display-only source inspector. */
final class ExpressionPanel extends LinearLayout {
    private static final int NATIVE_REGION = 3;
    private final BridgeUi ui;
    private final Context localized;
    private final TextView summary, empty;
    private final Button freeze;
    private final Button[] groups = new Button[4];
    private final EditText search;
    private final ScrollView scroll;
    private final Meters meters;
    private final NativeEyeView nativeView;
    private ExpressionStatus snapshot = ExpressionStatus.empty();
    private NativeEyeStatus nativeEye = NativeEyeStatus.empty();
    private OutputStatus outputs = OutputStatus.empty();
    private long now;
    private int mask, region;
    private boolean running, frozen, activeOnly;
    private String query = "";
    private final boolean chinese;

    ExpressionPanel(Context context) {
        super(context); ui = new BridgeUi(context); localized = BridgePreferences.localized(context);
        chinese = localized.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
        setOrientation(VERTICAL); setPadding(ui.dp(20), ui.dp(12), ui.dp(20), 0);
        LinearLayout toolbar = ui.row(); addView(toolbar);
        Button back = ui.button(toolbar, "‹", () -> ((Activity) context).finish());
        back.setId(R.id.expression_back); back.setContentDescription(s(R.string.expressions_back));
        back.setLayoutParams(new LayoutParams(ui.dp(48), ui.dp(48))); back.setTextSize(26);
        TextView title = ui.text(toolbar, s(R.string.expressions_title), 20, TEXT);
        ui.equally(title, 8); title.setSingleLine(true);
        freeze = ui.button(toolbar, s(R.string.expressions_freeze), () -> {
            frozen = !frozen;
            if (!frozen) update(BridgeState.expressions, BridgeState.nativeEye, BridgeState.outputs, android.os.SystemClock.elapsedRealtimeNanos(),
                    BridgePreferences.selection(BridgePreferences.get(context)), BridgeState.udp);
            else redraw();
        });
        freeze.setId(R.id.expression_freeze);
        freeze.setLayoutParams(new LayoutParams(-2, ui.dp(48)));
        Button more = ui.button(toolbar, "⋯", () -> {});
        more.setId(R.id.expression_menu); more.setContentDescription(s(R.string.expressions_tools));
        more.setLayoutParams(new LayoutParams(ui.dp(48), ui.dp(48)));
        more.setOnClickListener(view -> showTools(more));
        ui.gap(this, 12);
        summary = ui.text(this, "", 12, MUTED); summary.setId(R.id.expression_summary);
        ui.gap(this, 16);
        LinearLayout regions = ui.row(); regions.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        regions.setBackground(ui.shape(SURFACE, BORDER, 14)); addView(regions);
        int[] labels = {R.string.expressions_region_eyes, R.string.expressions_region_face, R.string.expressions_region_mouth, R.string.native_region};
        int[] ids = {R.id.expression_eyes, R.id.expression_face, R.id.expression_mouth, R.id.expression_native};
        for (int i = 0; i < groups.length; i++) {
            final int selected = i;
            groups[i] = ui.button(regions, s(labels[i]), () -> selectRegion(selected));
            groups[i].setTextSize(13); groups[i].setPadding(ui.dp(4), ui.dp(10), ui.dp(4), ui.dp(10));
            groups[i].setId(ids[i]); ui.equally(groups[i], 1);
        }
        ui.gap(this, 10);
        search = new EditText(context); search.setId(R.id.expression_search);
        search.setSingleLine(true); search.setTextSize(15); search.setTextColor(TEXT);
        search.setHintTextColor(MUTED); search.setHint(s(R.string.expressions_search));
        search.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        search.setMinHeight(ui.dp(48)); search.setBackground(ui.shape(SURFACE, BORDER, 10));
        addView(search, new LayoutParams(-1, -2)); search.setVisibility(GONE);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                query = value.toString().trim().toLowerCase(Locale.ROOT); redraw();
            }
            public void afterTextChanged(Editable value) {}
        });
        scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, ui.dp(16)); addView(scroll, new LayoutParams(-1, 0, 1));
        LinearLayout content = ui.column(); scroll.addView(content);
        nativeView = new NativeEyeView(context); content.addView(nativeView, new LayoutParams(-1, -2));
        meters = new Meters(context); content.addView(meters, new LayoutParams(-1, -2));
        empty = ui.text(content, s(R.string.expressions_empty), 14, MUTED);
        empty.setId(R.id.expression_empty); empty.setPadding(0, ui.dp(24), 0, 0); empty.setVisibility(GONE);
        selectRegion(0);
    }
    private String s(int id, Object... args) { return localized.getString(id, args); }
    private void selectRegion(int selected) {
        region = selected;
        for (int i = 0; i < groups.length; i++) {
            groups[i].setSelected(i == selected); ui.styleButton(groups[i], i == selected);
        }
        redraw(); scroll.scrollTo(0, 0);
    }
    private void showTools(Button anchor) {
        PopupMenu menu = new PopupMenu(getContext(), anchor);
        menu.getMenu().add(0, 1, 0, s(R.string.expressions_search_action));
        menu.getMenu().add(0, 2, 1, s(R.string.expressions_active)).setCheckable(true).setChecked(activeOnly);
        menu.getMenu().add(0, 4, 2, s(R.string.outputs_title));
        menu.getMenu().add(0, 3, 3, s(R.string.expressions_about));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                search.setVisibility(search.getVisibility() == VISIBLE ? GONE : VISIBLE);
                if (search.getVisibility() == GONE) search.setText("");
            } else if (item.getItemId() == 2) { activeOnly = !activeOnly; redraw(); }
            else if (item.getItemId() == 4) {
                TextView text = new TextView(getContext()); text.setTextColor(TEXT); text.setTextSize(12);
                text.setTypeface(Typeface.MONOSPACE); text.setTextIsSelectable(true); text.setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16));
                text.setText(String.join("\n\n", s(R.string.outputs_hint), TrackingMode.advertise(BridgeState.mode),
                        nativeEye.describe(), snapshot.describe(), outputs.describe()));
                ScrollView detail = new ScrollView(getContext()); detail.addView(text);
                new AlertDialog.Builder(getContext()).setTitle(s(R.string.outputs_title)).setView(detail)
                        .setPositiveButton(android.R.string.ok, null).show();
            }
            else new AlertDialog.Builder(getContext()).setTitle(s(R.string.expressions_about))
                    .setMessage(s(R.string.expressions_hint) + "\n\n" + s(R.string.expressions_scale) + "\n\n"
                            + s(R.string.expressions_brow_note) + "\n\n"
                            + s(R.string.native_hint) + "\n\n"
                            + s(R.string.expressions_clocks, age(0), age(1), age(2)))
                    .setPositiveButton(android.R.string.ok, null).show();
            return true;
        });
        menu.show();
    }
    void saveState(Bundle saved) {
        saved.putInt("region_id", groups[region].getId()); saved.putString("query", search.getText().toString());
        saved.putBoolean("active", activeOnly); saved.putBoolean("search", search.getVisibility() == VISIBLE);
    }
    void restoreState(Bundle saved) {
        activeOnly = saved.getBoolean("active");
        search.setVisibility(saved.getBoolean("search") ? VISIBLE : GONE);
        search.setText(saved.getString("query", ""));
        int selected = saved.getInt("region_id", R.id.expression_eyes);
        for (int i = 0; i < groups.length; i++) if (groups[i].getId() == selected) selectRegion(i);
    }
    void update(ExpressionStatus value, NativeEyeStatus eye, OutputStatus output, long time, int enabledMask, boolean serviceRunning) {
        if (frozen) return;
        snapshot = value; nativeEye = eye; outputs = output; now = time; mask = enabledMask; running = serviceRunning; redraw();
    }
    private ExpressionStatus.Data data(int shape) { return snapshot.data(shape, now, mask, running); }
    private int stateLabel(ExpressionStatus.Data data) {
        switch (data) {
            case LIVE: return R.string.live;
            case INVALID: return R.string.expressions_invalid;
            case STALE: return R.string.ui_data_stale;
            case OFF: return R.string.disabled;
            case STOPPED: return R.string.status_stopped;
            case UNSUPPORTED: return R.string.expressions_unsupported;
            case NOT_CAPTURED: return R.string.expressions_not_captured;
            case FILTERED: return R.string.native_filtered;
            default: return R.string.ui_data_waiting;
        }
    }
    private String age(int group) {
        long age = snapshot.ageNanos(group, now);
        return !running || (snapshot.captureMask & (group == ExpressionStatus.MOUTH ? 2 : 1)) == 0 || age < 0
                ? "—" : s(R.string.ui_value_ms, age / 1_000_000d);
    }
    private boolean active(int shape) { return data(shape) == ExpressionStatus.Data.LIVE && Math.abs(snapshot.value(shape)) > .01f; }
    private void redraw() {
        int live = 0, active = 0;
        if (region != NATIVE_REGION) for (ExpressionCatalog.Row row : ExpressionCatalog.GROUPS[region]) {
            if (data(row.left) == ExpressionStatus.Data.LIVE) live++;
            if (active(row.left)) active++;
            if (row.right >= 0) {
                if (data(row.right) == ExpressionStatus.Data.LIVE) live++;
                if (active(row.right)) active++;
            }
        }
        String reading = s(frozen ? R.string.expressions_frozen : R.string.expressions_summary, active, live);
        if (region == NATIVE_REGION) {
            live = 0;
            for (int field = 0; field < NativeEyeStatus.COUNT; field++)
                if (nativeEye.data(field, now, mask, running) == ExpressionStatus.Data.LIVE) live++;
            long age = nativeEye.ageNanos(now);
            reading = s(R.string.native_summary, s(frozen ? R.string.native_frozen : stateLabel(nativeEye.sampleData(now, mask, running))),
                    live, age < 0 ? "—" : s(R.string.ui_value_ms, age / 1_000_000d));
        }
        if (activeOnly) reading = s(R.string.expressions_active_filter, reading);
        summary.setText(s(R.string.expressions_status_line, frozen ? "Ⅱ" : "●", reading));
        summary.setTextColor(frozen ? AMBER : live > 0 ? ACCENT : MUTED);
        freeze.setText(s(frozen ? R.string.expressions_resume : R.string.expressions_freeze));
        freeze.setSelected(frozen); ui.styleButton(freeze, frozen);
        nativeView.setVisibility(region == NATIVE_REGION ? VISIBLE : GONE); meters.setVisibility(region == NATIVE_REGION ? GONE : VISIBLE);
        if (region == NATIVE_REGION) nativeView.update(nativeEye, now, mask, running, query, activeOnly); else meters.filter();
        empty.setVisibility((region == NATIVE_REGION ? nativeView.visibleCount() == 0 : meters.visible.isEmpty()) ? VISIBLE : GONE);
    }

    private final class Meters extends View {
        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final List<ExpressionCatalog.Row> visible = new ArrayList<>();
        private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
        private final float scale = Math.max(1, getResources().getConfiguration().fontScale);
        Meters(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
        private float d(float dp) { return ui.dp(dp * scale); }
        private void text(Canvas canvas, String value, float x, float y, float width, int size, int color, Typeface font) {
            paint.setTypeface(font); paint.setTextSize(d(size)); paint.setColor(color);
            canvas.drawText(TextUtils.ellipsize(value, paint, Math.max(0, width), TextUtils.TruncateAt.END).toString(), x, y, paint);
        }
        void filter() {
            List<ExpressionCatalog.Row> next = new ArrayList<>();
            StringBuilder description = new StringBuilder();
            for (ExpressionCatalog.Row row : ExpressionCatalog.GROUPS[region]) {
                if (!row.matches(query) || activeOnly && !active(row.left) && (row.right < 0 || !active(row.right))) continue;
                next.add(row);
                for (int shape : new int[]{row.left, row.right}) if (shape >= 0)
                    description.append(chinese ? ExpressionCatalog.CHINESE[shape] : ExpressionCatalog.NAMES[shape])
                            .append(": ").append(data(shape) == ExpressionStatus.Data.LIVE
                                    ? String.format(Locale.ROOT, "%.3f", snapshot.value(shape)) : s(stateLabel(data(shape)))).append("; ");
            }
            if (!next.equals(visible)) { visible.clear(); visible.addAll(next); requestLayout(); }
            setContentDescription(description.toString()); invalidate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(d(78) * visible.size()));
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            for (int i = 0; i < visible.size(); i++) {
                ExpressionCatalog.Row row = visible.get(i);
                float y = i * d(78);
                boolean lit = active(row.left) || row.right >= 0 && active(row.right);
                text(canvas, chinese ? row.chinese : row.english, 0, y + d(20), width, 15, lit ? TEXT : MUTED, medium);
                String codes = ExpressionCatalog.NAMES[row.left] + (row.right < 0 ? "" : " / " + ExpressionCatalog.NAMES[row.right]);
                text(canvas, codes, 0, y + d(36), width, 10, MUTED, regular);
                if (row.right < 0) meter(canvas, row.left, "", 0, y, width);
                else {
                    float cell = (width - ui.dp(24)) / 2;
                    meter(canvas, row.left, s(R.string.expressions_left), 0, y, cell);
                    meter(canvas, row.right, s(R.string.expressions_right), cell + ui.dp(24), y, cell);
                }
            }
        }
        private void meter(Canvas canvas, int shape, String side, float x, float y, float width) {
            ExpressionStatus.Data state = data(shape);
            boolean live = state == ExpressionStatus.Data.LIVE;
            float value = snapshot.value(shape);
            int ink = live && (value < 0 || value > 1) ? AMBER : live && value > .01f ? ACCENT : MUTED;
            String label = live ? side : side + (side.isEmpty() ? "" : " · ") + s(stateLabel(state));
            text(canvas, label, x, y + d(56), width - d(68), 11, MUTED, regular);
            paint.setTextAlign(Paint.Align.RIGHT); paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(d(14)); paint.setColor(ink);
            canvas.drawText(live ? String.format(Locale.ROOT, "%.3f", value) : "—", x + width, y + d(56), paint);
            paint.setTextAlign(Paint.Align.LEFT);
            float top = y + d(64), bottom = top + d(4);
            paint.setColor(BORDER); canvas.drawRoundRect(x, top, x + width, bottom, d(2), d(2), paint);
            if (live) {
                paint.setColor(ink); canvas.drawRoundRect(x, top, x + width * Math.max(0, Math.min(1, value)), bottom, d(2), d(2), paint);
            }
        }
    }
}
