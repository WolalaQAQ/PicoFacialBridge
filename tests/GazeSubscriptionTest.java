package dev.pico.facialprobe;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public final class GazeSubscriptionTest {
    private static int checks;
    private static final String ID = "0123456789abcdef";
    private static final String SUB = "PXR_GAZE_SUB id=" + ID;
    private static final long SECOND = 1_000_000_000L;
    private static byte[] message(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private static void check(boolean value, String reason) {
        checks++; if (!value) throw new AssertionError(reason);
    }
    static byte[] eye(long time, float x, boolean valid) {
        byte[] eye = new byte[200];
        java.nio.ByteBuffer b = TrackingData.bytes(eye);
        b.putInt(0, 0x904); b.putInt(4, 0x904); b.putInt(8, valid ? 2 : 0);
        b.putFloat(48, x); b.putFloat(56, -1f);
        b.putFloat(60, x); b.putFloat(68, -1f);
        b.putFloat(72, x); b.putFloat(80, -1f);
        b.putFloat(84, 1f); b.putFloat(88, 1f); b.putFloat(92, 35f); b.putFloat(96, 35f);
        b.putLong(168, time);
        return eye;
    }
    static byte[] face(long time) {
        byte[] face = new byte[384];
        TrackingData.bytes(face).putLong(0, time).putFloat(296, 1f).putFloat(300, 1f);
        return face;
    }
    public static void main(String[] args) throws Exception {
        GazeSubscription gaze = new GazeSubscription();
        PeerProtocol primary = new PeerProtocol();
        check(!gaze.connected() && gaze.captureMask(0) == 0, "No subscriber creates no gaze demand");
        check(GazeSubscription.isControl(message(SUB)), "Separate command namespace");
        check(!GazeSubscription.isControl(message("DISCOVER_DAEMON")), "Discovery remains the primary namespace");
        check(!gaze.receive("gaze", message("STOP"), 0, SECOND), "Legacy STOP is not a gaze stop");
        gaze.updateSelection(true, SECOND);
        check(!gaze.receive("gaze", message(SUB + "x"), 0, SECOND), "Reject malformed subscription");
        check(!gaze.receive("gaze", message("PXR_GAZE_SUB id=ABCDEF0123456789"), 0, SECOND), "Canonical subscription ID");
        check(gaze.receive("gaze", message(SUB), 0, SECOND), "Direct subscription without primary client");
        check(gaze.connected() && gaze.wanted() && gaze.captureMask(0) == 1, "Standalone native-eye demand");
        check(gaze.captureMask(2) == 3, "Face-only primary plus gaze requests native eyes too");
        long epoch = gaze.epoch();
        check(new String(gaze.acknowledgement(), StandardCharsets.US_ASCII).equals(
                "PXR_GAZE_ACK id=" + ID + " epoch=000000003b9aca00 enabled=1"), "Exact acknowledgement layout");
        check(gaze.takeReply() && !gaze.takeReply(), "Acknowledge once per control update");
        check(!gaze.receive("other", message(SUB), 1, SECOND), "Other endpoint cannot steal active gaze subscription");
        check(primary.receive("vrcft", message("DISCOVER_DAEMON"), 0), "Primary discovery still works");
        check(primary.receive("vrcft", message("PXR_SUB id=aaaaaaaaaaaaaaaa mask=3"), 0), "Primary subscription unchanged");
        primary.updateSelection(3, SECOND);
        long primaryEpoch = primary.epoch();
        check(!primary.receive("gaze", message(SUB), 0), "Independent gaze endpoint is not the avatar owner");

        byte[] nativeEye = eye(SECOND, .2f, true), original = nativeEye.clone();
        byte[] packet = gaze.next(Collections.singletonList(nativeEye), SECOND);
        check(packet != null && packet.length == 83 && packet[0] == 'E' && packet[1] == 1 && packet[2] == 0, "Native E framing only");
        check(TrackingData.bytes(packet).getLong(3) == epoch && TrackingData.bytes(packet).getLong(11) == SECOND, "Gaze epoch and original source time");
        check(TrackingData.bytes(packet).getFloat(55) == .2f && (TrackingData.bytes(packet).getInt(27) & 2) != 0, "Combined native ray retained");
        check((TrackingData.bytes(packet).getInt(19) & 0x100) == 0, "No claim of independent per-eye native measurements");
        check(Arrays.equals(nativeEye, original), "Do not modify the shared source frame");
        check(gaze.next(Collections.singletonList(nativeEye), SECOND + 1) == null, "Do not resend a held source");
        check(gaze.receive("gaze", message(SUB), 100, SECOND + 100_000_000), "Renew active subscription");
        check(gaze.epoch() == epoch && gaze.next(Collections.singletonList(nativeEye), SECOND + 1) == null, "Renewal changes neither epoch nor deduplication");

        byte[] newer = eye(SECOND + 20, .3f, true), newestInvalid = eye(SECOND + 30, .4f, false);
        packet = gaze.next(Arrays.asList(newestInvalid, nativeEye, newer), SECOND + 40);
        check(packet != null && TrackingData.bytes(packet).getLong(11) == SECOND + 30, "Pick newest source, not ring-history playback");
        check(TrackingData.bytes(packet).getInt(27) == 0, "Newest invalidation must not be hidden by an older good ray");
        check(gaze.next(Collections.singletonList(newer), SECOND + 41) == null, "Ignore a backward source");
        check(gaze.next(Collections.emptyList(), SECOND + 42) == null, "No input is not a new frame");
        check(gaze.next(Collections.singletonList(eye(SECOND + 50, 0, true)), SECOND + 50 + GazeSubscription.MAX_AGE_NS + 1) == null,
                "Reject samples older than the native-gaze latency budget");
        check(gaze.next(Collections.singletonList(eye(SECOND + 50, 0, true)), SECOND + 50 + GazeSubscription.MAX_AGE_NS) != null,
                "Accept the exact freshness boundary");
        check(gaze.next(Collections.singletonList(eye(2 * SECOND, 0, true)), SECOND) == null, "Do not forward future-dated input");

        FramePump pump = new FramePump(); pump.epoch = primaryEpoch;
        ArrayList<byte[]> avatarPackets = new ArrayList<>();
        pump.accept(Collections.singletonList(nativeEye), Collections.singletonList(face(SECOND)), SECOND, true, true, avatarPackets::add);
        check(avatarPackets.size() == 2 && avatarPackets.get(0)[0] == 'E' && avatarPackets.get(1)[0] == 'F', "Original avatar channels still emit E/F");
        check(TrackingData.bytes(avatarPackets.get(0)).getLong(3) == primaryEpoch && avatarPackets.get(1).length == 227,
                "Avatar keeps its own epoch and packet layout");

        gaze.updateSelection(false, 2 * SECOND);
        check(gaze.connected() && !gaze.wanted() && gaze.captureMask(2) == 2, "Local eye switch stops only gaze demand");
        check(gaze.epoch() > epoch && gaze.takeReply(), "Local eye switch advances only gaze epoch");
        check(new String(gaze.acknowledgement(), StandardCharsets.US_ASCII).endsWith("enabled=0"), "Report disabled subscription");
        check(gaze.next(Collections.singletonList(eye(2 * SECOND, 0, true)), 2 * SECOND) == null, "Eye off emits no gaze");
        check(primary.connected() && primary.epoch() == primaryEpoch, "Gaze control does not edit the avatar session");
        gaze.updateSelection(true, 3 * SECOND);
        check(gaze.wanted() && gaze.takeReply(), "Eye switch can recover without resubscribing");
        check(gaze.next(Collections.singletonList(eye(3 * SECOND, 0, true)), 3 * SECOND) != null, "New native samples resume");

        check(primary.receive("vrcft", message("STOP"), 200), "Original primary STOP works");
        check(!primary.connected() && gaze.connected() && gaze.captureMask(0) == 1, "Avatar STOP does not interrupt direct gaze");
        check(!gaze.receive("gaze", message("PXR_GAZE_STOP id=aaaaaaaaaaaaaaaa"), 201, 3 * SECOND), "A stale ID cannot unsubscribe gaze");
        check(!gaze.receive("other", message("PXR_GAZE_STOP id=" + ID), 202, 3 * SECOND), "A different endpoint cannot unsubscribe gaze");
        check(!gaze.expire(3099) && gaze.expire(3100), "Lease expires exactly three seconds after renewal");
        check(!gaze.connected() && gaze.captureMask(0) == 0, "Expired consumer leaves no native demand");
        check(gaze.receive("new", message(SUB), 3101, 4 * SECOND), "An expired slot can be acquired by a new endpoint");
        check(gaze.epoch() > epoch, "New owner gets a new epoch");
        check(primary.receive("vrcft", message("DISCOVER_DAEMON"), 3101), "Avatar can return independently");
        check(gaze.receive("new", message("PXR_GAZE_STOP id=" + ID), 3102, 4 * SECOND), "Dedicated gaze STOP");
        check(primary.connected() && !gaze.connected(), "Gaze STOP leaves the avatar owner connected");
        long now = 12000, staleEye = 1000, freshMorphology = now;
        check(!StreamHealth.reconnectDue(true, true, now, StreamHealth.requiredFreshness(2, true, staleEye, freshMorphology)),
                "Auxiliary native-eye stall cannot reconnect a healthy face-only avatar");
        check(StreamHealth.requiredFreshness(2, true, staleEye, freshMorphology)
                        == StreamHealth.requiredFreshness(2, false, staleEye, freshMorphology),
                "Adding gaze does not tighten the primary face-only health requirement");
        check(StreamHealth.reconnectDue(true, true, now, StreamHealth.requiredFreshness(1, true, staleEye, freshMorphology)),
                "Primary eye subscriber retains its own native-eye health requirement");
        check(StreamHealth.reconnectDue(true, true, now, StreamHealth.requiredFreshness(0, true, staleEye, freshMorphology)),
                "Standalone gaze can reconnect its own stalled native source");
        check(!StreamHealth.reconnectDue(true, true, now, StreamHealth.requiredFreshness(0, true, now, 0)),
                "Standalone gaze does not wait for morphology updates");
        check(StreamHealth.reconnectDue(true, false, now, StreamHealth.requiredFreshness(2, true, now, now)),
                "Actual shared Binder failure still permits recovery");
        System.out.println("PASS: " + checks + " direct-gaze subscription/coexistence/freshness checks");
    }
}
