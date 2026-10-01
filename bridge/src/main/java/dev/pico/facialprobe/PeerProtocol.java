package dev.pico.facialprobe;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Single-peer compatibility state machine; payloads and source endpoint must match exactly. */
final class PeerProtocol {
    static final byte[] PING = new byte[]{'M','A','R','C','O',0};
    private static final byte[] DISCOVER = "DISCOVER_DAEMON".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] POLO = "POLO".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] STOP = "STOP".getBytes(StandardCharsets.US_ASCII);
    private String endpoint;
    private boolean awaiting;
    private boolean discovery;
    private long firstPing, nextPing;

    boolean connected() { return endpoint != null; }
    boolean receive(String sender, byte[] bytes, long now) {
        if (endpoint != null && !endpoint.equals(sender)) return false;
        if (Arrays.equals(bytes, DISCOVER)) { endpoint = sender; awaiting = false; nextPing = now; discovery = true; return true; }
        if (endpoint == null) return false;
        if (Arrays.equals(bytes, POLO)) { awaiting = false; nextPing = now + 25000; return true; }
        if (Arrays.equals(bytes, STOP)) { clear(); return true; }
        return false;
    }
    boolean shouldPing(long now) { return connected() && now >= nextPing; }
    void pingSent(long now) { if (!awaiting) firstPing = now; awaiting = true; nextPing = now + 5000; }
    boolean expired(long now) { return connected() && awaiting && now - firstPing >= 25000; }
    /** Every accepted discovery needs a mode reply, including a restarted PC at the same endpoint. */
    boolean takeDiscovery() { boolean result = discovery; discovery = false; return result; }
    void clear() { endpoint = null; awaiting = false; discovery = false; }
}
