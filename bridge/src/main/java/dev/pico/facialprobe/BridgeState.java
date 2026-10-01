package dev.pico.facialprobe;

import android.util.Log;
import java.util.ArrayDeque;

final class BridgeState {
    static volatile int status = R.string.status_stopped, detail = R.string.detail_initial, clientMessage = R.string.client_none;
    static volatile String error = "", client = "", address = "-";
    static volatile boolean eye, face, udp;
    static volatile TrackingMode.Info mode = TrackingMode.normal();
    static volatile long packets, eyeFrames, faceFrames;
    static volatile long ringOverruns;
    static final TransmissionRates rates = new TransmissionRates();
    private static final ArrayDeque<String> logs = new ArrayDeque<>();
    static synchronized void log(String message) {
        Log.i("PicoFacialBridge", message);
        if (logs.size() >= 160) logs.removeFirst();
        logs.addLast(message);
    }
    static synchronized String logs() { return String.join("\n", logs); }
}
