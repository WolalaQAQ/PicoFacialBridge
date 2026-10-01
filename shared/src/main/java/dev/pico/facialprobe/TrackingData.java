package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verified PICO OS 5.13.7 source fields, plus the fork-only split wire protocol.
 *
 * <p>The upstream daemon/module pair used one fixed 536-byte datagram: a 384-byte facial prefix
 * followed by a 152-byte eye prefix. Because the two streams run at very different rates (the eye
 * service updates at roughly 90 Hz, the facial model at roughly 23 Hz), that framing re-sent the
 * unchanged half on every update of the other half. This fork does not keep it: each datagram is a
 * one-byte tag plus only the fields the receiver consumes, so the eye stream stays small and
 * independent.
 *
 * <pre>
 *   'E' + 72-byte eye frame     -> 73 bytes, sent when the eye stream updates
 *   'F' + 224-byte facial frame -> 225 bytes, sent when the facial stream updates
 * </pre>
 *
 * <p>Dropped on purpose: the vendor's 3D gaze points, position guides, foveated slots, the
 * always-zero blendshape slots 52..71 and the viseme/emotion fields. All are unread by the fork
 * module, so no upstream framing compatibility is preserved.
 */
public final class TrackingData {
    /** Datagram tags; neither collides with the ASCII control datagrams or the {@code MARCO\0} ping. */
    static final byte EYE_TAG = 'E';
    static final byte FACE_TAG = 'F';
    static final int EYE_PACKET_SIZE = 73;
    static final int FACE_PACKET_SIZE = 225;

    // Eye frame: payload starts at 1. Source offsets are inside the 200-byte eye sample.
    private static final int EYE_TIMESTAMP = 1;        // ulong <- source 168
    private static final int EYE_LEFT_STATUS = 9;      // <- source 0
    private static final int EYE_RIGHT_STATUS = 13;    // <- source 4
    private static final int EYE_COMBINED_STATUS = 17;// <- source 8
    private static final int EYE_VECTORS = 21;         // three float3: left/right/combined <- source 48
    private static final int EYE_OPENNESS = 57;        // left/right <- source 84
    private static final int EYE_PUPIL = 65;           // left/right <- source 92

    // Face frame: payload starts at 1. Source offsets are inside the 384-byte facial prefix.
    private static final int FACE_TIMESTAMP = 1;       // ulong <- source 0
    private static final int FACE_SHAPES = 9;          // 52 floats <- source 8
    private static final int FACE_SHAPE_COUNT = 52;
    private static final int FACE_VALID = 217;         // VideoInputValid[eye], [face] <- source 296

    // The receiver picks per-eye gaze solely from 0x100 (EYE_GAZE_VECTOR_VALID) and reads pupil
    // diameter without any validity check. Some firmware reports a fixed-depth split of the fused
    // gaze as its "per-eye" vectors rather than independent per-eye data; forwarding that makes the
    // receiver hold a constant offset between the two eyes. Strip the per-eye gaze point/vector
    // validity bits on the wire so the receiver keeps using the fused gaze while still reading real
    // pupil diameter. No-op when those bits are not set. In enhance mode with the dual plugin the
    // per-eye vectors are real, so the caller asks to keep them.
    private static final int STRIP_PER_EYE_GAZE_FLAGS = 0x80 | 0x100; // EYE_GAZE_POINT_VALID | EYE_GAZE_VECTOR_VALID

    private TrackingData() {}

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

    /** Builds a self-contained eye datagram from a 200-byte vendor eye sample. */
    static byte[] eyePacket(byte[] eye, boolean keepPerEyeGaze) {
        if (eye == null || eye.length < 200) throw new IllegalArgumentException("Eye prefix too short");
        byte[] packet = new byte[EYE_PACKET_SIZE];
        ByteBuffer src = bytes(eye), dst = bytes(packet);
        dst.put(0, EYE_TAG);
        dst.putLong(EYE_TIMESTAMP, src.getLong(168));
        int left = src.getInt(0), right = src.getInt(4);
        if (!keepPerEyeGaze) {
            left &= ~STRIP_PER_EYE_GAZE_FLAGS;
            right &= ~STRIP_PER_EYE_GAZE_FLAGS;
        }
        dst.putInt(EYE_LEFT_STATUS, left);
        dst.putInt(EYE_RIGHT_STATUS, right);
        dst.putInt(EYE_COMBINED_STATUS, src.getInt(8));
        for (int eyeIndex = 0; eyeIndex < 3; eyeIndex++) { // left, right, combined gaze vectors
            int source = 48 + eyeIndex * 12, destination = EYE_VECTORS + eyeIndex * 12;
            dst.putFloat(destination, src.getFloat(source));
            dst.putFloat(destination + 4, src.getFloat(source + 4));
            dst.putFloat(destination + 8, src.getFloat(source + 8));
        }
        dst.putFloat(EYE_OPENNESS, src.getFloat(84));
        dst.putFloat(EYE_OPENNESS + 4, src.getFloat(88));
        dst.putFloat(EYE_PUPIL, src.getFloat(92));
        dst.putFloat(EYE_PUPIL + 4, src.getFloat(96));
        return packet;
    }

    /** Builds a self-contained facial datagram from a >=384-byte vendor facial prefix. */
    static byte[] facePacket(byte[] face) {
        return facePacket(face, true, true);
    }

    /** Eye-only still needs low-rate blink/brow/wide/squint, but must not activate mouth output. */
    static byte[] facePacket(byte[] face, boolean sendEye, boolean sendFace) {
        if (face == null || face.length < 384) throw new IllegalArgumentException("Facial prefix too short");
        byte[] packet = new byte[FACE_PACKET_SIZE];
        ByteBuffer src = bytes(face), dst = bytes(packet);
        dst.put(0, FACE_TAG);
        dst.putLong(FACE_TIMESTAMP, src.getLong(0));
        for (int shape = 0; shape < FACE_SHAPE_COUNT; shape++) {
            if ((eyeShape(shape) && sendEye) || (!eyeShape(shape) && sendFace))
                dst.putFloat(FACE_SHAPES + shape * 4, src.getFloat(8 + shape * 4));
        }
        dst.putFloat(FACE_VALID, sendEye ? src.getFloat(296) : 0f);      // VIDEO_INPUT_EYE
        dst.putFloat(FACE_VALID + 4, sendFace ? src.getFloat(300) : 0f);  // VIDEO_INPUT_FACE
        return packet;
    }

    private static boolean eyeShape(int index) {
        switch (index) {
            case 0: case 2: case 3: case 4: case 11: case 12: case 16:
            case 26: case 28: case 30: case 31: case 35: case 36: case 38:
            case 41: case 44: case 45: case 46: case 47: return true;
            default: return false;
        }
    }
}
