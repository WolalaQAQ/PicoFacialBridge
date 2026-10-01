package dev.pico.facialprobe;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Single-peer session and subscription state machine; payloads and source endpoint must match exactly. */
final class PeerProtocol {
    static final byte[] PING = new byte[]{'M','A','R','C','O',0};
    private static final byte[] DISCOVER = "DISCOVER_DAEMON".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] POLO = "POLO".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] STOP = "STOP".getBytes(StandardCharsets.US_ASCII);
    private static final String SUBSCRIBE = "PXR_SUB id=[0-9a-f]{16} mask=[0-3]";
    private String endpoint;
    private boolean awaiting;
    private boolean discovery;
    private boolean subscriptionReply;
    private long firstPing, nextPing;
    private String subscriptionId;
    private int requestedMask, effectiveMask = -1;
    private long epoch;

    /** No tracking datagram is sent before the PC has subscribed. */
    boolean subscribed() { return subscriptionId != null; }
    int requestedMask() { return requestedMask; }
    int effectiveMask(int local) { return local & requestedMask; }
    long epoch() { return epoch; }
    /** Advances the epoch whenever the effective channels change; true when the caller must reset. */
    boolean updateSelection(int local, long nowNanos) {
        if (!subscribed()) return false;
        int selected = effectiveMask(local);
        if (selected == effectiveMask) return false;
        effectiveMask = selected;
        epoch = Math.max(epoch + 1, nowNanos);
        return true;
    }
    byte[] acknowledgement() {
        return ("PXR_SUB_ACK id=" + subscriptionId + " mask=" + effectiveMask + " epoch="
                + String.format(java.util.Locale.ROOT, "%016x", epoch)).getBytes(StandardCharsets.US_ASCII);
    }

    boolean connected() { return endpoint != null; }
    boolean receive(String sender, byte[] bytes, long now) {
        if (endpoint != null && !endpoint.equals(sender)) return false;
        if (Arrays.equals(bytes, DISCOVER)) {
            endpoint = sender; awaiting = false; nextPing = now; discovery = true;
            clearSubscription();
            return true;
        }
        if (endpoint == null) return false;
        String control = new String(bytes, StandardCharsets.US_ASCII);
        if (control.matches(SUBSCRIBE)) {
            String id = control.substring(11, 27);
            int mask = control.charAt(33) - '0';
            // PC ownership/configuration is fixed until rediscovery; delayed control cannot
            // replace either the subscription identity or its demand within the same session.
            if (subscriptionId != null && (!subscriptionId.equals(id) || requestedMask != mask)) return false;
            if (subscriptionId == null) { subscriptionId = id; requestedMask = mask; effectiveMask = -1; }
            subscriptionReply = true;
            return true;
        }
        if (Arrays.equals(bytes, POLO)) { awaiting = false; nextPing = now + 25000; return true; }
        if (Arrays.equals(bytes, STOP)) { clear(); return true; }
        return false;
    }
    boolean shouldPing(long now) { return connected() && now >= nextPing; }
    void pingSent(long now) { if (!awaiting) firstPing = now; awaiting = true; nextPing = now + 5000; }
    boolean expired(long now) { return connected() && awaiting && now - firstPing >= 25000; }
    /** Every accepted discovery needs a mode reply, including a restarted PC at the same endpoint. */
    boolean takeDiscovery() { boolean result = discovery; discovery = false; return result; }
    boolean takeSubscriptionReply() { boolean result = subscriptionReply; subscriptionReply = false; return result; }
    private void clearSubscription() {
        subscriptionReply = false; subscriptionId = null; requestedMask = 0; effectiveMask = -1;
    }
    void clear() {
        endpoint = null; awaiting = false; discovery = false;
        clearSubscription();
    }
}
