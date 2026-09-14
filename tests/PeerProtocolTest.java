package dev.pico.facialprobe;

import java.nio.charset.StandardCharsets;

public final class PeerProtocolTest {
    private static int checks;
    private static byte[] message(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Protocol check " + checks); }
    public static void main(String[] args) {
        PeerProtocol peer = new PeerProtocol();
        check(!peer.connected());
        check(!peer.receive("pc", message("WRONG"), 0));
        check(!peer.receive("pc", message("DISCOVER_DAEMONx"), 0));
        check(peer.receive("pc", message("DISCOVER_DAEMON"), 0));
        check(peer.connected()); check(peer.shouldPing(0));
        peer.pingSent(0); check(!peer.shouldPing(4999)); check(peer.shouldPing(5000));
        check(!peer.receive("stranger", message("POLO"), 1000));
        check(!peer.receive("stranger", message("STOP"), 1000));
        check(!peer.receive("stranger", message("DISCOVER_DAEMON"), 1000));
        check(peer.receive("pc", message("POLO"), 1000));
        check(!peer.shouldPing(25999)); check(peer.shouldPing(26000));
        peer.pingSent(26000); check(!peer.expired(50999)); check(peer.expired(51000));
        check(peer.receive("pc", message("STOP"), 27000)); check(!peer.connected());
        check(peer.receive("new", message("DISCOVER_DAEMON"), 28000));
        check(peer.receive("new", message("DISCOVER_DAEMON"), 29000)); check(peer.shouldPing(29000));
        peer.clear(); check(!peer.connected()); check(!peer.shouldPing(30000));
        check(PeerProtocol.PING.length == 6 && PeerProtocol.PING[5] == 0);
        System.out.println("PASS: " + checks + " UDP protocol checks");
    }
}
