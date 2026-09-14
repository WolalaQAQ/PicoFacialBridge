package dev.pico.facialprobe;

import android.app.Activity;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stop = new AtomicBoolean();
    private TextView output;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        Button start = new Button(this); start.setText("Run Binder Probe (60 seconds)");
        Button halt = new Button(this); halt.setText("Stop Probe");
        output = new TextView(this); output.setTextSize(16);
        ScrollView scroll = new ScrollView(this); scroll.addView(output);
        layout.addView(start); layout.addView(halt); layout.addView(scroll);
        setContentView(layout);
        start.setOnClickListener(v -> requestOrRun());
        halt.setOnClickListener(v -> stop.set(true));
        if (getIntent().getBooleanExtra("autorun", false)) requestOrRun();
    }

    private void requestOrRun() {
        try {
            String[] declared = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS).requestedPermissions;
            if (declared != null && declared.length > 0
                    && (checkSelfPermission("com.picovr.permission.EYE_TRACKING") != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission("com.picovr.permission.FACE_TRACKING") != PackageManager.PERMISSION_GRANTED)) {
                report("USER_PERMISSION_REQUEST eye + face");
                requestPermissions(new String[]{"com.picovr.permission.EYE_TRACKING", "com.picovr.permission.FACE_TRACKING"}, 10);
                return;
            }
        } catch (PackageManager.NameNotFoundException e) { report(e.toString()); return; }
        runProbe();
    }

    @Override public void onRequestPermissionsResult(int code, String[] names, int[] grants) {
        super.onRequestPermissionsResult(code, names, grants);
        for (int i = 0; i < names.length; i++) report("USER_PERMISSION_RESULT " + names[i] + "=" + grants[i]);
        if (code == 10) runProbe();
    }

    private void runProbe() {
        if (!running.compareAndSet(false, true)) return;
        stop.set(false); output.setText("");
        new Thread(() -> {
            try {
                new BinderProbe(this, this::report).run(stop,
                        Math.max(5, Math.min(300, getIntent().getIntExtra("seconds", 60))));
            } catch (Exception e) { report("UNEXPECTED " + Log.getStackTraceString(e)); }
            finally { running.set(false); }
        }, "PicoBinderProbe").start();
    }

    private void report(String line) {
        Log.i("PicoBinderProbe", line);
        runOnUiThread(() -> {
            if (output.length() > 24000) output.setText(output.getText().subSequence(output.length() - 12000, output.length()));
            output.append(line + "\n");
        });
    }

    @Override public void onDestroy() { stop.set(true); super.onDestroy(); }
}
