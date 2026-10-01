package dev.pico.facialprobe;

import java.util.*;

public final class ForwardingTest {
    private static int checks;
    private static final long SECOND = 1_000_000_000L;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
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
                    rates.sent(now,packet[0] == 'E' ? TrackingData.eyeTimestamp(pump.eye()) : 0,
                            packet[0] == 'F' ? TrackingData.faceTimestamp(pump.face()) : 0);
                };
                pump.accept(Collections.singletonList(CadenceTest.eye(now,.1f)),Collections.singletonList(CadenceTest.face(now,.2f)),now,sendEye,sendFace,sink);
                pump.accept(Collections.singletonList(CadenceTest.eye(now+10_000_000,.3f)),Collections.singletonList(CadenceTest.face(now+20_000_000,.4f)),now+20_000_000,sendEye,sendFace,sink);
                int eyeReceived=0, faceReceived=0;
                while (true) {
                    java.net.DatagramPacket packet=new java.net.DatagramPacket(new byte[600],600);
                    try { receiver.receive(packet); } catch(java.net.SocketTimeoutException end) { break; }
                    byte tag=packet.getData()[0];
                    if (tag=='E') { eyeReceived++; check(packet.getLength()==83,"Eye datagram length"); }
                    else if (tag=='F' || tag=='A') { faceReceived++; check(packet.getLength()==(mask==1?95:mask==2?151:227),"Facial datagram length"); }
                    else throw new AssertionError("Unknown datagram tag "+tag);
                }
                check(eyeReceived==(sendEye?2:0) && faceReceived==((sendEye||sendFace)?2:0),"Loopback emits only the enabled streams: "+mask);
                check(rates.hz(now)[0]==(sendEye?2:0) && rates.hz(now)[1]==(sendFace?2:0),"Loopback transmitted per-source Hz: "+mask);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        long now = 10 * SECOND;
        byte[] eye = CadenceTest.eye(now, .7f), face = CadenceTest.face(now, .9f);
        TrackingData.bytes(face).putFloat(8 + 28 * 4, .4f); // left blink fallback lives in the facial frame
        TrackingData.bytes(face).putFloat(8 + 3 * 4, .3f);  // eyebrow also lives in the facial frame
        byte[] originalFace = face.clone(), originalEye = eye.clone();

        List<byte[]> support = new ArrayList<>();
        new FramePump().accept(Collections.singletonList(eye), Collections.singletonList(face),
                now, true, false, support::add);
        check(support.size() == 2 && support.get(1)[0] == 'A', "Eye-only retains low-rate eye shapes");
        checkEyeSupport(support.get(1));

        byte[] eyePacket = TrackingData.eyePacket(eye, false, 1, 1);
        check(eyePacket.length == 83 && eyePacket[0] == 'E', "Eye datagram framing");
        check(TrackingData.bytes(eyePacket).getFloat(55) == .7f, "Eye datagram carries the raw gaze");
        check(TrackingData.bytes(eyePacket).getFloat(67) == 0f, "Eye datagram does not carry facial blendshapes");
        byte[] facePacket = TrackingData.facePacket(face, 3, 1);
        check(facePacket.length == 227 && facePacket[0] == 'F', "Facial datagram framing");
        check(TrackingData.bytes(facePacket).getFloat(19 + 7 * 4) == .9f, "Facial datagram carries the raw jaw");
        check(TrackingData.bytes(facePacket).getFloat(19 + 28 * 4) == .4f, "Facial datagram carries the blink fallback blendshape");
        check(TrackingData.bytes(facePacket).getFloat(19 + 3 * 4) == .3f, "Facial datagram carries the eyebrow blendshape");
        check(facePacket[2] == 3, "Facial datagram carries both video-validity flags");
        check(Arrays.equals(face, originalFace) && Arrays.equals(eye, originalEye), "Building datagrams must not mutate source samples");

        FramePump pump = new FramePump(); List<byte[]> packets = new ArrayList<>();
        pump.accept(Collections.singletonList(eye),Collections.emptyList(),now,true,false,packets::add);
        check(packets.size() == 1 && packets.get(0)[0] == 'E', "Eye-only does not wait for missing face");
        pump.accept(Collections.emptyList(),Collections.singletonList(face),now,true,false,packets::add);
        check(packets.size() == 2 && packets.get(1)[0] == 'A', "Eye-only sends low-rate eye support, not mouth output");
        pump.accept(Collections.emptyList(),Collections.singletonList(CadenceTest.face(now+1,.5f)),now+1,false,true,packets::add);
        check(packets.size() == 3 && packets.get(2)[0] == 'F', "Hot switch to face-only is independent of the eye stream");
        pump.accept(Collections.singletonList(CadenceTest.eye(now+2,.2f)),Collections.singletonList(CadenceTest.face(now+2,.6f)),now+2,false,false,packets::add);
        check(packets.size() == 3, "Both disabled emit no data datagrams");
        pump.accept(Collections.singletonList(CadenceTest.eye(now+3,.3f)),Collections.emptyList(),now+3,true,true,packets::add);
        check(packets.size() == 4, "Re-enable restores new frames without restart");
        FramePump missing = new FramePump();
        missing.accept(Collections.emptyList(),Collections.singletonList(face),now,false,true,packets::add);
        check(packets.size() == 5, "Fresh face can start without eye");
        missing.accept(Collections.singletonList(eye),Collections.emptyList(),now+3*SECOND,true,false,packets::add);
        check(packets.size() == 5, "Stale enabled source emits nothing");

        byte[] normalEye = CadenceTest.eye(now + 4, .1f); TrackingData.bytes(normalEye).putInt(0, 0x984);
        FramePump normalPump = new FramePump(); List<byte[]> normalPackets = new ArrayList<>();
        normalPump.accept(Collections.singletonList(normalEye), Collections.emptyList(), now + 4, true, false, normalPackets::add);
        check(!normalPackets.isEmpty() && TrackingData.bytes(normalPackets.get(0)).getInt(19) == 0x804,
                "Normal mode strips the vendor's fixed-depth per-eye split");
        byte[] dualEye = CadenceTest.eye(now + 5, .1f); TrackingData.bytes(dualEye).putInt(0, 0x984);
        FramePump dualPump = new FramePump(); dualPump.keepPerEyeGaze = true; List<byte[]> dualPackets = new ArrayList<>();
        dualPump.accept(Collections.singletonList(dualEye), Collections.emptyList(), now + 5, true, false, dualPackets::add);
        check(!dualPackets.isEmpty() && TrackingData.bytes(dualPackets.get(0)).getInt(19) == 0x904,
                "Dual enhance keeps real per-eye validity on the wire");

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
                if (packet[0] == 'E') throw new java.io.IOException("simulated send failure");
                failed.sent(now,now,now);
            });
            throw new AssertionError("Missing send failure");
        } catch (java.io.IOException expected) { check(failed.hz(now)[0] == 0, "Failed send cannot count as transmitted Hz"); }
        loopback();
        System.out.println("PASS: " + checks + " selection/split-forwarding/transmitted-Hz/UDP-loopback checks");
    }
    private static void checkEyeSupport(byte[] packet) {
        // Eye auxiliary slot order: EyeBlinkL (28) is index 8, BrowInnerUp (3) is index 2.
        check(TrackingData.bytes(packet).getFloat(19 + 8 * 4) == .4f, "Eye-only retains raw blink");
        check(TrackingData.bytes(packet).getFloat(19 + 2 * 4) == .3f, "Eye-only retains raw brow");
        check((packet[2] & 1) != 0, "Eye-only retains eye validity");
        check((packet[2] & 2) == 0, "Eye-only cannot enable facial output");
        check(packet.length == 95, "Eye-only strips jaw data");
    }
}
