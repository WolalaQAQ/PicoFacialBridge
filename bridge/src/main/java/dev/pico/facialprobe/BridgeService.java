package dev.pico.facialprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BridgeService extends Service {
    static final String START = "dev.pico.bridge.START", STOP = "dev.pico.bridge.STOP", RESTART = "dev.pico.bridge.RESTART";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancel = new AtomicBoolean();
    private boolean wanted, working, destroyed;
    private SharedPreferences prefs;
    private volatile int transmitMask = 3;
    private final Object transmissionLock = new Object();
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsChanged = (preferences, key) -> {
        if (BridgePreferences.EYE.equals(key) || BridgePreferences.FACE.equals(key)) {
            synchronized (transmissionLock) {
                transmitMask = BridgePreferences.selection(preferences);
                BridgeState.rates.reset();
            }
            BridgeState.log("TRANSMISSION eye=" + ((transmitMask & 1) != 0) + " face=" + ((transmitMask & 2) != 0));
        } else if (BridgePreferences.LANGUAGE.equals(key)) {
            updateChannel();
            if (wanted || working) getSystemService(NotificationManager.class).notify(1, notification());
        }
    };
    private PowerManager.WakeLock wake;
    private WifiManager.MulticastLock multicast;

    @Override public void onCreate() {
        super.onCreate();
        prefs = BridgePreferences.get(this);
        transmitMask = BridgePreferences.selection(prefs);
        prefs.registerOnSharedPreferenceChangeListener(settingsChanged);
        updateChannel();
    }
    private void updateChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("tracking", BridgePreferences.localized(this).getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW));
    }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        String action = intent == null ? START : intent.getAction();
        startForeground(1, notification());
        if (STOP.equals(action)) {
            wanted = false; cancel.set(true);
            getSharedPreferences("bridge", MODE_PRIVATE).edit().putBoolean("wanted", false).apply();
            if (!working) stopNow();
        } else {
            wanted = intent != null || getSharedPreferences("bridge", MODE_PRIVATE).getBoolean("wanted", false);
            getSharedPreferences("bridge", MODE_PRIVATE).edit().putBoolean("wanted", wanted).apply();
            if (RESTART.equals(action)) cancel.set(true);
            if (!working && wanted) launch();
            else if (!wanted && !working) stopNow();
        }
        return START_STICKY;
    }
    private Notification notification() {
        Context localized = BridgePreferences.localized(this);
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, BridgeActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, BridgeService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "tracking").setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("PicoFacialBridge").setContentText(localized.getString(R.string.notification_active))
                .setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_media_pause, localized.getString(R.string.stop), stop).build();
    }
    private void launch() {
        cancel.set(false); working = true;
        BridgeState.status = R.string.status_starting; BridgeState.detail = R.string.detail_connecting; BridgeState.error = "";
        BridgeState.rates.reset();
        new Thread(() -> {
            boolean failed = false;
            try { runBridge(); }
            catch (Exception | LinkageError e) {
                if (!cancel.get()) { failed = true; BridgeState.status = R.string.status_error; BridgeState.detail = R.string.detail_error; BridgeState.error = e.toString(); BridgeState.log("ERROR " + e); }
            } finally {
                boolean finalFailed = failed;
                main.post(() -> {
                    working = false;
                    if (destroyed) return;
                    if (finalFailed) {
                        wanted = false;
                        getSharedPreferences("bridge", MODE_PRIVATE).edit().putBoolean("wanted", false).apply();
                        stopForeground(true); stopSelf();
                    } else if (wanted) launch(); else stopNow();
                });
            }
        }, "PicoFacialBridge").start();
    }

    private void runBridge() throws Exception {
        for (String p : new String[]{"com.picovr.permission.EYE_TRACKING", "com.picovr.permission.FACE_TRACKING"}) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) throw new SecurityException("Grant permission in app: " + p);
        }
        BridgeState.log("BEGIN uid=" + android.os.Process.myUid() + " pid=" + android.os.Process.myPid() + " no OpenXR session");
        TrackingSession session = new TrackingSession(BridgeState::log);
        MulticastSocket socket = null;
        DeliveryLedger ledger = null;
        boolean active = false, captureWanted = true;
        try {
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PicoFacialBridge:tracking");
            wake.acquire();
            multicast = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).createMulticastLock("PicoFacialBridge:discovery");
            multicast.setReferenceCounted(false); multicast.acquire();
            NetworkInterface network = NetworkInterface.getByName("wlan0");
            if (network == null || !network.isUp()) throw new IllegalStateException("Connect headset to Wi-Fi first");
            InetAddress own = null;
            for (Enumeration<InetAddress> it = network.getInetAddresses(); it.hasMoreElements();) {
                InetAddress candidate = it.nextElement(); if (candidate instanceof Inet4Address) own = candidate;
            }
            if (own == null) throw new IllegalStateException("Wi-Fi has no IPv4 address");
            BridgeState.address = own.getHostAddress() + ":9030";
            socket = new MulticastSocket(null); socket.setReuseAddress(true); socket.bind(new InetSocketAddress(9030));
            socket.setSoTimeout(1); socket.setNetworkInterface(network); socket.setTimeToLive(1);
            socket.joinGroup(new InetSocketAddress("239.255.255.250", 9030), network);
            BridgeState.udp = true; BridgeState.client = ""; BridgeState.clientMessage = R.string.client_none;
            BridgeState.packets = BridgeState.eyeFrames = BridgeState.faceFrames = 0;
            BridgeState.ringOverruns=0;
            BridgeState.log("UDP listening " + BridgeState.address + " multicast=239.255.255.250 interface=wlan0");
            PeerProtocol peer = new PeerProtocol();
            InetSocketAddress endpoint = null;
            long nextStart = 0, nextLog = 0, reportedOverruns=0;
            int lastSelection = transmitMask;
            FramePump pump=new FramePump();
            long lastFresh = SystemClock.elapsedRealtime();
            String startError = null;
            while (!cancel.get()) {
                long now = SystemClock.elapsedRealtime();
                final int selection = transmitMask;
                final boolean sendEye = (selection & 1) != 0, sendFace = (selection & 2) != 0;
                if (selection != lastSelection) { nextStart = 0; lastSelection = selection; }
                byte[] inbound = new byte[64]; DatagramPacket request = new DatagramPacket(inbound, inbound.length);
                try {
                    socket.receive(request);
                    InetSocketAddress sender = new InetSocketAddress(request.getAddress(), request.getPort());
                    boolean hadPeer=peer.connected();
                    if (peer.receive(sender.toString(), Arrays.copyOf(inbound, request.getLength()), now)) {
                        if (peer.connected()) {
                            if(!hadPeer){ledger=new DeliveryLedger();BridgeState.rates.reset();BridgeState.log("DELIVERY_BEGIN peer="+sender);}
                            endpoint = sender;
                            if (!captureWanted) nextStart = 0;
                            captureWanted = true; BridgeState.client = sender.toString();
                        } else {
                            finishLedger(ledger,"STOP");ledger=null;
                            endpoint = null; captureWanted = false; BridgeState.client = ""; BridgeState.clientMessage = R.string.client_stop; BridgeState.rates.reset();
                            BridgeState.log("CLIENT_STOP; tracking paused until next discovery");
                        }
                    }
                } catch (SocketTimeoutException expected) { /* poll the tracking rings */ }
                if (peer.expired(now)) {
                    finishLedger(ledger,"TIMEOUT");ledger=null;
                    peer.clear(); endpoint = null; captureWanted = false; BridgeState.client = ""; BridgeState.clientMessage = R.string.client_timeout; BridgeState.rates.reset();
                    BridgeState.log("CLIENT_TIMEOUT");
                }
                if (peer.shouldPing(now) && endpoint != null) {
                    socket.send(new DatagramPacket(PeerProtocol.PING, PeerProtocol.PING.length, endpoint)); peer.pingSent(now);
                }
                boolean sessionWanted = captureWanted && selection != 0;
                if (!sessionWanted && active) { session.close(); active = false; pump = new FramePump(); BridgeState.rates.reset(); }
                if (StreamHealth.reconnectDue(active, session.isAlive(), now, lastFresh)) {
                    BridgeState.log("RECONNECT stale or dead tracking session");
                    session.close(); active = false; nextStart = now + 1000;
                }
                if (sessionWanted && !active && now >= nextStart) {
                    try { session.open(); active = true; startError = null; lastFresh = now; pump=new FramePump(); reportedOverruns=0; BridgeState.rates.reset(); }
                    catch (UnsupportedOperationException e) { throw e; }
                    catch (Exception e) { session.close(); startError = e.toString(); BridgeState.log("RETRY " + e); nextStart = now + 5000; }
                }
                List<byte[]> eyes=active?session.readEyes():Collections.emptyList();
                List<byte[]> faces=active?session.readFaces():Collections.emptyList();
                BridgeState.eyeFrames+=eyes.size();BridgeState.faceFrames+=faces.size();
                if(active && session.overruns()>reportedOverruns){
                    BridgeState.ringOverruns+=session.overruns()-reportedOverruns;reportedOverruns=session.overruns();
                    BridgeState.log("RING_HISTORY_OVERRUN count="+BridgeState.ringOverruns);
                }
                final MulticastSocket sendingSocket=socket;
                final InetSocketAddress destination=endpoint;
                final DeliveryLedger sendingLedger=ledger;
                final FramePump forwardingPump = pump;
                long nano = SystemClock.elapsedRealtimeNanos();
                pump.accept(eyes,faces,nano,sendEye,sendFace,payload -> {
                    synchronized (transmissionLock) {
                        if(destination!=null && selection == transmitMask && !cancel.get()){
                            sendingSocket.send(new DatagramPacket(payload,payload.length,destination));
                            sendingLedger.add(payload);BridgeState.packets++;
                            BridgeState.rates.sent(SystemClock.elapsedRealtimeNanos(),
                                    sendEye && TrackingData.eyeFresh(forwardingPump.eye(), nano) ? TrackingData.eyeTimestamp(forwardingPump.eye()) : 0,
                                    sendFace && TrackingData.faceFresh(forwardingPump.face(), nano) ? TrackingData.faceTimestamp(forwardingPump.face()) : 0);
                        }
                    }
                });
                byte[] eye = active ? pump.eye() : null, face = active ? pump.face() : null;
                long eyeTime = eye == null ? 0 : TrackingData.eyeTimestamp(eye);
                long faceTime = face == null ? 0 : TrackingData.faceTimestamp(face);
                BridgeState.eye = TrackingData.eyeValid(eye) && TrackingData.fresh(nano, eyeTime);
                BridgeState.face = TrackingData.faceValid(face) && TrackingData.fresh(nano, faceTime);
                boolean live = TrackingData.forwardable(face, eye, nano, sendEye, sendFace);
                if (live) lastFresh = now;
                BridgeState.error = startError != null && sessionWanted ? startError : "";
                BridgeState.status = selection == 0 ? R.string.status_paused : live ? R.string.status_running
                        : startError != null && sessionWanted ? R.string.status_error : R.string.status_starting;
                BridgeState.detail = selection == 0 ? R.string.detail_paused : startError != null && sessionWanted ? R.string.detail_retry
                        : live ? R.string.detail_live : captureWanted ? R.string.detail_waiting : R.string.detail_discovery;
                if (now >= nextLog) {
                    double[] hz = BridgeState.rates.hz(SystemClock.elapsedRealtimeNanos());
                    BridgeState.log("STATE " + getResources().getResourceEntryName(BridgeState.status) + " eye=" + BridgeState.eye + " face=" + BridgeState.face
                            + " frames=" + BridgeState.eyeFrames + "/" + BridgeState.faceFrames + " packets=" + BridgeState.packets + " client=" + BridgeState.client + " ringOverruns="+BridgeState.ringOverruns
                            + " transmitMask=" + selection + " eyeTxHz=" + hz[0] + " faceTxHz=" + hz[1]);
                    nextLog = now + 5000;
                }
                SystemClock.sleep(active ? 1 : 10);
            }
        } finally {
            finishLedger(ledger,"WORKER_END");
            session.close();
            if (socket != null) socket.close();
            if (multicast != null && multicast.isHeld()) multicast.release();
            if (wake != null && wake.isHeld()) wake.release();
            BridgeState.eye = BridgeState.face = BridgeState.udp = false;
            BridgeState.rates.reset();
            BridgeState.log("END worker cleanup complete");
        }
    }
    private void finishLedger(DeliveryLedger ledger,String reason){
        if(ledger!=null)BridgeState.log("DELIVERY_END reason="+reason+" packets="+ledger.count()+" sha256="+ledger.finishHex()+" ringOverruns="+BridgeState.ringOverruns);
    }
    private void stopNow() {
        BridgeState.status = R.string.status_stopped; BridgeState.detail = R.string.detail_stopped; BridgeState.error = "";
        BridgeState.client = ""; BridgeState.clientMessage = R.string.client_none; BridgeState.rates.reset(); stopForeground(true); stopSelf();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true; wanted = false; cancel.set(true); prefs.unregisterOnSharedPreferenceChangeListener(settingsChanged); super.onDestroy();
    }
}
