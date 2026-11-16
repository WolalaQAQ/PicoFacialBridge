package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.util.List;
import static dev.pico.facialprobe.ExpressionStatus.Data;

/** Native E fields, decoded with the same encoder and validity rules as the PC receiver. */
final class NativeEyeStatus {
    static final int COUNT = 13;
    static final String[] NAMES = {"PupilDiameterL", "PupilDiameterR", "OpennessL", "OpennessR",
            "GazeLX", "GazeRX", "GazeLY", "GazeRY", "GazeLZ", "GazeRZ", "GazeCombinedX", "GazeCombinedY", "GazeCombinedZ"};
    private final float[] values = new float[COUNT];
    private final float[] rawPupil = new float[2];
    private final int[] rawStatus = new int[3], wireStatus = new int[3];
    private final boolean[] gazeFinite = new boolean[3];
    private final boolean keepPerEye;
    final long sourceNanos;
    final int captureMask;
    private NativeEyeStatus(Accumulator source, boolean keepPerEye) {
        this.keepPerEye = keepPerEye;
        captureMask = source.mask;
        sourceNanos = source.time;
        if (source.eye == null) return;
        ByteBuffer raw = TrackingData.bytes(source.eye);
        ByteBuffer wire = TrackingData.bytes(TrackingData.eyePacket(source.eye, keepPerEye, 1, 0));
        for (int i = 0; i < 3; i++) {
            rawStatus[i] = raw.getInt(i * 4); wireStatus[i] = wire.getInt(19 + i * 4);
            gazeFinite[i] = true;
            for (int axis = 0; axis < 3; axis++) gazeFinite[i] &= Float.isFinite(raw.getFloat(48 + i * 12 + axis * 4));
        }
        for (int side = 0; side < 2; side++) {
            rawPupil[side] = wire.getFloat(75 + side * 4);
            values[side] = rawPupil[side] / 10f;
            values[2 + side] = wire.getFloat(67 + side * 4);
            for (int axis = 0; axis < 3; axis++) values[4 + axis * 2 + side] = wire.getFloat(31 + side * 12 + axis * 4);
        }
        for (int axis = 0; axis < 3; axis++) values[10 + axis] = wire.getFloat(55 + axis * 4);
    }
    static NativeEyeStatus empty() { return new Accumulator().snapshot(false); }
    float value(int field) { return values[field]; }
    float rawPupil(int side) { return rawPupil[side]; }
    int rawStatus(int side) { return rawStatus[side]; }
    int wireStatus(int side) { return wireStatus[side]; }
    String describe() {
        StringBuilder out = new StringBuilder("[Bridge native source / Avatar-E encoding]\n");
        out.append("captureMask=").append(captureMask).append(" sourceNs=").append(sourceNanos).append('\n');
        for (int i = 0; i < 3; i++) out.append("status[").append(i).append("] source=")
                .append(String.format(java.util.Locale.ROOT, "0x%08x", rawStatus[i])).append(" encoded=")
                .append(String.format(java.util.Locale.ROOT, "0x%08x", wireStatus[i])).append('\n');
        for (int i = 0; i < COUNT; i++) out.append(NAMES[i]).append('=')
                .append(sourceNanos == 0 ? "not observed" : Float.toString(values[i])).append('\n');
        for (int i = 0; i < 2; i++) out.append("pupilRaw").append(i == 0 ? 'L' : 'R').append('=')
                .append(sourceNanos == 0 ? "not observed" : Float.toString(rawPupil[i])).append('\n');
        return out.toString();
    }
    long ageNanos(long now) { return sourceNanos == 0 ? -1 : Math.max(0, now - sourceNanos); }
    Data sampleData(long now, int enabledMask, boolean running) {
        if (!running) return Data.STOPPED;
        if ((enabledMask & 1) == 0) return Data.OFF;
        if ((captureMask & 1) == 0) return Data.NOT_CAPTURED;
        if (sourceNanos == 0) return Data.WAITING;
        return TrackingData.fresh(now, sourceNanos) ? Data.LIVE : Data.STALE;
    }
    Data data(int field, long now, int enabledMask, boolean running) {
        Data base = sampleData(now, enabledMask, running);
        if (base != Data.LIVE) return base;
        int side = field < 4 ? field % 2 : field < 10 ? (field - 4) % 2 : 2;
        int bit = field < 2 ? 0x800 : field < 4 ? 4 : field < 10 ? 0x100 : 2;
        if (field >= 4 && !gazeFinite[side]) return Data.INVALID;
        if ((wireStatus[side] & bit) == 0) {
            if (bit == 0x100 && !keepPerEye && (rawStatus[side] & bit) != 0)
                return Data.FILTERED;
            return Data.INVALID;
        }
        if (!Float.isFinite(values[field]) || field < 2 && values[field] <= 0
                || field >= 2 && field < 4 && (values[field] < 0 || values[field] > 1)) return Data.INVALID;
        return Data.LIVE;
    }
    static final class Row {
        final String chinese, english;
        final int left, right;
        final float minimum, maximum;
        final boolean raw;
        Row(String chinese, String english, int left, int right, float minimum, float maximum, boolean raw) {
            this.chinese = chinese; this.english = english; this.left = left; this.right = right;
            this.minimum = minimum; this.maximum = maximum; this.raw = raw;
        }
        String code() { return raw ? "PupilRawL / PupilRawR" : NAMES[left] + (right < 0 ? "" : " / " + NAMES[right]); }
        boolean matches(String query) {
            return chinese.contains(query) || english.toLowerCase(java.util.Locale.ROOT).contains(query)
                    || code().toLowerCase(java.util.Locale.ROOT).contains(query);
        }
        float value(NativeEyeStatus sample, int field) { return raw ? sample.rawPupil(field) : sample.value(field); }
    }
    static final Row[] ROWS = {
        new Row("瞳孔直径 · mm", "Pupil diameter · mm", 0, 1, 0, 12, false),
        new Row("瞳孔原值 · 线上值", "Pupil raw · wire value", 0, 1, 0, 120, true),
        new Row("原生睁眼度", "Native openness", 2, 3, 0, 1, false),
        new Row("逐眼视线 X", "Per-eye gaze X", 4, 5, -1, 1, false),
        new Row("逐眼视线 Y", "Per-eye gaze Y", 6, 7, -1, 1, false),
        new Row("逐眼视线 Z", "Per-eye gaze Z", 8, 9, -1, 1, false),
        new Row("合并视线 X", "Combined gaze X", 10, -1, -1, 1, false),
        new Row("合并视线 Y", "Combined gaze Y", 11, -1, -1, 1, false),
        new Row("合并视线 Z", "Combined gaze Z", 12, -1, -1, 1, false)
    };
    static final class Accumulator {
        private byte[] eye;
        private long time;
        private int mask;
        private NativeEyeStatus published;
        void accept(List<byte[]> eyes, int captureMask) {
            if (mask != captureMask) published = null;
            mask = captureMask;
            if ((mask & 1) == 0) { eye = null; time = 0; return; }
            for (byte[] sample : eyes) {
                long next = TrackingData.eyeTimestamp(sample);
                if (next > time) { eye = sample.clone(); time = next; published = null; }
            }
        }
        NativeEyeStatus snapshot(boolean keepPerEye) {
            if (published == null || published.keepPerEye != keepPerEye) published = new NativeEyeStatus(this, keepPerEye);
            return published;
        }
    }
}
