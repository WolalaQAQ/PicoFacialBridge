package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.util.Collections;
import static dev.pico.facialprobe.ExpressionStatus.Data.*;

public final class NativeEyeStatusTest {
    private static int checks;
    private static final long NOW = 10_000_000_000L;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void near(float actual, float expected, String message) { check(Math.abs(actual - expected) < .00001f, message); }
    static byte[] eye(long time) {
        byte[] data = new byte[200]; ByteBuffer b = TrackingData.bytes(data);
        b.putInt(0, 0x904); b.putInt(4, 0x904); b.putInt(8, 2); b.putLong(168, time);
        for (int i = 0; i < 9; i++) b.putFloat(48 + i * 4, (i - 4) / 10f);
        b.putFloat(84, .75f); b.putFloat(88, .35f); b.putFloat(92, 35.53125f); b.putFloat(96, 42f);
        return data;
    }
    private static NativeEyeStatus sample(byte[] eye, boolean keep) {
        NativeEyeStatus.Accumulator a = new NativeEyeStatus.Accumulator();
        a.accept(Collections.singletonList(eye), 1); return a.snapshot(keep);
    }
    private static void fields() {
        byte[] e = eye(NOW); NativeEyeStatus s = sample(e, true);
        ByteBuffer wire = TrackingData.bytes(TrackingData.eyePacket(e, true, 1, 99));
        for (int i = 0; i < NativeEyeStatus.COUNT; i++) check(s.data(i, NOW, 1, true) == LIVE, "All native scalars valid: " + i);
        for (int side = 0; side < 2; side++) {
            near(s.rawPupil(side), wire.getFloat(75 + side * 4), "Raw pupil matches actual encoder");
            near(s.value(side), wire.getFloat(75 + side * 4) / 10f, "Same /10 mm conversion as PC WireFields");
            near(s.value(2 + side), wire.getFloat(67 + side * 4), "Real openness, not 1-Blink");
            for (int axis = 0; axis < 3; axis++)
                near(s.value(4 + axis * 2 + side), wire.getFloat(31 + side * 12 + axis * 4), "Per-eye signed component");
        }
        for (int axis = 0; axis < 3; axis++) near(s.value(10 + axis), wire.getFloat(55 + axis * 4), "Combined signed component");
        for (int side = 0; side < 3; side++) check(s.wireStatus(side) == wire.getInt(19 + side * 4), "Wire status exact");
        boolean[] covered = new boolean[13];
        for (NativeEyeStatus.Row row : NativeEyeStatus.ROWS) {
            covered[row.left] = true; if (row.right >= 0) covered[row.right] = true;
            check(row.maximum > row.minimum, "Every row has a real scale");
        }
        for (boolean present : covered) check(present, "Every native scalar has a visible row");
        check(NativeEyeStatus.ROWS[0].maximum == 12 && NativeEyeStatus.ROWS[1].maximum == 120, "Pupil is not clamped to blendshape scale");
        check(NativeEyeStatus.ROWS[3].minimum == -1, "Negative gaze components retain signed scale");
        check(NativeEyeStatus.ROWS[0].matches("pupildiameterr") && NativeEyeStatus.ROWS[1].matches("pupilrawr"), "Search both pupil representations");
        check(NativeEyeStatus.ROWS[0].matches("瞳孔"), "Chinese pupil search");
        for (String name : NativeEyeStatus.NAMES) check(s.describe().contains(name + "="), "Source details expose all native fields");
        check(s.describe().contains("pupilRawL=35.53125"), "Source details preserve raw pupil alongside millimeters");
        check(NativeEyeStatus.empty().describe().contains("PupilDiameterL=not observed"), "Missing source is not fabricated zero");
    }
    private static void validity() {
        NativeEyeStatus normal = sample(eye(NOW), false);
        for (int i = 4; i < 10; i++) check(normal.data(i, NOW, 1, true) == FILTERED, "Normal mode strips independent validity");
        check(normal.data(10, NOW, 1, true) == LIVE && normal.data(0, NOW, 1, true) == LIVE, "Filtering gaze does not hide pupil/combined");
        check(normal.rawStatus(0) == 0x904 && normal.wireStatus(0) == 0x804, "Source and wire flags distinguished");
        byte[] invalid = eye(NOW);
        TrackingData.bytes(invalid).putFloat(48, Float.NaN).putFloat(92, Float.NaN).putFloat(88, Float.POSITIVE_INFINITY);
        for (boolean keep : new boolean[]{false, true}) {
            NativeEyeStatus s = sample(invalid, keep);
            check(s.data(4, NOW, 1, true) == INVALID && s.data(8, NOW, 1, true) == INVALID, "One bad vector component invalidates its whole eye, never mislabeled filtered");
            check(s.data(0, NOW, 1, true) == INVALID && s.data(1, NOW, 1, true) == LIVE, "Pupil invalidation is per eye");
            check(s.data(3, NOW, 1, true) == INVALID && s.data(2, NOW, 1, true) == LIVE, "Openness validity independent");
            check(s.data(10, NOW, 1, true) == LIVE, "Other native data survives sentinels");
        }
        for (float value : new float[]{0, -1}) {
            byte[] badPupil = eye(NOW); TrackingData.bytes(badPupil).putFloat(92, value);
            check(sample(badPupil, true).data(0, NOW, 1, true) == INVALID, "Receiver rejects nonpositive pupil without inventing default");
        }
        byte[] badOpen = eye(NOW); TrackingData.bytes(badOpen).putFloat(84, 1.2f);
        check(sample(badOpen, true).data(2, NOW, 1, true) == INVALID, "Receiver rejects openness out of range");
        byte[] badCombined = eye(NOW); TrackingData.bytes(badCombined).putFloat(80, Float.NaN);
        check(sample(badCombined, true).data(10, NOW, 1, true) == INVALID, "Combined quality is not borrowed from per-eye gaze");
    }
    private static void clocks() {
        NativeEyeStatus.Accumulator a = new NativeEyeStatus.Accumulator();
        a.accept(Collections.emptyList(), 1);
        check(a.snapshot(true).data(0, NOW, 1, true) == WAITING, "New capture waits for native data");
        byte[] first = eye(NOW); a.accept(Collections.singletonList(first), 1);
        TrackingData.bytes(first).putFloat(92, 99);
        NativeEyeStatus before = a.snapshot(true);
        near(before.rawPupil(0), 35.53125f, "Accumulator owns immutable sample");
        byte[] newer = eye(NOW + 100); TrackingData.bytes(newer).putFloat(92, 50);
        a.accept(Collections.singletonList(newer), 1);
        near(before.rawPupil(0), 35.53125f, "Published snapshot stays immutable");
        a.accept(Collections.singletonList(eye(NOW - 1)), 1);
        near(a.snapshot(true).rawPupil(0), 50, "Old native source cannot overwrite latest");
        NativeEyeStatus s = a.snapshot(true);
        check(s.ageNanos(NOW + 200) == 100, "Native has its own source clock");
        check(s.data(0, NOW + 3_000_000_000L, 1, true) == STALE, "Native expires without needing a new publication");
        check(s.data(0, NOW - 200_000_000L, 1, true) == STALE, "Future timestamp beyond tolerance is not live");
        check(s.data(0, NOW, 0, true) == OFF && s.data(0, NOW, 1, false) == STOPPED, "Local and service gates");
        a.accept(Collections.emptyList(), 0);
        check(a.snapshot(true).data(0, NOW, 1, true) == NOT_CAPTURED, "Non-eye capture has no native sample");
        a.accept(Collections.emptyList(), 1);
        check(a.snapshot(true).data(0, NOW, 1, true) == WAITING, "Re-enabled native waits for new observation");
    }
    private static byte[] face(int version) {
        byte[] f = new byte[384]; ByteBuffer b = TrackingData.bytes(f);
        b.putLong(0, NOW); b.putFloat(296, 1); b.putFloat(300, 1);
        for (int i = 0; i < 52; i++) b.putFloat(8 + i * 4, i / 100f);
        b.putFloat(216, .8125f); b.putInt(220, -1); b.putInt(224, 1);
        if (version != 0) { b.putInt(228, 0x31495045); b.putInt(232, version); }
        b.putInt(236, 7); b.putLong(240, NOW + 2); b.putLong(248, NOW + 1);
        return f;
    }
    private static void packets() {
        OutputStatus.Accumulator history = new OutputStatus.Accumulator();
        check(history.snapshot(1, 0, 0, 0, false).describe().contains("not sent"), "No receiver is not a fictitious successful packet");
        for (int version : new int[]{0, 1, 2}) for (int mask = 1; mask <= 3; mask++) {
            byte[] packet = TrackingData.facePacket(face(version), mask, 77, NOW + 3);
            history.sent(packet, false);
            String text = history.snapshot(3, mask, mask, 88, true).describe();
            check(text.contains("tag=" + (char) packet[0]) && text.contains("bytes=" + packet.length), "Every A/F/X variant inspected");
            check(text.contains("epoch=77\n") && text.contains("currentAvatarEpoch=88"), "Historical packet epoch not confused with current session");
            int[] shapes = mask == 1 ? TrackingData.EYE_SHAPES : mask == 2 ? TrackingData.FACE_ONLY_SHAPES : null;
            for (int i = 0; i < (shapes == null ? 52 : shapes.length); i++) {
                int shape = shapes == null ? i : shapes[i];
                check(text.contains(ExpressionCatalog.NAMES[shape] + "=" + Float.toString(shape / 100f) + "\n"), "Every encoded shape present");
            }
            if (packet[0] == 'X') {
                check(text.contains("leftState=-1\n") && text.contains("rightState=1\n") && text.contains("BrowInnerUpR=0.8125"), "X quality and right inner brow exposed");
                if (version == 2) check(text.contains("groupUpdated=" + (mask == 1 ? 3 : 7))
                        && text.contains("mouthSourceNs=" + (NOW + 2)) && text.contains("rightSourceNs=" + (NOW + 1)), "All v2 group metadata exposed");
            }
        }
        byte[] e = TrackingData.eyePacket(eye(NOW), true, 1, 91);
        history.sent(e, false);
        history.sent(TrackingData.eyePacket(eye(NOW), false, 1, 92), true);
        OutputStatus frozen = history.snapshot(1, 1, 1, 91, true);
        String text = frozen.describe();
        for (String field : new String[]{"gazeLX", "gazeLY", "gazeLZ", "gazeRX", "gazeRY", "gazeRZ",
                "gazeCombinedX", "gazeCombinedY", "gazeCombinedZ", "opennessL", "opennessR", "pupilRawL", "pupilRawR"})
            check(text.contains(field + "="), "Every native wire float appears: " + field);
        check(text.contains("statusL=0x00000904") && text.contains("statusL=0x00000804"), "Avatar and gaze routes retain their actual filtering");
        check(text.contains("pupilRawL=35.53125") && text.contains("pupilMmL=3.553125"), "Raw and mm, without 3-decimal display rounding");
        java.util.Arrays.fill(e, (byte) 0);
        check(frozen.describe().equals(text), "Packet snapshot is immutable");
        history.sent(TrackingData.eyePacket(eye(NOW + 100), true, 1, 100), false);
        check(frozen.describe().equals(text), "Frozen successful output cannot change with later sends");
    }
    private static void publication() {
        NativeEyeStatus.Accumulator a = new NativeEyeStatus.Accumulator();
        NativeEyeStatus previous = a.snapshot(true);
        a.accept(Collections.emptyList(), 0);
        check(a.snapshot(true) == previous, "Idle native polls reuse a snapshot");
        for (int i = 0; i < 9; i++) {
            long time = NOW + i * 11_111_111L;
            byte[] sample = eye(time); TrackingData.bytes(sample).putFloat(92, 30 + i);
            a.accept(Collections.singletonList(sample), 1);
            NativeEyeStatus current = a.snapshot(true);
            check(current != previous && current.sourceNanos == time, "Native publication follows each source, not a 100ms timer");
            near(current.rawPupil(0), 30 + i, "Latest real pupil is displayed without interpolation");
            a.accept(Collections.singletonList(sample), 1);
            check(a.snapshot(true) == current, "Duplicate source does not rebuild native snapshot");
            previous = current;
        }
        NativeEyeStatus filtered = a.snapshot(false);
        check(filtered != previous && filtered.data(4, previous.sourceNanos, 1, true) == FILTERED,
                "Mode change publishes immediately even without new native samples");
        check(a.snapshot(false) == filtered, "Unchanged mode reuses the snapshot");
        a.accept(Collections.emptyList(), 0);
        check(a.snapshot(false).sourceNanos == 0 && a.snapshot(false).captureMask == 0,
                "Capture-off gate publishes immediately");
        check(previous.sourceNanos > 0 && previous.rawPupil(0) == 38, "Frozen native data stays unchanged");

        OutputStatus.Accumulator history = new OutputStatus.Accumulator();
        OutputStatus first = history.snapshot(1, 1, 1, 1, true);
        check(history.snapshot(1, 1, 1, 1, true) == first, "Unchanged output metadata does not copy packets");
        history.sent(TrackingData.eyePacket(eye(NOW), true, 1, 1), false);
        OutputStatus sent = history.snapshot(1, 1, 1, 1, true);
        check(sent != first && history.snapshot(1, 1, 1, 1, true) == sent, "Successful sends immediately replace the output snapshot");
        OutputStatus local = history.snapshot(3, 1, 1, 1, true);
        OutputStatus request = history.snapshot(3, 3, 1, 1, true);
        OutputStatus effective = history.snapshot(3, 3, 3, 1, true);
        OutputStatus epoch = history.snapshot(3, 3, 3, 2, true);
        OutputStatus stopped = history.snapshot(3, 3, 3, 2, false);
        check(local != sent && local.localMask == 3, "Local gate updates without waiting for a packet");
        check(request != local && request.requestedMask == 3, "Subscription mask updates immediately");
        check(effective != request && effective.effectiveMask == 3, "Effective mask updates immediately");
        check(epoch != effective && epoch.epoch == 2, "Session epoch updates immediately");
        check(stopped != epoch && !stopped.subscribed && sent.subscribed, "Unsubscribe updates without mutating prior snapshots");
    }
    public static void main(String[] args) {
        fields(); validity(); clocks(); packets(); publication();
        System.out.println("PASS: " + checks + " native-eye/full-packet inspector checks");
    }
}
