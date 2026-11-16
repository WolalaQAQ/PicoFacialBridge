package dev.pico.facialprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static dev.pico.facialprobe.BridgeUi.*;

/** Native quantities use their physical/signed scales, not the blendshape 0..1 scale. */
final class NativeEyeView extends LinearLayout {
    private final BridgeUi ui;
    private final Context localized;
    private final Readouts readouts;
    private final TextView flags;
    private NativeEyeStatus snapshot = NativeEyeStatus.empty();
    private long now;
    private int mask;
    private boolean running;
    private final boolean chinese;
    NativeEyeView(Context context) {
        super(context); setOrientation(VERTICAL); ui = new BridgeUi(context); localized = BridgePreferences.localized(context);
        chinese = localized.getResources().getConfiguration().getLocales().get(0).getLanguage().equals("zh");
        readouts = new Readouts(context); addView(readouts, new LayoutParams(-1, -2));
        readouts.setId(R.id.native_readouts);
        ui.gap(this, 12); flags = ui.text(this, "", 11, MUTED); flags.setTypeface(Typeface.MONOSPACE);
        flags.setTextIsSelectable(true); flags.setId(R.id.native_flags);
    }
    void update(NativeEyeStatus value, long time, int enabledMask, boolean serviceRunning, String query, boolean activeOnly) {
        snapshot = value; now = time; mask = enabledMask; running = serviceRunning;
        readouts.filter(query, activeOnly);
        if (snapshot.sourceNanos == 0 || !running || (mask & snapshot.captureMask & 1) == 0) {
            flags.setText("—"); return;
        }
        String raw = String.format(Locale.ROOT, "%08x / %08x / %08x", value.rawStatus(0), value.rawStatus(1), value.rawStatus(2));
        String wire = String.format(Locale.ROOT, "%08x / %08x / %08x", value.wireStatus(0), value.wireStatus(1), value.wireStatus(2));
        flags.setText(localized.getString(R.string.native_flags_value, raw, wire, value.sourceNanos));
    }
    int visibleCount() { return readouts.visible.size(); }
    private ExpressionStatus.Data data(int field) { return snapshot.data(field, now, mask, running); }
    private String state(ExpressionStatus.Data data) {
        int label;
        switch (data) {
            case STALE: label = R.string.ui_data_stale; break;
            case OFF: label = R.string.disabled; break;
            case STOPPED: label = R.string.status_stopped; break;
            case FILTERED: label = R.string.native_filtered; break;
            case NOT_CAPTURED: label = R.string.expressions_not_captured; break;
            case WAITING: label = R.string.ui_data_waiting; break;
            default: label = R.string.expressions_invalid;
        }
        return localized.getString(label);
    }
    private final class Readouts extends View {
        private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final List<NativeEyeStatus.Row> visible = new ArrayList<>();
        private final float scale = Math.max(1, getResources().getConfiguration().fontScale);
        Readouts(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }
        private float d(float value) { return ui.dp(value * scale); }
        void filter(String query, boolean activeOnly) {
            List<NativeEyeStatus.Row> next = new ArrayList<>();
            StringBuilder description = new StringBuilder();
            for (NativeEyeStatus.Row row : NativeEyeStatus.ROWS) {
                boolean active = data(row.left) == ExpressionStatus.Data.LIVE && Math.abs(row.value(snapshot, row.left)) > .01f
                        || row.right >= 0 && data(row.right) == ExpressionStatus.Data.LIVE && Math.abs(row.value(snapshot, row.right)) > .01f;
                if (!row.matches(query) || activeOnly && !active) continue;
                next.add(row);
                for (int field : new int[]{row.left, row.right}) if (field >= 0)
                    description.append(row.english).append(' ').append(NativeEyeStatus.NAMES[field]).append(": ")
                            .append(data(field) == ExpressionStatus.Data.LIVE ? Float.toString(row.value(snapshot, field)) : state(data(field))).append("; ");
            }
            if (!next.equals(visible)) { visible.clear(); visible.addAll(next); requestLayout(); }
            setContentDescription(description.toString()); invalidate();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), Math.round(d(78) * visible.size()));
        }
        private void text(Canvas canvas, String value, float x, float y, float width, int size, int color) {
            paint.setTextSize(d(size)); paint.setColor(color);
            canvas.drawText(TextUtils.ellipsize(value, paint, Math.max(0, width), TextUtils.TruncateAt.END).toString(), x, y, paint);
        }
        @Override protected void onDraw(Canvas canvas) {
            for (int i = 0; i < visible.size(); i++) {
                NativeEyeStatus.Row row = visible.get(i); float y = i * d(78), width = getWidth();
                paint.setTypeface(Typeface.DEFAULT);
                text(canvas, chinese ? row.chinese : row.english, 0, y + d(20), width, 15, TEXT);
                text(canvas, row.code(), 0, y + d(36), width, 10, MUTED);
                if (row.right < 0) meter(canvas, row, row.left, "", 0, y, width);
                else {
                    float cell = (width - ui.dp(24)) / 2;
                    meter(canvas, row, row.left, chinese ? "左" : "L", 0, y, cell);
                    meter(canvas, row, row.right, chinese ? "右" : "R", cell + ui.dp(24), y, cell);
                }
            }
        }
        private void meter(Canvas canvas, NativeEyeStatus.Row row, int field, String side, float x, float y, float width) {
            boolean live = data(field) == ExpressionStatus.Data.LIVE;
            float value = row.value(snapshot, field);
            int color = live && (value < row.minimum || value > row.maximum) ? AMBER : live ? ACCENT : MUTED;
            paint.setTypeface(Typeface.DEFAULT);
            String label = live ? side : side + (side.isEmpty() ? "" : " · ") + state(data(field));
            text(canvas, label, x, y + d(56), width - d(68), 11, MUTED);
            paint.setTypeface(Typeface.MONOSPACE); paint.setTextSize(d(14)); paint.setColor(color); paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(live ? String.format(Locale.ROOT, "%.3f", value) : "—", x + width, y + d(56), paint);
            paint.setTextAlign(Paint.Align.LEFT);
            float top = y + d(64), bottom = top + d(4), zero = x + width * (0 - row.minimum) / (row.maximum - row.minimum);
            paint.setColor(BORDER); canvas.drawRoundRect(x, top, x + width, bottom, d(2), d(2), paint);
            if (live) {
                float end = x + width * (Math.max(row.minimum, Math.min(row.maximum, value)) - row.minimum) / (row.maximum - row.minimum);
                paint.setColor(color); canvas.drawRoundRect(Math.min(zero, end), top, Math.max(zero, end), bottom, d(2), d(2), paint);
            }
            if (row.minimum < 0) { paint.setColor(MUTED); canvas.drawLine(zero, top - d(2), zero, bottom + d(2), paint); }
        }
    }
}
