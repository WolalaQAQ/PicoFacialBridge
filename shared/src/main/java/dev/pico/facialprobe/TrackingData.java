package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Verified PICO OS 5.13.7 fields, and the existing module's exact native-prefix wire protocol. */
public final class TrackingData {
    // Ocular blendshapes, including the blink/brow fallback read by the original PC eye parser.
    private static final int[] EYE_SHAPES = {0,2,3,4,11,12,16,26,28,30,31,35,36,38,41,44,45,46,47};
    // The receiver picks per-eye gaze solely from 0x100 (EYE_GAZE_VECTOR_VALID) and reads
    // pupil diameter without any validity check. Some firmware reports a fixed-depth split of
    // the fused gaze as its "per-eye" vectors rather than independent per-eye data; forwarding
    // that makes the receiver hold a constant offset between the two eyes. Strip the per-eye
    // gaze point/vector validity bits on the wire so the receiver keeps using the fused gaze
    // while still reading real pupil diameter. No-op when those bits are not set. The bits live
    // in the left/right EyePoseStatus at packet offsets 384/388, not the combined one at 392.
    private static final int STRIP_PER_EYE_GAZE_FLAGS = 0x80 | 0x100; // EYE_GAZE_POINT_VALID | EYE_GAZE_VECTOR_VALID
    private TrackingData() {}
    private static void stripPerEyeGazeFlags(byte[] packet) {
        ByteBuffer b = bytes(packet);
        b.putInt(384, b.getInt(384) & ~STRIP_PER_EYE_GAZE_FLAGS);
        b.putInt(388, b.getInt(388) & ~STRIP_PER_EYE_GAZE_FLAGS);
    }
    static ByteBuffer bytes(byte[] data) { return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN); }
    public static long eyeTimestamp(byte[] eye) { return bytes(eye).getLong(168); }
    public static long faceTimestamp(byte[] face) { return bytes(face).getLong(0); }
    public static float combinedGazeX(byte[] eye) { return bytes(eye).getFloat(72); }
    public static boolean eyeValid(byte[] eye) {
        if (eye == null || eye.length < 200) return false;
        ByteBuffer b = bytes(eye);
        return (b.getInt(8) & 2) != 0 && Float.isFinite(b.getFloat(72)) && Float.isFinite(b.getFloat(76)) && Float.isFinite(b.getFloat(80));
    }
    public static boolean faceValid(byte[] face) {
        return face != null && face.length >= 384 && bytes(face).getFloat(300) == 1f;
    }
    public static boolean fresh(long now, long timestamp) {
        return timestamp > 0 && now - timestamp >= -100000000L && now - timestamp < 2000000000L;
    }
    public static boolean forwardable(byte[] face, byte[] eye, long now) {
        // Invalid gaze during a blink must not suppress valid facial blendshapes or their validity flags.
        return face != null && eye != null && face.length >= 384 && eye.length >= 200
                && fresh(now, eyeTimestamp(eye)) && fresh(now, faceTimestamp(face));
    }
    static boolean eyeFresh(byte[] eye, long now) {
        return eye != null && eye.length >= 200 && fresh(now, eyeTimestamp(eye));
    }
    static boolean faceFresh(byte[] face, long now) {
        return face != null && face.length >= 384 && fresh(now, faceTimestamp(face));
    }
    static boolean forwardable(byte[] face, byte[] eye, long now, boolean sendEye, boolean sendFace) {
        return (sendEye && eyeFresh(eye, now)) || (sendFace && faceFresh(face, now));
    }
    static byte[] packet(byte[] face, byte[] eye, long now, boolean sendEye, boolean sendFace) {
        boolean hasEye = sendEye && eyeFresh(eye, now);
        boolean hasFace = sendFace && faceFresh(face, now);
        byte[] packet = new byte[536];
        if (hasFace) System.arraycopy(face, 0, packet, 0, 384);
        if (hasEye) {
            System.arraycopy(eye, 0, packet, 384, 152);
            stripPerEyeGazeFlags(packet);
        }
        if (!hasEye) {
            for (int index : EYE_SHAPES) bytes(packet).putFloat(8 + index * 4, 0);
            bytes(packet).putFloat(296, 0); // VIDEO_INPUT_EYE guards the PC eye parser.
            bytes(packet).putInt(380, 0); // Native face padding is the PC eye struct's timestamp field.
        } else if (!hasFace) {
            bytes(packet).putLong(0, eyeTimestamp(eye));
            if (faceFresh(face, now)) {
                for (int index : EYE_SHAPES) System.arraycopy(face, 8 + index * 4, packet, 8 + index * 4, 4);
                System.arraycopy(face, 296, packet, 296, 4);
            } else {
                // No facial buffer yet: use the real eye pose flags, not fabricated gaze samples.
                bytes(packet).putFloat(296, (bytes(eye).getInt(0) | bytes(eye).getInt(4) | bytes(eye).getInt(8)) != 0 ? 1f : 0f);
            }
        }
        return packet;
    }
    public static byte[] packet(byte[] face, byte[] eye) {
        if (face == null || eye == null || face.length < 384 || eye.length < 152) throw new IllegalArgumentException("Tracking prefixes too short");
        byte[] packet = new byte[536];
        System.arraycopy(face, 0, packet, 0, 384);
        System.arraycopy(eye, 0, packet, 384, 152);
        stripPerEyeGazeFlags(packet);
        return packet;
    }
}
