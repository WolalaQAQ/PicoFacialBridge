package dev.pico.facialprobe;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Independent native-gaze consumer; never changes the legacy eye/face peer. */
final class GazeSubscription {
    static final long LEASE_MS = 3000;
    static final long MAX_AGE_NS = 150_000_000L;
    private String endpoint, id;
    private long lastRequest, epoch, sourceTime;
    private boolean enabled, reply, sourceValid;

    static boolean isControl(byte[] bytes) {
        return new String(bytes, StandardCharsets.US_ASCII).startsWith("PXR_GAZE_");
    }
    boolean connected() { return endpoint != null; }
    boolean wanted() { return connected() && enabled; }
    String endpoint() { return endpoint; }
    long epoch() { return epoch; }
    String subscriptionId() { return connected() ? id : ""; }
    long leaseUntilMs() { return connected() ? lastRequest + LEASE_MS : 0; }
    long sourceNanos() { return sourceTime; }
    boolean sourceValid() { return sourceValid; }
    int captureMask(int primaryMask) { return primaryMask | (wanted() ? 1 : 0); }

    boolean receive(String sender, byte[] bytes, long nowMs, long nowNanos) {
        expire(nowMs);
        String message = new String(bytes, StandardCharsets.US_ASCII);
        if (message.matches("PXR_GAZE_SUB id=[0-9a-f]{16}")) {
            String requestedId = message.substring("PXR_GAZE_SUB id=".length());
            if (connected() && !endpoint.equals(sender)) return false;
            if (!connected() || !requestedId.equals(id)) {
                endpoint = sender; id = requestedId;
                resetSource(nowNanos);
            }
            lastRequest = nowMs; reply = true;
            return true;
        }
        if (connected() && endpoint.equals(sender) && message.equals("PXR_GAZE_STOP id=" + id)) {
            clear();
            return true;
        }
        return false;
    }
    void updateSelection(boolean eyeEnabled, long nowNanos) {
        if (enabled == eyeEnabled) return;
        enabled = eyeEnabled;
        if (connected()) resetSource(nowNanos);
    }
    void resetSource(long nowNanos) {
        sourceTime = 0; sourceValid = false;
        if (connected()) { epoch = Math.max(epoch + 1, nowNanos); reply = true; }
    }
    boolean expire(long nowMs) {
        if (!connected() || nowMs - lastRequest < LEASE_MS) return false;
        clear();
        return true;
    }
    boolean takeReply() { boolean result = reply; reply = false; return result; }
    byte[] acknowledgement() {
        return ("PXR_GAZE_ACK id=" + id + " epoch=" + String.format(Locale.ROOT, "%016x", epoch)
                + " enabled=" + (enabled ? 1 : 0)).getBytes(StandardCharsets.US_ASCII);
    }
    byte[] next(List<byte[]> eyes, long nowNanos) {
        if (!wanted()) return null;
        byte[] latest = null;
        long latestTime = sourceTime;
        for (byte[] eye : eyes) {
            if (eye == null || eye.length < 200) continue;
            long time = TrackingData.eyeTimestamp(eye);
            if (time > latestTime && nowNanos >= time && nowNanos - time <= MAX_AGE_NS) {
                latest = eye; latestTime = time;
            }
        }
        if (latest == null) return null;
        sourceTime = latestTime;
        // Direction-only consumer: do not advertise the vendor's split fused
        // vectors as independent per-eye measurements. Invalidations are sent too.
        byte[] packet = TrackingData.eyePacket(latest, false, 1, epoch);
        sourceValid = (TrackingData.bytes(packet).getInt(27) & 2) != 0;
        return packet;
    }
    void clear() { endpoint = id = null; sourceTime = 0; reply = sourceValid = false; }
}
