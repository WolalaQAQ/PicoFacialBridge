package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Verified PICO OS 5.13.7 source fields, plus the BridgeSplit wire protocol.
 *
 * <p>The upstream daemon/module pair used one fixed 536-byte datagram: a 384-byte facial prefix
 * followed by a 152-byte eye prefix. Because the two streams run at very different rates (the eye
 * service updates at roughly 90 Hz, the facial model at roughly 23 Hz), that framing re-sent the
 * unchanged half on every update of the other half. BridgeSplit does not keep it: each datagram
 * carries only the fields the receiver consumes for the channels it subscribed to.
 *
 * <p>Every datagram starts with a 19-byte little-endian header: tag, channel mask (eye=1, face=2),
 * morphology validity (eye=1, face=2), int64 epoch, int64 source timestamp.
 *
 * <pre>
 *   'E' 83 bytes  native eye frame, when the eye channel is subscribed
 *   'A' 95 bytes  19 eye morphology shapes, eye-only subscription
 *   'F' 151 bytes 33 non-eye shapes, face-only subscription
 *   'F' 227 bytes all 52 shapes, both channels subscribed
 * </pre>
 *
 * <p>Dropped on purpose: the vendor's 3D gaze points, position guides, foveated slots, the
 * always-zero blendshape slots 52..71 and the viseme/emotion fields.
 */
public final class TrackingData {
    /** Datagram tags; none collides with the ASCII control datagrams or the {@code MARCO\0} ping. */
    static final byte EYE_TAG = 'E';
    static final byte FACE_TAG = 'F';
    static final byte AUX_TAG = 'A';
    static final int HEADER_SIZE = 19;
    static final int EYE_PACKET_SIZE = 83;

    private static final int HEADER_MASK = 1, HEADER_VALID = 2, HEADER_EPOCH = 3, HEADER_TIMESTAMP = 11;
    // Eye frame payload. Source offsets are inside the 200-byte eye sample.
    private static final int EYE_STATUS = 19;          // left/right/combined uint32 <- source 0
    private static final int EYE_VECTORS = 31;         // three float3: left/right/combined <- source 48
    private static final int EYE_OPENNESS = 67;        // left/right <- source 84
    private static final int EYE_PUPIL = 75;           // left/right <- source 92
    // Only the consumed state bits: combined gaze, openness, per-eye gaze vector, pupil.
    private static final int EYE_STATUS_BITS = 0x906;
    private static final int FACE_SHAPE_COUNT = 52;
    // Stable eye morphology order: ascending PICO shape identifiers (19 float32 fields).
    static final int[] EYE_SHAPES = {0, 2, 3, 4, 11, 12, 16, 26, 28, 30, 31, 35, 36, 38, 41, 44, 45, 46, 47};
    // Complement of EYE_SHAPES: face-only never serializes disabled eye morphology.
    static final int[] FACE_ONLY_SHAPES = {1, 5, 6, 7, 8, 9, 10, 13, 14, 15, 17, 18, 19, 20, 21, 22, 23,
            24, 25, 27, 29, 32, 33, 34, 37, 39, 40, 42, 43, 48, 49, 50, 51};

    private static void header(ByteBuffer dst, byte tag, int mask, int valid, long epoch, long timestamp) {
        dst.put(0, tag); dst.put(HEADER_MASK, (byte) mask); dst.put(HEADER_VALID, (byte) valid);
        dst.putLong(HEADER_EPOCH, epoch); dst.putLong(HEADER_TIMESTAMP, timestamp);
    }

    /**
     * Builds the native eye datagram from a 200-byte vendor eye sample. Vendor non-finite
     * sentinels are zeroed with their validity cleared, so an invalidation reaches the receiver
     * rather than freezing a frame.
     */
    static byte[] eyePacket(byte[] eye, boolean keepPerEyeGaze, int mask, long epoch) {
        if (eye == null || eye.length < 200) throw new IllegalArgumentException("Eye prefix too short");
        byte[] packet = new byte[EYE_PACKET_SIZE];
        ByteBuffer src = bytes(eye), dst = bytes(packet);
        header(dst, EYE_TAG, mask, 0, epoch, src.getLong(168));
        for (int i = 0; i < 3; i++) {
            int status = src.getInt(i * 4);
            if (i < 2 && !keepPerEyeGaze) status &= ~STRIP_PER_EYE_GAZE_FLAGS;
            status &= EYE_STATUS_BITS;
            for (int j = 0; j < 3; j++) {
                float value = src.getFloat(48 + i * 12 + j * 4);
                if (!Float.isFinite(value)) { value = 0f; status &= ~(i == 2 ? 0x2 : 0x100); }
                dst.putFloat(EYE_VECTORS + i * 12 + j * 4, value);
            }
            if (i < 2) {
                float openness = src.getFloat(84 + i * 4), pupil = src.getFloat(92 + i * 4);
                if (!Float.isFinite(openness)) { openness = 0f; status &= ~0x4; }
                if (!Float.isFinite(pupil)) { pupil = 0f; status &= ~0x800; }
                dst.putFloat(EYE_OPENNESS + i * 4, openness);
                dst.putFloat(EYE_PUPIL + i * 4, pupil);
            }
            dst.putInt(EYE_STATUS + i * 4, status);
        }
        return packet;
    }

    /** Directly serializes only the subscribed morphology from a >=384-byte vendor facial prefix. */
    static byte[] facePacket(byte[] face, int mask, long epoch) {
        if (face == null || face.length < 384) throw new IllegalArgumentException("Facial prefix too short");
        if (mask < 1 || mask > 3) throw new IllegalArgumentException("Unsupported channel mask");
        int[] shapes = mask == 1 ? EYE_SHAPES : mask == 2 ? FACE_ONLY_SHAPES : null;
        int count = shapes == null ? FACE_SHAPE_COUNT : shapes.length;
        byte[] packet = new byte[HEADER_SIZE + count * 4];
        ByteBuffer src = bytes(face), dst = bytes(packet);
        int valid = ((src.getFloat(296) == 1f ? 1 : 0) | (src.getFloat(300) == 1f ? 2 : 0)) & mask;
        header(dst, mask == 1 ? AUX_TAG : FACE_TAG, mask, valid, epoch, src.getLong(0));
        for (int i = 0; i < count; i++) {
            float value = src.getFloat(8 + (shapes == null ? i : shapes[i]) * 4);
            // No per-shape presence bits: zero a single non-finite sentinel instead of
            // invalidating the whole eye/face group, so one bad slot cannot blank the frame.
            dst.putFloat(HEADER_SIZE + i * 4, Float.isFinite(value) ? value : 0f);
        }
        return packet;
    }

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
}
