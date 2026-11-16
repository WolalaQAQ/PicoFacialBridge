package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/** Read-only source expressions, with independent L/R/mouth observation clocks. */
final class ExpressionStatus {
    static final int COUNT = 53, LEFT = 0, RIGHT = 1, MOUTH = 2;
    enum Data { LIVE, INVALID, STALE, WAITING, OFF, NOT_CAPTURED, STOPPED, UNSUPPORTED, FILTERED }
    private final float[] values;
    private final long[] times;
    private final boolean[] valid;
    private final int[] quality;
    final int version, captureMask, lastFrameUpdated;

    private ExpressionStatus(Accumulator source) {
        values = source.values.clone(); times = source.times.clone(); valid = source.valid.clone();
        quality = source.quality.clone();
        version = source.version; captureMask = source.mask; lastFrameUpdated = source.updated;
    }
    static ExpressionStatus empty() { return new Accumulator().snapshot(); }
    float value(int shape) { return values[shape]; }
    String describe() {
        StringBuilder out = new StringBuilder("[Bridge expression source snapshot]\n");
        out.append("version=").append(version).append(" captureMask=").append(captureMask)
                .append(" lastFrameUpdated=").append(lastFrameUpdated).append('\n');
        String[] groups = {"left", "right", "mouth"};
        for (int i = 0; i < 3; i++) out.append(groups[i]).append("SourceNs=").append(times[i])
                .append(" quality=").append(quality[i]).append(" usable=").append(valid[i]).append('\n');
        for (int i = 0; i < COUNT; i++) out.append(ExpressionCatalog.NAMES[i]).append('=')
                .append(times[group(i)] == 0 ? "not observed" : Float.toString(values[i])).append('\n');
        return out.toString();
    }
    long ageNanos(int group, long now) { return times[group] == 0 ? -1 : Math.max(0, now - times[group]); }
    static int group(int shape) {
        switch (shape) {
            case 4: case 11: case 12: case 35: case 36: case 38: case 41: case 45: case 46: case 52:
                return RIGHT;
            case 0: case 2: case 3: case 16: case 26: case 28: case 30: case 31: case 44: case 47:
                return LEFT;
            default: return MOUTH;
        }
    }
    Data data(int shape, long now, int enabledMask, boolean running) {
        if (shape == 52 && version == 0) return Data.UNSUPPORTED;
        if (!running) return Data.STOPPED;
        int group = group(shape), channel = group == MOUTH ? 2 : 1;
        if ((enabledMask & channel) == 0) return Data.OFF;
        if ((captureMask & channel) == 0) return Data.NOT_CAPTURED;
        if (times[group] == 0) return Data.WAITING;
        if (!TrackingData.fresh(now, times[group])) return Data.STALE;
        if (!valid[group] || !Float.isFinite(values[shape])) return Data.INVALID;
        return Data.LIVE;
    }

    /** Worker-owned; only snapshot() crosses to the UI thread. */
    static final class Accumulator {
        private final float[] values = new float[COUNT];
        private final long[] times = new long[3];
        private final boolean[] valid = new boolean[3];
        private final int[] quality = {-1, -1, -1};
        private int version = -1, mask;
        private int updated;
        private ExpressionStatus published;

        Accumulator() { Arrays.fill(values, Float.NaN); }
        void accept(List<byte[]> faces, int captureMask) {
            if (mask != captureMask) published = null;
            for (int group = 0; group < 3; group++) {
                int channel = group == MOUTH ? 2 : 1;
                if ((captureMask & channel) == 0) { times[group] = 0; valid[group] = false; quality[group] = -1; }
            }
            mask = captureMask;
            for (byte[] face : faces) {
                int nextVersion = TrackingData.independentVersion(face);
                if (nextVersion != version) {
                    published = null;
                    Arrays.fill(values, Float.NaN); Arrays.fill(times, 0); Arrays.fill(valid, false);
                    Arrays.fill(quality, -1);
                    version = nextVersion;
                }
                ByteBuffer src = TrackingData.bytes(face);
                int nextUpdated = version == 2 ? src.getInt(236) : 7;
                if (updated != nextUpdated) published = null;
                updated = nextUpdated;
                for (int group = 0; group < 3; group++) {
                    int channel = group == MOUTH ? 2 : 1;
                    long time = src.getLong(version != 2 || group == LEFT ? 0 : group == RIGHT ? 248 : 240);
                    if ((mask & channel) == 0 || (updated & (1 << group)) == 0 || time <= times[group]) continue;
                    published = null;
                    times[group] = time;
                    quality[group] = group == MOUTH ? (src.getFloat(300) == 1f ? 1 : 0)
                            : version == 0 ? (src.getFloat(296) == 1f ? 1 : 0) : src.getInt(group == LEFT ? 220 : 224);
                    valid[group] = group == MOUTH ? src.getFloat(300) == 1f
                            : version == 0 ? src.getFloat(296) == 1f
                            : src.getInt(group == LEFT ? 220 : 224) != 0;
                    if (group == RIGHT && version != 0 && !Float.isFinite(src.getFloat(216))) valid[group] = false;
                    for (int shape = 0; shape < COUNT; shape++) {
                        if (group(shape) == group && (shape != 52 || version != 0))
                            values[shape] = src.getFloat(shape == 52 ? 216 : 8 + shape * 4);
                    }
                }
            }
        }
        ExpressionStatus snapshot() {
            if (published == null) published = new ExpressionStatus(this);
            return published;
        }
    }
}
