package dev.pico.facialprobe;

import java.util.ArrayDeque;

/** Successful, distinct source frames per trailing second; never counts held samples twice. */
final class TransmissionRates {
    private final ArrayDeque<Long> eyes = new ArrayDeque<>(), faces = new ArrayDeque<>();
    private long lastEye, lastFace;
    synchronized void sent(long now, long eyeTimestamp, long faceTimestamp) {
        trim(eyes, now); trim(faces, now);
        if (eyeTimestamp > lastEye) { eyes.addLast(now); lastEye = eyeTimestamp; }
        if (faceTimestamp > lastFace) { faces.addLast(now); lastFace = faceTimestamp; }
    }
    synchronized double[] hz(long now) {
        trim(eyes, now); trim(faces, now);
        return new double[]{eyes.size(), faces.size()};
    }
    synchronized void reset() { eyes.clear(); faces.clear(); lastEye = lastFace = 0; }
    private static void trim(ArrayDeque<Long> events, long now) {
        while (!events.isEmpty() && now - events.peekFirst() >= 1_000_000_000L) events.removeFirst();
    }
}
