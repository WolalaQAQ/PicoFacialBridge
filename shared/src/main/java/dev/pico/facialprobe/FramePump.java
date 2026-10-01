package dev.pico.facialprobe;

import java.util.List;

/**
 * Emits one datagram per source update, in timestamp order. Eye and facial updates are separate
 * datagrams, so a fast eye update is never delayed by, and never re-sends, a slow facial sample.
 */
final class FramePump {
    interface Sink { void send(byte[] packet) throws Exception; }
    private byte[] eye, face;
    /** Enhance mode with the dual plugin asks to keep the real per-eye validity bits on the wire. */
    boolean keepPerEyeGaze;
    byte[] eye() { return eye; }
    byte[] face() { return face; }
    void accept(List<byte[]> eyes, List<byte[]> faces, long now, Sink sink) throws Exception {
        accept(eyes, faces, now, true, true, sink);
    }
    void accept(List<byte[]> eyes, List<byte[]> faces, long now, boolean sendEye, boolean sendFace, Sink sink) throws Exception {
        int e = 0, f = 0;
        while (e < eyes.size() || f < faces.size()) {
            long eyeTime = e < eyes.size() ? TrackingData.eyeTimestamp(eyes.get(e)) : Long.MAX_VALUE;
            long faceTime = f < faces.size() ? TrackingData.faceTimestamp(faces.get(f)) : Long.MAX_VALUE;
            boolean eyeChanged = eyeTime <= faceTime, faceChanged = faceTime <= eyeTime;
            if (eyeChanged) eye = eyes.get(e++);
            if (faceChanged) face = faces.get(f++);
            if (eyeChanged && sendEye && TrackingData.eyeFresh(eye, now)) sink.send(TrackingData.eyePacket(eye, keepPerEyeGaze));
            if (faceChanged && (sendEye || sendFace) && TrackingData.faceFresh(face, now))
                sink.send(TrackingData.facePacket(face, sendEye, sendFace));
        }
    }
}
