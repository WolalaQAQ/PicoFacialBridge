"""Compile the real inspector Activity against deterministic Android API doubles."""
import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev/pico/facialprobe/"

SOURCES = {
    "android/os/Bundle.java": "package android.os; public class Bundle {}",
    "android/os/SystemClock.java": """
        package android.os;
        public final class SystemClock {
            public static long now = 10_000_000_000L;
            public static long elapsedRealtimeNanos() { return now; }
        }
    """,
    "android/view/Gravity.java": """
        package android.view;
        public final class Gravity { public static final int TOP = 1, CENTER_HORIZONTAL = 2; }
    """,
    "android/view/Choreographer.java": """
        package android.view;
        import java.util.ArrayList;
        import java.util.List;
        public final class Choreographer {
            public interface FrameCallback { void doFrame(long frameTimeNanos); }
            private static final Choreographer INSTANCE = new Choreographer();
            private final List<FrameCallback> pending = new ArrayList<>();
            public static Choreographer getInstance() { return INSTANCE; }
            public void postFrameCallback(FrameCallback callback) { pending.add(callback); }
            public void removeFrameCallback(FrameCallback callback) { pending.removeIf(item -> item == callback); }
            public int pendingCount() { return pending.size(); }
            public void step(long period) {
                android.os.SystemClock.now += period;
                List<FrameCallback> current = new ArrayList<>(pending); pending.clear();
                for (FrameCallback callback : current) callback.doFrame(android.os.SystemClock.now - 1_000_000_000L);
            }
        }
    """,
    "android/app/Activity.java": """
        package android.app;
        public class Activity {
            public static class Window {
                public void setStatusBarColor(int color) {}
                public void setNavigationBarColor(int color) {}
            }
            public static class Configuration { public int screenWidthDp = 1024; }
            public static class Resources {
                public Configuration getConfiguration() { return new Configuration(); }
            }
            public Window getWindow() { return new Window(); }
            public Resources getResources() { return new Resources(); }
            public void setContentView(Object view) {}
            public void onCreate(android.os.Bundle saved) {}
            public void onSaveInstanceState(android.os.Bundle saved) {}
            public void onResume() {}
            public void onPause() {}
        }
    """,
    "android/widget/FrameLayout.java": """
        package android.widget;
        public class FrameLayout {
            public FrameLayout(android.app.Activity activity) {}
            public static class LayoutParams { public LayoutParams(int width, int height, int gravity) {} }
            public void setBackgroundColor(int color) {}
            public void addView(Object view, LayoutParams params) {}
        }
    """,
    PACKAGE + "BridgeUi.java": """
        package dev.pico.facialprobe;
        final class BridgeUi {
            static final int BG = 0;
            BridgeUi(android.app.Activity activity) {}
            int dp(int value) { return value; }
        }
    """,
    PACKAGE + "R.java": """
        package dev.pico.facialprobe;
        final class R { static final class id { static final int expression_screen = 1; } }
    """,
    PACKAGE + "BridgeState.java": """
        package dev.pico.facialprobe;
        final class BridgeState {
            static Object expressions = new Object(), nativeEye = new Object(), outputs = new Object();
            static boolean udp;
        }
    """,
    PACKAGE + "BridgePreferences.java": """
        package dev.pico.facialprobe;
        final class BridgePreferences {
            static int mask;
            static Object get(android.app.Activity activity) { return null; }
            static int selection(Object prefs) { return mask; }
        }
    """,
    PACKAGE + "ExpressionPanel.java": """
        package dev.pico.facialprobe;
        final class ExpressionPanel {
            static ExpressionPanel latest;
            int updates, saves, restores, mask;
            long now;
            Object expressions, nativeEye, outputs;
            boolean running;
            ExpressionPanel(android.app.Activity activity) { latest = this; }
            void setId(int id) {}
            void saveState(android.os.Bundle saved) { saves++; }
            void restoreState(android.os.Bundle saved) { restores++; }
            void update(Object values, Object eye, Object sent, long time, int selection, boolean active) {
                updates++; expressions = values; nativeEye = eye; outputs = sent;
                now = time; mask = selection; running = active;
            }
        }
    """,
    PACKAGE + "InspectorFramesTest.java": """
        package dev.pico.facialprobe;
        import android.os.Bundle;
        import android.os.SystemClock;
        import android.view.Choreographer;
        public final class InspectorFramesTest {
            private static int checks;
            private static void check(boolean value, String message) {
                checks++; if (!value) throw new AssertionError(message);
            }
            public static void main(String[] args) {
                Choreographer frames = Choreographer.getInstance();
                ExpressionActivity activity = new ExpressionActivity(); activity.onCreate(null);
                ExpressionPanel panel = ExpressionPanel.latest;
                check(panel.updates == 1 && frames.pendingCount() == 0, "Creation renders once without starting a timer");
                activity.onResume();
                check(frames.pendingCount() == 1, "Resume schedules one display callback");
                for (int hz : new int[]{60, 72, 90, 120}) {
                    int before = panel.updates;
                    for (int i = 0; i < hz; i++) {
                        BridgeState.expressions = new Object(); BridgeState.nativeEye = new Object();
                        BridgeState.outputs = new Object(); BridgeState.udp = (i & 1) == 0;
                        BridgePreferences.mask = i % 4;
                        frames.step(1_000_000_000L / hz);
                        check(panel.updates == before + i + 1 && frames.pendingCount() == 1,
                                "Every display frame renders exactly once; no fixed 10Hz gate or duplicate callbacks");
                        check(panel.expressions == BridgeState.expressions && panel.nativeEye == BridgeState.nativeEye
                                && panel.outputs == BridgeState.outputs, "Each frame reads the newest source references directly");
                        check(panel.now == SystemClock.now && panel.mask == BridgePreferences.mask
                                && panel.running == BridgeState.udp, "Freshness and local gates use the current elapsed clock");
                    }
                }
                Object held = panel.expressions; int before = panel.updates;
                frames.step(11_111_111L);
                check(panel.expressions == held && panel.updates == before + 1, "No new observation holds the real value, without interpolation");
                check(panel.now == SystemClock.now, "Source age keeps advancing even when values hold");
                activity.onPause(); before = panel.updates;
                check(frames.pendingCount() == 0, "Pause removes the display callback");
                frames.step(1_000_000_000L);
                check(panel.updates == before, "Paused Activity does not render");
                for (int i = 0; i < 10; i++) {
                    activity.onResume(); frames.step(11_111_111L); activity.onPause();
                    check(panel.updates == before + i + 1 && frames.pendingCount() == 0,
                            "Repeated entry/exit never multiplies or leaks callbacks");
                }
                Bundle saved = new Bundle(); activity.onSaveInstanceState(saved);
                check(panel.saves == 1, "Inspector state is still saved");
                ExpressionActivity restored = new ExpressionActivity(); restored.onCreate(saved);
                check(ExpressionPanel.latest.restores == 1 && frames.pendingCount() == 0, "Recreation restores state without a timer");
                restored.onResume(); frames.step(11_111_111L); restored.onPause();
                check(ExpressionPanel.latest.updates == 2 && frames.pendingCount() == 0, "Recreated screen follows frames and stops cleanly");
                System.out.println("PASS: " + checks + " real-Activity frame/lifecycle checks with Android API doubles");
            }
        }
    """,
}


