package dev.pico.facialprobe;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared visual tokens and the small set of building blocks used by the dashboard. */
final class BridgeUi {
    static final int BG = Color.rgb(11, 17, 25), SURFACE = Color.rgb(20, 30, 42);
    static final int BORDER = Color.rgb(40, 56, 73), TEXT = Color.rgb(236, 243, 250);
    static final int MUTED = Color.rgb(151, 170, 189), ACCENT = Color.rgb(115, 229, 197);
    static final int ACCENT_DIM = Color.rgb(28, 60, 56), BLUE = Color.rgb(132, 183, 255);
    static final int AMBER = Color.rgb(245, 199, 115), RED = Color.rgb(255, 146, 146);
    private final Context context;

    BridgeUi(Context context) { this.context = context; }
    int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    GradientDrawable shape(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }
    LinearLayout column() {
        LinearLayout view = new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    LinearLayout row() {
        LinearLayout view = new LinearLayout(context); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    TextView text(LinearLayout parent, String value, int size, int color) {
        TextView view = new TextView(context); view.setText(value); view.setTextSize(size);
        view.setTextColor(color); view.setFontFeatureSettings("tnum");
        view.setLineSpacing(dp(3), 1); view.setIncludeFontPadding(false);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    TextView title(LinearLayout parent, String value) {
        TextView view = text(parent, value, 18, TEXT); view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); return view;
    }
    LinearLayout card(LinearLayout parent) {
        LinearLayout view = column(); view.setPadding(dp(20), dp(20), dp(20), dp(20));
        view.setBackground(shape(SURFACE, BORDER, 18));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12); parent.addView(view, params); return view;
    }
    Button button(LinearLayout parent, String text, Runnable action) {
        Button view = new Button(context); view.setText(text); view.setAllCaps(false);
        view.setTextSize(15); view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setMinWidth(0); view.setMinimumWidth(0); view.setMinHeight(dp(48)); view.setMinimumHeight(dp(48));
        view.setPadding(dp(14), dp(10), dp(14), dp(10)); view.setElevation(0); view.setStateListAnimator(null);
        view.setBackgroundTintList(null);
        view.setOnClickListener(v -> action.run()); styleButton(view, false);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    void styleButton(Button view, boolean selected) {
        view.setTextColor(selected ? BG : TEXT);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x24ffffff),
                shape(selected ? ACCENT : SURFACE, selected ? 0 : BORDER, 12), null));
    }
    void gap(LinearLayout parent, int height) { parent.addView(new View(context), new LinearLayout.LayoutParams(1, dp(height))); }
    void equally(View view, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(margin), 0, dp(margin), 0); view.setLayoutParams(params);
    }
    TextView field(LinearLayout parent, String label, int id, boolean monospace) {
        LinearLayout line = row();
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, -2);
        lineParams.topMargin = dp(14); parent.addView(line, lineParams);
        TextView caption = text(line, label, 14, MUTED); equally(caption, 0);
        TextView value = text(line, "—", 15, TEXT); value.setId(id); equally(value, 0);
        value.setGravity(Gravity.END); value.setTextIsSelectable(true);
        if (monospace) value.setTypeface(Typeface.MONOSPACE);
        return value;
    }
}
