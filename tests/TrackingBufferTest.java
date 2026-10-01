package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Dependency-free tests runnable with a JDK before Android packaging. */
public final class TrackingBufferTest {
    static int checks;
    static ByteBuffer ring(int slotBytes, int index) {
        ByteBuffer memory = ByteBuffer.allocate(32 + 4 * slotBytes).order(ByteOrder.LITTLE_ENDIAN);
        memory.putInt(0, 1); memory.putInt(4, slotBytes); memory.putInt(8, 4);
        memory.putInt(12, index); memory.putInt(16, 32);
        return memory;
    }
    static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    static void rejects(ByteBuffer data, String name) {
        try { TrackingBuffer.latest(data, 148); }
        catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError("Accepted invalid " + name);
    }
    public static void main(String[] args) {
        ByteBuffer data = ring(152, 2);
        data.putLong(32 + 2 * 152, 123456789L);
        data.putInt(32 + 2 * 152 + 8, 7);
        byte[] sample = TrackingBuffer.latest(data, 148);
        check(sample.length == 152, "Preserve native slot padding");
        check(ByteBuffer.wrap(sample).order(ByteOrder.LITTLE_ENDIAN).getLong() == 123456789L, "Use data offset and slot stride");
        check(TrackingBuffer.latest(ring(152, -1), 148) == null, "Empty ring is not tracking success");
        rejects(ByteBuffer.allocate(19), "short header");
        ByteBuffer invalid = ring(152, 4); rejects(invalid, "index >= capacity");
        invalid = ring(152, -2); rejects(invalid, "negative index");
        invalid = ring(152, 0); invalid.putInt(0, 2); rejects(invalid, "unknown version");
        invalid = ring(152, 0); invalid.putInt(8, 0); rejects(invalid, "zero capacity");
        invalid = ring(152, 0); invalid.putInt(4, 147); rejects(invalid, "undersized slot");
        invalid = ring(152, 0); invalid.putInt(16, 0); rejects(invalid, "header overlap");
        invalid = ring(152, 0); invalid.putInt(16, 0x7fffffff); rejects(invalid, "offset overflow");
        invalid = ring(152, 0); invalid.putInt(8, 0x7fffffff); rejects(invalid, "capacity overflow");
        invalid = ring(152, 0); invalid.putInt(4, -1); rejects(invalid, "unsigned stride overflow");
        data.putLong(32 + 2 * 152, 123456790L);
        check(!Arrays.equals(sample, TrackingBuffer.latest(data, 148)), "Ring wrap at same index may hold a new timestamp");
        ByteBuffer face = ring(384, 0); face.putLong(32, 123L); face.putFloat(32 + 8 + 7 * 4, 0.75f);
        face.putFloat(32 + 296, 1f); face.putFloat(32 + 300, 1f);
        byte[] facial = TrackingBuffer.latest(face, 380);
        check(facial.length == 384, "Preserve face tail padding");
        check(ByteBuffer.wrap(facial).order(ByteOrder.LITTLE_ENDIAN).getFloat(8 + 7 * 4) == 0.75f, "Face blendshape offset");
        ByteBuffer modernEye = ring(200, 0);
        modernEye.putInt(32, 20); modernEye.putInt(36, 20); modernEye.putInt(40, 3);
        modernEye.putFloat(32 + 72, 0.3f); modernEye.putFloat(32 + 76, -0.2f); modernEye.putFloat(32 + 80, -1f);
        modernEye.putFloat(32 + 84, 0.8f); modernEye.putLong(32 + 168, 9000000000L);
        byte[] eyeBytes = TrackingBuffer.latest(modernEye, 200, 168);
        check(TrackingData.eyeTimestamp(eyeBytes) == 9000000000L, "Firmware eye timestamp at 168, not 0");
        check(TrackingData.eyeValid(eyeBytes), "Combined gaze bit 1 is valid");
        check(TrackingData.combinedGazeX(eyeBytes) == 0.3f, "Combined gaze at 72");
        byte[] facePacket = TrackingData.facePacket(facial);
        check(facePacket.length == 225 && facePacket[0] == 'F', "Compact facial datagram has the face tag");
        ByteBuffer faceWire = ByteBuffer.wrap(facePacket).order(ByteOrder.LITTLE_ENDIAN);
        check(faceWire.getLong(1) == 123L, "Facial timestamp preserved");
        check(faceWire.getFloat(217) == 1f && faceWire.getFloat(221) == 1f, "Facial video-validity flags carried");
        check(Arrays.equals(Arrays.copyOfRange(facial, 8, 8 + 52 * 4), Arrays.copyOfRange(facePacket, 9, 9 + 52 * 4)),
                "All 52 used blendshapes copied verbatim");
        byte[] eyePacket = TrackingData.eyePacket(eyeBytes, false);
        check(eyePacket.length == 73 && eyePacket[0] == 'E', "Compact eye datagram has the eye tag");
        ByteBuffer eyeWire = ByteBuffer.wrap(eyePacket).order(ByteOrder.LITTLE_ENDIAN);
        check(eyeWire.getLong(1) == 9000000000L, "Real eye timestamp on the wire");
        check(eyeWire.getInt(9) == 20 && eyeWire.getInt(13) == 20, "Left/right eye status offsets");
        check(eyeWire.getInt(17) == 3, "Combined eye status offset");
        check(eyeWire.getFloat(45) == 0.3f && eyeWire.getFloat(49) == -0.2f && eyeWire.getFloat(53) == -1f, "Combined gaze vector carried");
        check(eyeWire.getFloat(57) == 0.8f, "Left openness carried");
        // The per-eye gaze point (0x80) and gaze vector (0x100) validity bits are intentionally
        // dropped so the receiver keeps the fused gaze, while openness (0x4), pupil diameter
        // (0x800) and the combined status survive.
        byte[] gatedEye = eyeBytes.clone();
        ByteBuffer gated = ByteBuffer.wrap(gatedEye).order(ByteOrder.LITTLE_ENDIAN);
        gated.putInt(0, 0x984); gated.putInt(4, 0x984);
        ByteBuffer stripped = ByteBuffer.wrap(TrackingData.eyePacket(gatedEye, false)).order(ByteOrder.LITTLE_ENDIAN);
        check(stripped.getInt(9) == 0x804 && stripped.getInt(13) == 0x804,
                "Strip only per-eye gaze point/vector validity bits");
        check(stripped.getInt(17) == 3, "Combined eye status is never stripped");
        check(gated.getInt(0) == 0x984 && gated.getInt(4) == 0x984, "Stripping must not mutate the source sample");
        eyeBytes[8] = 0;
        check(!TrackingData.eyeValid(eyeBytes), "No gaze-valid bit is not eye tracking success");
        check(TrackingData.forwardable(facial, eyeBytes, 9100000000L) == false, "Old face timestamp must not be forwarded");
        ByteBuffer.wrap(facial).order(ByteOrder.LITTLE_ENDIAN).putLong(0, 9000000000L);
        check(TrackingData.forwardable(facial, eyeBytes, 9100000000L), "Fresh invalid gaze still forwards blink/face data");
        check(!TrackingData.forwardable(facial, eyeBytes, 12000000000L), "Stale mappings stop packets");
        check(StreamHealth.reconnectDue(true, false, 1000, 999), "Binder death reconnects immediately");
        check(!StreamHealth.reconnectDue(true, true, 9999, 0), "Allow startup grace interval");
        check(StreamHealth.reconnectDue(true, true, 10000, 0), "Stalled mapping reconnects after grace");
        check(!StreamHealth.reconnectDue(false, false, 20000, 0), "Do not recover an intentionally closed session");
        int[] cleanup = {0};
        try {
            ResourceScope.close(() -> { cleanup[0]++; throw new Exception("unmap"); },
                    () -> { cleanup[0]++; throw new Exception("fd"); }, () -> cleanup[0]++);
            throw new AssertionError("Expected aggregate cleanup failure");
        } catch (Exception expected) { check(cleanup[0] == 3 && expected.getSuppressed().length == 1, "Cleanup still invokes STOP after earlier failures"); }
        try { TrackingData.facePacket(new byte[383]); throw new AssertionError("Accepted short face"); }
        catch (IllegalArgumentException expected) { checks++; }
        try { TrackingData.eyePacket(new byte[199], false); throw new AssertionError("Accepted short eye"); }
        catch (IllegalArgumentException expected) { checks++; }
        // Enhance mode with the dual plugin is the only case that keeps the real per-eye bits.
        ByteBuffer kept = ByteBuffer.wrap(TrackingData.eyePacket(gatedEye, true)).order(ByteOrder.LITTLE_ENDIAN);
        check(kept.getInt(9) == 0x984 && kept.getInt(13) == 0x984,
                "Enhance mode keeps the real per-eye validity bits");
        check(gated.getInt(0) == 0x984 && gated.getInt(4) == 0x984, "Enhance-mode packet never mutates the source sample");
        // Mode detection: only root + a published, gate-open module reaches enhance.
        check(!TrackingMode.detect(false, false, "on", "dual").enhance, "Rootless stays normal");
        check(!TrackingMode.detect(true, false, "on", "dual").enhance, "Root without a module stays normal");
        check(!TrackingMode.detect(true, true, "off", "dual").enhance, "A closed gate stays normal");
        TrackingMode.Info enhance = TrackingMode.detect(true, true, "on", "dual");
        check(enhance.enhance && enhance.keepPerEyeGaze() && TrackingMode.ENHANCE.equals(enhance.label),
                "Root + module + gate + dual is enhance");
        check(TrackingMode.detect(true, true, "on", "left").enhance, "Single-eye plugin is enhance without per-eye gaze");
        check(!TrackingMode.detect(true, true, "on", "left").keepPerEyeGaze(), "Only dual keeps per-eye gaze");
        String advert = TrackingMode.advertise(enhance);
        check(advert.startsWith(TrackingMode.PREFIX), "Advert carries the control prefix");
        check(advert.length() != 6 && advert.charAt(0) != 'E' && advert.charAt(0) != 'F', "Advert cannot be mistaken for a ping or a tracking packet");
        check(enhance.sameAs(TrackingMode.parse(advert)), "Advert round-trips through the PC parser");
        TrackingMode.Info noMode = TrackingMode.parse("PXR_MODE v=1 rooted=1 enhance=1 gate=1 plugin=dual");
        check(noMode != null && noMode.enhance && noMode.keepPerEyeGaze(), "Mode falls back to gate+enhance");
        check(TrackingMode.parse("DISCOVER_DAEMON") == null, "Foreign datagrams are not mode messages");
        check(TrackingMode.OFF.equals(TrackingMode.parse("PXR_MODE v=1 mode=enhance rooted=1 enhance=1 gate=1").plugin),
                "An absent plugin never unlocks per-eye gaze");
        check(TrackingMode.statusValue("mode=left gate=on", "mode").equals("left")
                && TrackingMode.statusValue("mode=left", "gate").isEmpty(), "Status line parser");
        System.out.println("PASS: " + checks + " shared-memory checks");
    }
}
