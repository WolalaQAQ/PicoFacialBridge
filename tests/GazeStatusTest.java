package dev.pico.facialprobe;

/** Pure clock/state checks; no Android, sockets, processes or headset access. */
public final class GazeStatusTest {
    private static int checks;
    private static void check(boolean condition, String reason) {
        checks++; if (!condition) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        long nowMs = 5000, nowNs = 5_000_000_000L;
        GazeStatus live = new GazeStatus(GazeStatus.Link.SUBSCRIBED, "192.0.2.10:50001", "0123456789abcdef",
                7, nowMs + 3000, nowNs, true, "");
        check(live.connection(nowMs) == GazeStatus.Link.SUBSCRIBED, "Active subscription");
        check(live.leaseRemainingMs(nowMs + 1) == 2999, "Lease follows monotonic clock");
        check(live.data(true, nowMs, nowNs) == GazeStatus.Data.FRESH, "Fresh valid sample");
        check(live.data(false, nowMs, nowNs) == GazeStatus.Data.OFF, "Local switch gates the displayed output");
        check(live.data(true, nowMs + 150, nowNs + GazeSubscription.MAX_AGE_NS) == GazeStatus.Data.FRESH, "Exact freshness boundary");
        check(live.data(true, nowMs + 151, nowNs + GazeSubscription.MAX_AGE_NS + 1) == GazeStatus.Data.STALE, "Held sample ages out without a new snapshot");
        check(live.connection(nowMs + 3000) == GazeStatus.Link.EXPIRED, "Lease visibly expires even while producer blocks");
        check(live.leaseRemainingMs(nowMs + 4000) == 0, "Never show negative lease");
        check(live.data(true, nowMs + 3000, nowNs) == GazeStatus.Data.WAITING, "Expired subscription cannot display live delivery");
        check(live.data(true, nowMs, nowNs - 1) == GazeStatus.Data.STALE, "Do not display a future sample as fresh");
        GazeStatus invalid = new GazeStatus(GazeStatus.Link.SUBSCRIBED, live.endpoint, live.id, 7,
                nowMs + 3000, nowNs, false, "");
        check(invalid.data(true, nowMs, nowNs) == GazeStatus.Data.INVALID, "Invalid is distinct from stale");
        GazeStatus empty = new GazeStatus(GazeStatus.Link.SUBSCRIBED, live.endpoint, live.id, 8,
                nowMs + 3000, 0, false, "");
        check(empty.data(true, nowMs, nowNs) == GazeStatus.Data.WAITING, "New epoch waits for its own sample");
        check(empty.sourceAgeNanos(nowNs) == -1, "No fabricated source age");
        for (GazeStatus.Link link : new GazeStatus.Link[]{GazeStatus.Link.STOPPED, GazeStatus.Link.WAITING,
                GazeStatus.Link.UNSUBSCRIBED, GazeStatus.Link.EXPIRED, GazeStatus.Link.FAILED}) {
            check(GazeStatus.idle(link).connection(nowMs) == link, "Keep the actual disconnect reason");
            check(GazeStatus.idle(link).data(true, nowMs, nowNs) == GazeStatus.Data.WAITING, "Disconnected is not live");
        }
        check(live.sourceNanos == nowNs && live.leaseUntilMs == nowMs + 3000, "Reading status does not renew lease or source time");
        System.out.println("PASS: " + checks + " gaze dashboard state checks");
    }
}
