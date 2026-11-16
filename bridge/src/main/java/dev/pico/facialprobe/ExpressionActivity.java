package dev.pico.facialprobe;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.Gravity;
import android.widget.FrameLayout;
import static dev.pico.facialprobe.BridgeUi.*;

/** A diagnostics child screen; opening or closing it never changes tracking. */
public final class ExpressionActivity extends Activity {
    private final Choreographer choreographer = Choreographer.getInstance();
    private ExpressionPanel panel;
    private final Choreographer.FrameCallback refresh = new Choreographer.FrameCallback() {
        public void doFrame(long frameTimeNanos) { render(); choreographer.postFrameCallback(this); }
    };
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        FrameLayout background = new FrameLayout(this); background.setBackgroundColor(BG);
        panel = new ExpressionPanel(this); panel.setId(R.id.expression_screen);
        int width = new BridgeUi(this).dp(Math.min(getResources().getConfiguration().screenWidthDp, 1000));
        background.addView(panel, new FrameLayout.LayoutParams(width, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        setContentView(background);
        if (saved != null) panel.restoreState(saved);
        render();
    }
    void render() {
        panel.update(BridgeState.expressions, BridgeState.nativeEye, BridgeState.outputs, SystemClock.elapsedRealtimeNanos(),
                BridgePreferences.selection(BridgePreferences.get(this)), BridgeState.udp);
    }
    @Override public void onSaveInstanceState(Bundle saved) { panel.saveState(saved); super.onSaveInstanceState(saved); }
    @Override public void onResume() { super.onResume(); choreographer.postFrameCallback(refresh); }
    @Override public void onPause() { choreographer.removeFrameCallback(refresh); super.onPause(); }
}
