package dev.pico.facialprobe;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import static dev.pico.facialprobe.ExpressionStatus.Data.*;

public final class ExpressionStatusTest {
    private static int checks;
    private static final long NOW = 10_000_000_000L;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static byte[] frame(int version, long left, long right, long mouth, int updated, float value) {
        byte[] face = new byte[384]; ByteBuffer b = TrackingData.bytes(face);
        b.putLong(0, left);
        for (int i = 0; i < 52; i++) b.putFloat(8 + i * 4, value + i / 1000f);
        b.putFloat(296, 1); b.putFloat(300, 1);
        b.putFloat(216, .81f); b.putInt(220, 1); b.putInt(224, 1);
        if (version != 0) { b.putInt(228, 0x31495045); b.putInt(232, version); }
        b.putInt(236, updated); b.putLong(240, mouth); b.putLong(248, right);
        return face;
    }
    private static ExpressionStatus single(byte[] frame, int mask) {
        ExpressionStatus.Accumulator a = new ExpressionStatus.Accumulator();
        a.accept(Collections.singletonList(frame), mask); return a.snapshot();
    }
    private static void catalog() {
        check(ExpressionCatalog.NAMES.length == 53 && ExpressionCatalog.CHINESE.length == 53, "Bilingual 52+1 names");
        boolean[] seen = new boolean[53];
        int rows = 0, pairs = 0;
        for (ExpressionCatalog.Row[] section : ExpressionCatalog.GROUPS) for (ExpressionCatalog.Row row : section) {
            rows++; if (row.right >= 0) pairs++;
            for (int shape : new int[]{row.left, row.right}) if (shape >= 0) {
                check(!seen[shape], "No duplicate shape " + shape); seen[shape] = true;
            }
        }
        check(rows == 32 && pairs == 21, "53 slots become 32 rows with 21 bilateral pairs");
        check(ExpressionCatalog.GROUPS[0].length == 10 && ExpressionCatalog.GROUPS[1].length == 3, "Focused regions avoid long all-shape lists");
        check(ExpressionCatalog.GROUPS[0][0].matches("eyeblinkr"), "Searching a right-side field retains its pair");
        check(ExpressionCatalog.GROUPS[0][0].matches("闭眼"), "Chinese action-name search");
        check(!ExpressionCatalog.GROUPS[0][0].matches("jawopen"), "Search does not match unrelated rows");
        for (ExpressionCatalog.Row[] section : ExpressionCatalog.GROUPS) for (ExpressionCatalog.Row row : section)
            if (row.right >= 0) check(row.left == 3 || ExpressionCatalog.NAMES[row.left].endsWith("L")
                    || ExpressionCatalog.NAMES[row.left].endsWith("Left"), "Pair left slot is really left");
        for (boolean included : seen) check(included, "All source expressions appear exactly once");
        for (int shape : TrackingData.EYE_SHAPES) check(ExpressionStatus.group(shape) != 2, "Eye channel membership");
        for (int shape : TrackingData.FACE_ONLY_SHAPES) check(ExpressionStatus.group(shape) == 2, "Mouth channel membership");
        check(ExpressionStatus.group(52) == 1, "Right inner brow belongs to right group");
        check(ExpressionCatalog.NAMES[7].equals("JawOpen") && ExpressionCatalog.NAMES[51].equals("TongueOut"), "PICO source order");
    }
    private static void ordinary() {
        ExpressionStatus s = single(frame(0, NOW, 0, 0, 0, .25f), 3);
        check(s.data(7, NOW, 3, true) == LIVE, "Normal mouth live");
        check(s.data(28, NOW, 3, true) == LIVE && s.data(38, NOW, 3, true) == LIVE, "Both ordinary eyes live");
        check(Math.abs(s.value(7) - .257f) < .00001f, "Source value is not scaled or smoothed");
        check(s.data(52, NOW, 3, true) == UNSUPPORTED, "No fabricated right inner brow");
        check(s.data(7, NOW, 1, true) == OFF && s.data(28, NOW, 2, true) == OFF, "Local switches gate display");
        check(s.data(7, NOW, 3, false) == STOPPED, "Stopped service hides values");
        check(s.data(7, NOW + 2_000_000_000L, 3, true) == STALE, "Values expire without new publication");
        check(s.ageNanos(2, NOW + 100) == 100, "Age is based on source time");
        check(ExpressionStatus.empty().data(7, NOW, 3, false) == STOPPED, "Initial stopped state");
        check(single(frame(1, NOW, 0, 0, 0, .2f), 1).data(7, NOW, 3, true) == NOT_CAPTURED, "PC capture intersection visible");
    }
    private static void independent() {
        ExpressionStatus.Accumulator a = new ExpressionStatus.Accumulator();
        a.accept(Collections.singletonList(frame(2, NOW, NOW, NOW, 3, .1f)), 3);
        ExpressionStatus first = a.snapshot();
        check(first.data(28, NOW, 3, true) == LIVE && first.data(38, NOW, 3, true) == LIVE, "New eyes");
        check(first.data(7, NOW, 3, true) == WAITING, "Held unobserved mouth is not made live");
        check(first.value(52) == .81f, "Independent right brow read from EPI1");
        check(first.describe().contains("version=2") && first.describe().contains("mouthSourceNs=0 quality=-1"), "Source metadata is explicit before a mouth observation");
        check(first.describe().contains("JawOpen=not observed"), "Source detail does not invent unseen mouth values");
        a.accept(Collections.singletonList(frame(2, NOW + 5, NOW + 6, NOW + 7, 4, .4f)), 3);
        ExpressionStatus second = a.snapshot();
        check(second.data(7, NOW + 7, 3, true) == LIVE, "Mouth-only observation");
        check(second.value(28) == first.value(28), "Mouth update cannot replace held eyes");
        check(second.ageNanos(0, NOW + 10) == 10 && second.ageNanos(2, NOW + 10) == 3, "Group clocks remain independent");
        check(Float.isNaN(first.value(7)), "Published snapshot is immutable");
        a.accept(Collections.singletonList(frame(2, NOW + 5, NOW + 6, NOW + 8, 0, .9f)), 3);
        check(a.snapshot().value(7) == second.value(7), "No update bit cannot advance values");
        a.accept(Collections.singletonList(frame(2, NOW - 1, NOW - 1, NOW - 1, 7, .9f)), 3);
        check(a.snapshot().value(7) == second.value(7), "Old group identities cannot overwrite observations");
        byte[] invalid = frame(2, NOW + 20, NOW + 20, NOW + 20, 2, .9f);
        TrackingData.bytes(invalid).putInt(224, 0);
        a.accept(Collections.singletonList(invalid), 3);
        ExpressionStatus third = a.snapshot();
        check(third.data(38, NOW + 20, 3, true) == INVALID, "Invalid new right eye replaces old validity");
        check(third.data(28, NOW + 20, 3, true) == LIVE, "Right invalidation leaves left live");
        check(third.data(28, NOW + 2_000_000_001L, 3, true) == STALE, "Fast right updates cannot freshen left");
        a.accept(Collections.singletonList(frame(1, NOW + 30, 0, 0, 0, .2f)), 3);
        check(a.snapshot().version == 1 && a.snapshot().data(7, NOW + 30, 3, true) == LIVE, "v1 single clock supported");
        a.accept(Collections.singletonList(frame(2, NOW + 40, NOW + 40, 0, 3, .2f)), 3);
        check(a.snapshot().data(7, NOW + 40, 3, true) == WAITING, "Version changes clear old mouth");
    }
    private static void gatesAndSentinels() {
        ExpressionStatus.Accumulator a = new ExpressionStatus.Accumulator();
        a.accept(Collections.singletonList(frame(2, NOW, NOW, NOW, 7, .2f)), 3);
        a.accept(Collections.emptyList(), 1);
        a.accept(Collections.emptyList(), 3);
        check(a.snapshot().data(7, NOW, 3, true) == WAITING, "Re-enabled channel waits for a new observation");
        byte[] f = frame(2, NOW + 1, NOW + 1, NOW + 1, 7, .2f);
        TrackingData.bytes(f).putFloat(8 + 7 * 4, Float.NaN).putFloat(8 + 5 * 4, 1.4f);
        ExpressionStatus s = single(f, 3);
        check(s.data(7, NOW + 1, 3, true) == INVALID && s.data(5, NOW + 1, 3, true) == LIVE, "Nonfinite is per-shape invalid");
        check(s.value(5) == 1.4f, "Out-of-range source values remain unmodified");
        TrackingData.bytes(f).putFloat(216, Float.POSITIVE_INFINITY);
        s = single(f, 3);
        check(s.data(38, NOW + 1, 3, true) == INVALID && s.data(28, NOW + 1, 3, true) == LIVE, "Right brow sentinel respects wire quality");
        TrackingData.bytes(f).putFloat(300, 0);
        check(single(f, 3).data(5, NOW + 1, 3, true) == INVALID, "Invalid mouth quality");
        s = single(frame(2, NOW, NOW + 10, NOW + 20, 7, .1f), 3);
        check(s.data(28, NOW - 100_000_001L, 3, true) == STALE, "Future clock beyond tolerance is not live");
        a = new ExpressionStatus.Accumulator();
        a.accept(Arrays.asList(frame(2, NOW, NOW, 0, 3, .1f),
                frame(2, NOW, NOW, NOW + 1, 4, .3f)), 3);
        check(a.snapshot().data(28, NOW + 1, 3, true) == LIVE && a.snapshot().data(7, NOW + 1, 3, true) == LIVE,
                "All ring samples are consumed before publishing the latest snapshot");
    }
    private static void publication() {
        ExpressionStatus.Accumulator a = new ExpressionStatus.Accumulator();
        ExpressionStatus previous = a.snapshot();
        a.accept(Collections.emptyList(), 0);
        check(a.snapshot() == previous, "Idle polls reuse an immutable snapshot");
        for (int i = 0; i < 9; i++) {
            long time = NOW + i * 11_111_111L;
            byte[] sample = frame(2, time, time, time, 7, i / 10f);
            a.accept(Collections.singletonList(sample), 3);
            ExpressionStatus current = a.snapshot();
            check(current != previous && a.snapshot() == current, "Each new source immediately publishes once, without a 100ms gate");
            check(current.ageNanos(0, time) == 0 && current.ageNanos(2, time) == 0, "Publication keeps the actual group clocks");
            check(Math.abs(current.value(7) - (i / 10f + .007f)) < .00001f, "Latest expression is not interpolated");
            a.accept(Collections.singletonList(sample), 3);
            check(a.snapshot() == current, "A held observation does not recopy the snapshot");
            previous = current;
        }
        float held = previous.value(7);
        a.accept(Collections.singletonList(frame(2, NOW, NOW, NOW, 0, .99f)), 3);
        ExpressionStatus metadata = a.snapshot();
        check(metadata != previous && metadata.lastFrameUpdated == 0, "Update-bit metadata changes publish without inventing new observations");
        check(metadata.value(7) == held, "Metadata-only publication leaves real values unchanged");
        a.accept(Collections.emptyList(), 1);
        check(a.snapshot().captureMask == 1 && a.snapshot().data(7, NOW, 3, true) == NOT_CAPTURED,
                "Capture gate clears immediately without a new frame");
        check(previous.captureMask == 3 && previous.value(7) == held, "Retained/frozen snapshots never mutate");
    }
    public static void main(String[] args) {
        catalog(); ordinary(); independent(); gatesAndSentinels(); publication();
        System.out.println("PASS: " + checks + " expression monitor checks");
    }
}
