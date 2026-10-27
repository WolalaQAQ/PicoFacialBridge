package dev.pico.facialprobe;

/** Immutable, read-only UI state; displaying it never renews a subscription. */
final class GazeStatus {
    enum Link { STOPPED, WAITING, SUBSCRIBED, UNSUBSCRIBED, EXPIRED, FAILED }
    enum Data { OFF, WAITING, FRESH, INVALID, STALE }
    final Link link;
    final String endpoint, id, error;
    final long epoch, leaseUntilMs, sourceNanos;
    final boolean valid;

    GazeStatus(Link link, String endpoint, String id, long epoch, long leaseUntilMs,
               long sourceNanos, boolean valid, String error) {
        this.link = link; this.endpoint = endpoint; this.id = id; this.epoch = epoch;
        this.leaseUntilMs = leaseUntilMs; this.sourceNanos = sourceNanos;
        this.valid = valid; this.error = error;
    }
    static GazeStatus idle(Link link) { return new GazeStatus(link, "", "", 0, 0, 0, false, ""); }
    long leaseRemainingMs(long nowMs) { return Math.max(0, leaseUntilMs - nowMs); }
    Link connection(long nowMs) {
        return link == Link.SUBSCRIBED && leaseRemainingMs(nowMs) == 0 ? Link.EXPIRED : link;
    }
    long sourceAgeNanos(long nowNanos) {
        return sourceNanos == 0 || nowNanos < sourceNanos ? -1 : nowNanos - sourceNanos;
    }
    Data data(boolean eyeEnabled, long nowMs, long nowNanos) {
        if (!eyeEnabled) return Data.OFF;
        if (connection(nowMs) != Link.SUBSCRIBED || sourceNanos == 0) return Data.WAITING;
        long age = sourceAgeNanos(nowNanos);
        if (age < 0 || age > GazeSubscription.MAX_AGE_NS) return Data.STALE;
        return valid ? Data.FRESH : Data.INVALID;
    }
}
