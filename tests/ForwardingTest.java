package dev.pico.facialprobe;

import java.util.*;

public final class ForwardingTest {
    private static int checks;
    private static final long SECOND = 1_000_000_000L;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static boolean zero(byte[] data, int from, int to) {
        for (int i = from; i < to; i++) if (data[i] != 0) return false;
        return true;
    }
    private static void loopback() throws Exception {
        try (java.net.DatagramSocket receiver = new java.net.DatagramSocket(0, java.net.InetAddress.getLoopbackAddress());
             java.net.DatagramSocket sender = new java.net.DatagramSocket()) {
            receiver.setSoTimeout(100);
            for (int mask=0; mask<4; mask++) {
                final boolean sendEye=(mask&1)!=0, sendFace=(mask&2)!=0;
                long now=10*SECOND;
                FramePump pump=new FramePump(); TransmissionRates rates=new TransmissionRates();
                FramePump.Sink sink=packet -> {
                    sender.send(new java.net.DatagramPacket(packet,packet.length,receiver.getLocalSocketAddress()));
                    rates.sent(now,sendEye?TrackingData.eyeTimestamp(pump.eye()):0,
                            sendFace?TrackingData.faceTimestamp(pump.face()):0);
                };
                pump.accept(Collections.singletonList(CadenceTest.eye(now,.1f)),Collections.singletonList(CadenceTest.face(now,.2f)),now,sendEye,sendFace,sink);
                pump.accept(Collections.singletonList(CadenceTest.eye(now+10_000_000,.3f)),Collections.singletonList(CadenceTest.face(now+20_000_000,.4f)),now+20_000_000,sendEye,sendFace,sink);
                int received=0;
                while (true) {
                    java.net.DatagramPacket packet=new java.net.DatagramPacket(new byte[600],600);
                    try { receiver.receive(packet); } catch(java.net.SocketTimeoutException end) { break; }
                    received++;
                    check(packet.getLength()==536,"Loopback preserves exact legacy framing");
                    check(sendEye || zero(packet.getData(),380,536),"Loopback disabled eye has no wire payload");
                    check(sendFace || TrackingData.bytes(packet.getData()).getFloat(36)==0,"Loopback disabled face has no jaw payload");
                }
                check(received==(mask==0?0:mask==3?3:2),"Loopback mode packet count: "+mask);
                check(rates.hz(now)[0]==(sendEye?2:0) && rates.hz(now)[1]==(sendFace?2:0),"Loopback transmitted per-source Hz: "+mask);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        long now = 10 * SECOND;
        byte[] eye = CadenceTest.eye(now, .7f), face = CadenceTest.face(now, .9f);
        TrackingData.bytes(face).putFloat(8 + 28 * 4, .4f); // left blink belongs to eye output
        TrackingData.bytes(face).putFloat(8 + 3 * 4, .3f); // eyebrow belongs to eye output
        byte[] originalFace = face.clone(), originalEye = eye.clone();
        byte[] both = TrackingData.packet(face, eye, now, true, true);
        check(Arrays.equals(both, TrackingData.packet(face, eye)), "Both enabled preserve the original wire bytes");
        byte[] eyeOnly = TrackingData.packet(face, eye, now, true, false);
        check(eyeOnly.length == 536, "Single stream preserves packet length");
        check(TrackingData.bytes(eyeOnly).getFloat(36) == 0, "Eye only must not leak jaw data");
        check(TrackingData.bytes(eyeOnly).getFloat(120) == .4f, "Eye only keeps raw blink fallback");
        check(TrackingData.bytes(eyeOnly).getFloat(20) == .3f, "Eye only keeps eyebrow expressions used by eye parser");
        check(TrackingData.bytes(eyeOnly).getFloat(296) == 1 && TrackingData.bytes(eyeOnly).getFloat(300) == 0,
                "Eye parser enabled, face parser disabled");
        check(zero(eyeOnly, 300, 384), "No face validity, emotions or padding in eye-only packet");
        check(Arrays.equals(Arrays.copyOfRange(eyeOnly,384,536), Arrays.copyOf(eye,152)), "Raw gaze unchanged");
        byte[] faceOnly = TrackingData.packet(face, eye, now, false, true);
        check(zero(faceOnly, 384, 536), "Disabled eye prefix is zero, not last-known gaze");
        check(TrackingData.bytes(faceOnly).getFloat(120) == 0 && TrackingData.bytes(faceOnly).getFloat(20) == 0,
                "Disabled eye blendshapes are removed from the face prefix too");
        check(TrackingData.bytes(faceOnly).getFloat(296) == 0 && TrackingData.bytes(faceOnly).getFloat(300) == 1,
                "Eye parser disabled, face parser enabled");
        check(TrackingData.bytes(faceOnly).getFloat(36) == .9f, "Face only keeps raw jaw");
        check(Arrays.equals(face, originalFace) && Arrays.equals(eye, originalEye), "Masking must not mutate source samples");
        byte[] paddedFace = face.clone(); Arrays.fill(paddedFace, 380, 384, (byte)0x55);
        check(zero(TrackingData.packet(paddedFace, eye, now, false, true),380,536),
                "The PC eye struct begins at 380: disabled eye must also clear native padding");
        byte[] solo = TrackingData.packet(null, eye, now, true, false);
        check(TrackingData.bytes(solo).getFloat(296) == 1, "Eye-only works even before first facial sample");
        check(TrackingData.bytes(TrackingData.packet(face, null, now, false, true)).getFloat(300) == 1,
                "Face-only works without any eye sample");
        check(TrackingData.bytes(TrackingData.packet(face, eye, now + 3*SECOND, true, false)).getFloat(296) == 0,
                "Stale eye data cannot be marked valid");
        check(zero(TrackingData.packet(face,eye,now,false,false),0,536), "Both disabled remove all data");

        FramePump pump = new FramePump(); List<byte[]> packets = new ArrayList<>();
        pump.accept(Collections.singletonList(eye),Collections.emptyList(),now,true,false,packets::add);
        check(packets.size() == 1, "Eye-only does not wait for missing face");
        pump.accept(Collections.emptyList(),Collections.singletonList(face),now,true,false,packets::add);
        check(packets.size() == 1, "Disabled facial source cannot trigger packets");
        pump.accept(Collections.emptyList(),Collections.singletonList(CadenceTest.face(now+1,.5f)),now+1,false,true,packets::add);
        check(packets.size() == 2 && zero(packets.get(1),384,536), "Hot switch to face-only masks held eye");
        pump.accept(Collections.singletonList(CadenceTest.eye(now+2,.2f)),Collections.singletonList(CadenceTest.face(now+2,.6f)),now+2,false,false,packets::add);
        check(packets.size() == 2, "Both disabled emit no data packets");
        pump.accept(Collections.singletonList(CadenceTest.eye(now+3,.3f)),Collections.emptyList(),now+3,true,true,packets::add);
        check(packets.size() == 3, "Re-enable restores new frames without restart");
        FramePump missing = new FramePump();
        missing.accept(Collections.emptyList(),Collections.singletonList(face),now,false,true,packets::add);
        check(packets.size() == 4, "Fresh face can start without eye");
        missing.accept(Collections.singletonList(eye),Collections.emptyList(),now+3*SECOND,true,false,packets::add);
        check(packets.size() == 4, "Stale enabled source emits nothing");

        TransmissionRates rates = new TransmissionRates();
        check(rates.hz(now)[0] == 0 && rates.hz(now)[1] == 0, "No successful sends means zero Hz");
        for (int i=0;i<90;i++) rates.sent(now+i*10_000_000L, now+i, i%4==0?now+i:0);
        check(rates.hz(now+900_000_000L)[0] == 90 && rates.hz(now+900_000_000L)[1] == 23, "Independent actual source-update rates");
        rates.sent(now+910_000_000L,now+89,now+88);
        check(rates.hz(now+920_000_000L)[0] == 90 && rates.hz(now+920_000_000L)[1] == 23, "Held samples do not inflate Hz");
        check(rates.hz(now+2*SECOND)[0] == 0 && rates.hz(now+2*SECOND)[1] == 0, "Idle/disconnected rates decay to zero");
        rates.reset(); rates.sent(now, now, 0);
        check(rates.hz(now)[0] == 1 && rates.hz(now)[1] == 0, "Disabled channel remains zero");
        check(rates.hz(now+SECOND)[0] == 0, "One-second rolling window boundary");
        rates.reset();
        check(rates.hz(now)[0] == 0, "Stop/settings change reset rates immediately");
        TransmissionRates failed = new TransmissionRates();
        try {
            new FramePump().accept(Collections.singletonList(eye),Collections.singletonList(face),now,true,true,packet -> {
                if (packet.length == 536) throw new java.io.IOException("simulated send failure");
                failed.sent(now,now,now);
            });
            throw new AssertionError("Missing send failure");
        } catch (java.io.IOException expected) { check(failed.hz(now)[0] == 0, "Failed send cannot count as transmitted Hz"); }
        loopback();
        System.out.println("PASS: " + checks + " selection/masking/transmitted-Hz/UDP-loopback checks");
    }
}
