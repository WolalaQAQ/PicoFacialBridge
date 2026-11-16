package dev.pico.facialprobe;

import java.nio.ByteBuffer;

/** Only successful socket sends enter this in-memory, display-only packet inspector. */
final class OutputStatus {
    private final byte[] eye, face, gaze;
    final int localMask, requestedMask, effectiveMask;
    final long epoch;
    final boolean subscribed;
    private OutputStatus(Accumulator source, int local, int requested, int effective, long epoch, boolean subscribed) {
        eye = copy(source.eye); face = copy(source.face); gaze = copy(source.gaze);
        localMask = local; requestedMask = requested; effectiveMask = effective; this.epoch = epoch; this.subscribed = subscribed;
    }
    static OutputStatus empty() { return new Accumulator().snapshot(0, 0, 0, 0, false); }
    private static byte[] copy(byte[] value) { return value == null ? null : value.clone(); }
    String describe() {
        StringBuilder out = new StringBuilder();
        out.append("localMask=").append(localMask).append(" requestedMask=").append(requestedMask)
                .append(" effectiveMask=").append(effectiveMask).append(" subscribed=").append(subscribed)
                .append(" currentAvatarEpoch=").append(epoch).append('\n');
        packet(out, "Avatar E", eye); packet(out, "Avatar A/F/X", face); packet(out, "Gaze E", gaze);
        return out.toString();
    }
    private static void field(StringBuilder out, String name, Object value) { out.append(name).append('=').append(value).append('\n'); }
    private static void packet(StringBuilder out, String route, byte[] packet) {
        out.append("\n[").append(route).append("]\n");
        if (packet == null) { out.append("not sent\n"); return; }
        ByteBuffer b = TrackingData.bytes(packet);
        field(out, "tag", (char) packet[0]); field(out, "bytes", packet.length);
        field(out, "mask", packet[1] & 255); field(out, "morphologyFlags", packet[2] & 255);
        field(out, "epoch", b.getLong(3)); field(out, "sourceNs", b.getLong(11));
        if (packet[0] == TrackingData.EYE_TAG) {
            String[] sides = {"L", "R", "Combined"}, axes = {"X", "Y", "Z"};
            for (int side = 0; side < 3; side++) {
                field(out, "status" + sides[side], String.format(java.util.Locale.ROOT, "0x%08x", b.getInt(19 + side * 4)));
                for (int axis = 0; axis < 3; axis++) field(out, "gaze" + sides[side] + axes[axis], b.getFloat(31 + side * 12 + axis * 4));
            }
            for (int side = 0; side < 2; side++) {
                field(out, "openness" + sides[side], b.getFloat(67 + side * 4));
                field(out, "pupilRaw" + sides[side], b.getFloat(75 + side * 4));
                field(out, "pupilMm" + sides[side], b.getFloat(75 + side * 4) / 10f);
            }
            return;
        }
        int mask = packet[1] & 255;
        int[] shapes = mask == 1 ? TrackingData.EYE_SHAPES : mask == 2 ? TrackingData.FACE_ONLY_SHAPES : null;
        int count = shapes == null ? 52 : shapes.length;
        for (int i = 0; i < count; i++) field(out, ExpressionCatalog.NAMES[shapes == null ? i : shapes[i]], b.getFloat(19 + i * 4));
        if (packet[0] == TrackingData.INDEPENDENT_TAG) {
            int offset = 19 + count * 4, version = b.getInt(offset);
            field(out, "expressionVersion", version); field(out, "BrowInnerUpR", b.getFloat(offset + 4));
            field(out, "leftState", b.getInt(offset + 8)); field(out, "rightState", b.getInt(offset + 12));
            if (version == 2) {
                field(out, "groupUpdated", b.getInt(offset + 16));
                field(out, "mouthSourceNs", b.getLong(offset + 20)); field(out, "rightSourceNs", b.getLong(offset + 28));
            }
        }
    }
    static final class Accumulator {
        private byte[] eye, face, gaze;
        private OutputStatus published;
        void sent(byte[] packet, boolean gazeRoute) {
            published = null;
            if (gazeRoute) gaze = packet.clone();
            else if (packet[0] == TrackingData.EYE_TAG) eye = packet.clone();
            else face = packet.clone();
        }
        OutputStatus snapshot(int local, int requested, int effective, long epoch, boolean subscribed) {
            if (published == null || published.localMask != local || published.requestedMask != requested
                    || published.effectiveMask != effective || published.epoch != epoch || published.subscribed != subscribed)
                published = new OutputStatus(this, local, requested, effective, epoch, subscribed);
            return published;
        }
    }
}
