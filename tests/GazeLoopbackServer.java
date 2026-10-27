package dev.pico.facialprobe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Synthetic host fixture using the production subscription and framing classes. */
public final class GazeLoopbackServer {
    private static void send(DatagramSocket socket, byte[] bytes, InetSocketAddress endpoint) throws java.io.IOException {
        socket.send(new DatagramPacket(bytes, bytes.length, endpoint));
    }
    public static void main(String[] args) throws Exception {
        ConcurrentLinkedQueue<String> commands = new ConcurrentLinkedQueue<>();
        Thread input = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));
                String line;
                while ((line = reader.readLine()) != null) commands.add(line);
            } catch (java.io.IOException error) { commands.add("quit"); }
        }, "fixture-input");
        input.setDaemon(true); input.start();
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))) {
            socket.setSoTimeout(2);
            PeerProtocol primary = new PeerProtocol();
            GazeSubscription gaze = new GazeSubscription();
            InetSocketAddress primaryEndpoint = null, gazeEndpoint = null;
            FramePump pump = new FramePump();
            int local = 3;
            boolean running = true, valid = true;
            long nextEye = 0, nextFace = 0;
            long end = System.nanoTime() + 30_000_000_000L;
            System.out.println("SERVER_READY port=" + socket.getLocalPort());
            System.out.flush();
            while (running && System.nanoTime() < end) {
                String command;
                while ((command = commands.poll()) != null) {
                    if (command.equals("quit")) running = false;
                    else if (command.equals("eye-off")) local = 2;
                    else if (command.equals("eye-on")) local = 3;
                    else if (command.equals("invalid")) valid = false;
                    else if (command.equals("valid")) valid = true;
                    if (command.equals("state"))
                        System.out.println("STATE primary=" + primary.connected() + " gaze=" + gaze.connected());
                    else System.out.println("CONTROL " + command);
                    System.out.flush();
                }
                long nano = System.nanoTime(), now = nano / 1_000_000L;
                gaze.updateSelection((local & 1) != 0, nano);
                DatagramPacket incoming = new DatagramPacket(new byte[128], 128);
                try {
                    socket.receive(incoming);
                    InetSocketAddress sender = (InetSocketAddress)incoming.getSocketAddress();
                    byte[] bytes = Arrays.copyOf(incoming.getData(), incoming.getLength());
                    if (GazeSubscription.isControl(bytes)) {
                        if (gaze.receive(sender.toString(), bytes, now, nano)) gazeEndpoint = gaze.connected() ? sender : null;
                    } else if (primary.receive(sender.toString(), bytes, now)) {
                        primaryEndpoint = primary.connected() ? sender : null;
                        if (primary.takeDiscovery()) {
                            pump = new FramePump();
                            send(socket, "PXR_MODE mode=normal rooted=0 enhance=0 gate=0 plugin=off".getBytes(StandardCharsets.US_ASCII), sender);
                        }
                    }
                } catch (SocketTimeoutException expected) { /* bounded fixture poll */ }
                if (gaze.expire(now) || !gaze.connected()) gazeEndpoint = null;
                boolean selectionChanged = primary.updateSelection(local, nano);
                if ((primary.takeSubscriptionReply() || selectionChanged) && primaryEndpoint != null)
                    send(socket, primary.acknowledgement(), primaryEndpoint);
                if (primary.shouldPing(now) && primaryEndpoint != null) {
                    send(socket, PeerProtocol.PING, primaryEndpoint); primary.pingSent(now);
                }
                if (gaze.takeReply() && gazeEndpoint != null) send(socket, gaze.acknowledgement(), gazeEndpoint);
                int selected = primary.effectiveMask(local);
                int capture = gaze.captureMask(primary.connected() ? selected : 0);
                if (capture == 0 || nano < nextEye) continue;
                nextEye = nano + 10_000_000L;
                List<byte[]> eyes = Collections.singletonList(GazeSubscriptionTest.eye(nano, .2f, valid));
                List<byte[]> faces = selected != 0 && nano >= nextFace
                        ? Collections.singletonList(GazeSubscriptionTest.face(nano)) : Collections.emptyList();
                if (!faces.isEmpty()) nextFace = nano + 40_000_000L;
                if (gazeEndpoint != null) {
                    byte[] packet = gaze.next(eyes, nano);
                    if (packet != null) send(socket, packet, gazeEndpoint);
                }
                pump.epoch = primary.epoch();
                final InetSocketAddress destination = primaryEndpoint;
                pump.accept(eyes, faces, nano, (selected & 1) != 0, (selected & 2) != 0,
                        bytes -> { if (destination != null) send(socket, bytes, destination); });
            }
        }
    }
}