class InspectorFrameTest(unittest.TestCase):
    def test_real_activity_follows_frames_and_removes_callbacks(self):
        jdk = Path(os.environ.get("JAVA_HOME") or (ROOT / "tools/jdk-path.txt").read_text().strip())
        suffix = ".exe" if os.name == "nt" else ""
        build = ROOT / "build/inspector-frame-tests"
        build.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=build) as temporary:
            root = Path(temporary)
            sources = []
            for name, content in SOURCES.items():
                source = root / name
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text(textwrap.dedent(content), encoding="utf-8")
                sources.append(str(source))
            sources.append(str(ROOT / "bridge/src/main/java" / PACKAGE / "ExpressionActivity.java"))
            classes = root / "classes"
            compiled = subprocess.run([str(jdk / "bin" / ("javac" + suffix)), "-encoding", "UTF-8",
                                       "-Xlint:all", "-Werror", "-d", str(classes), *sources],
                                      capture_output=True, text=True, encoding="utf-8", timeout=60)
            self.assertEqual(compiled.returncode, 0, compiled.stdout + compiled.stderr)
            tested = subprocess.run([str(jdk / "bin" / ("java" + suffix)), "-cp", str(classes),
                                     "dev.pico.facialprobe.InspectorFramesTest"],
                                    capture_output=True, text=True, encoding="utf-8", timeout=30)
            self.assertEqual(tested.returncode, 0, tested.stdout + tested.stderr)
            self.assertIn("PASS:", tested.stdout)
            print(tested.stdout.strip())


if __name__ == "__main__":
    unittest.main()
