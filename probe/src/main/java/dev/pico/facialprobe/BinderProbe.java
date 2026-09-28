package dev.pico.facialprobe;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.SystemClock;
import android.system.Os;
import android.system.StructStat;
import android.util.Log;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Minimum real-device probe. Uses Java Binder/Parcel (framework native transport), not shell IPC. */
final class BinderProbe {
    static final String SERVICE = "pxreyetrackingservice";
    static final String DESCRIPTOR = "pvr.IEyeTrackingService";
    private final Context context;
    private final Consumer<String> log;
    private IBinder binder;

    BinderProbe(Context context, Consumer<String> log) { this.context = context; this.log = log; }

    void run(AtomicBoolean stop, int seconds) throws Exception {
        log.accept("BEGIN uid=" + Process.myUid() + " pid=" + Process.myPid()
                + " domain=" + readText("/proc/self/attr/current")
                + " targetSdk=" + context.getApplicationInfo().targetSdkVersion
                + " package=" + context.getPackageName());
        for (String permission : new String[]{"com.picovr.permission.EYE_TRACKING", "com.picovr.permission.FACE_TRACKING"}) {
            String level;
            try { level = String.valueOf(context.getPackageManager().getPermissionInfo(permission, 0).protectionLevel); }
            catch (PackageManager.NameNotFoundException e) { level = "NOT_REGISTERED"; }
            log.accept("PERMISSION " + permission + " protectionLevel=" + level + " check=" + context.checkSelfPermission(permission));
        }
        for (String library : new String[]{"libbinder.so", "libutils.so", "libbase.so", "libcutils.so", "libbinder_ndk.so"}) {
            try { System.load("/system/lib64/" + library); log.accept("LINKER " + library + " OK"); }
            catch (UnsatisfiedLinkError e) { log.accept("LINKER " + library + " FAILED " + e.getMessage()); }
        }
        binder = NativeBindings.lookupService();
        log.accept("SERVICE_LOOKUP NDK binder=" + binder);
        try {
            Class<?> manager = Class.forName("android.os.ServiceManager");
            log.accept("SERVICE_MANAGER Java counterpart to defaultServiceManager available");
            IBinder javaBinder = (IBinder) manager.getDeclaredMethod("checkService", String.class).invoke(null, SERVICE);
            log.accept("SERVICE_LOOKUP Java binder=" + javaBinder);
            if (binder == null) binder = javaBinder;
        } catch (ReflectiveOperationException e) {
            log.accept("SERVICE_LOOKUP JAVA_API_FAILED " + Log.getStackTraceString(e));
        }
        if (binder == null) { log.accept("RESULT SERVICE_NOT_VISIBLE; inspect AVC before assigning cause"); return; }
        log.accept("PING alive=" + binder.isBinderAlive() + " ping=" + binder.pingBinder());
        Parcel descriptorRequest = Parcel.obtain(), descriptorReply = Parcel.obtain();
        try {
            boolean handled = binder.transact(IBinder.INTERFACE_TRANSACTION, descriptorRequest, descriptorReply, 0);
            String descriptor = descriptorReply.readString();
            log.accept("INTERFACE_TRANSACTION handled=" + handled + " descriptor=" + descriptor);
            if (!handled || !DESCRIPTOR.equals(descriptor)) throw new IOException("Unexpected Binder descriptor");
        } finally { descriptorRequest.recycle(); descriptorReply.recycle(); }

        boolean started = false;
        Mapping eye = null, face = null;
        try {
            int result = algorithm(true);
            started = result == 0;
            if (!started) log.accept("START_FAILED code=" + result + "; still testing read-only shared-memory retrieval");
            eye = mapping(2); face = mapping(3);
            if (eye == null || face == null) { log.accept("RESULT SHARED_MEMORY_FAILED"); return; }
            long end = SystemClock.elapsedRealtime() + seconds * 1000L;
            long nextLog = 0;
            int loggedSamples = 0;
            int eyeChanges = 0, faceChanges = 0, eyeValueChanges = 0, faceValueChanges = 0, perEyeGateFrames = 0;
            byte[] oldEye = null, oldFace = null;
            while (!stop.get() && SystemClock.elapsedRealtime() < end) {
                byte[] e = TrackingBuffer.latest(eye.memory, 200, 168);
                byte[] f = TrackingBuffer.latest(face.memory, 380);
                if (e != null && !Arrays.equals(e, oldEye)) {
                    eyeChanges++; if (valuesChanged(oldEye, e, 0, 140)) eyeValueChanges++; oldEye = e;
                    if (perEyeGateOpen(e)) perEyeGateFrames++;
                }
                if (f != null && !Arrays.equals(f, oldFace)) {
                    faceChanges++; if (valuesChanged(oldFace, f, 8, 380)) faceValueChanges++; oldFace = f;
                }
                if (SystemClock.elapsedRealtime() >= nextLog) {
                    if (e != null) logEye(e);
                    if (e != null) logEyeGate(e);
                    if (f != null) logFace(f);
                    log.accept("COUNTS eyeFrames=" + eyeChanges + " faceFrames=" + faceChanges
                            + " eyeValueChanges=" + eyeValueChanges + " faceValueChanges=" + faceValueChanges);
                    nextLog = SystemClock.elapsedRealtime() + 200;
                    if (loggedSamples < 5) {
                        dump("eye-" + loggedSamples, e);
                        dump("face-" + loggedSamples, f);
                        loggedSamples++;
                    }
                }
                SystemClock.sleep(20);
            }
            log.accept("RESULT started=" + started + " eyeFrames=" + eyeChanges + " faceFrames=" + faceChanges
                    + " eyeValueChanges=" + eyeValueChanges + " faceValueChanges=" + faceValueChanges
                    + " perEyeGateFrames=" + perEyeGateFrames);
        } finally {
            boolean mustStop = started;
            ResourceScope.close(eye, face, () -> { if (mustStop) algorithm(false); });
            log.accept("END cleanup_complete=true");
        }
    }

    private int algorithm(boolean start) throws Exception {
        int[] nativeStatus = NativeBindings.algorithm(binder, start);
        log.accept((start ? "START" : "STOP") + " NDK transportStatus=" + nativeStatus[0]
                + " binderStatus=" + nativeStatus[1] + " serviceStatus=" + nativeStatus[2]);
        return nativeStatus[0] != 0 ? nativeStatus[0] : nativeStatus[1] != 0 ? nativeStatus[1] : nativeStatus[2];
    }

    /** Kept as a controlled wire-format comparison, not automatically retried after native success. */
    private int javaAlgorithm(boolean start) throws Exception {
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(DESCRIPTOR); request.writeInt(5);
            if (start) { request.writeString("12"); request.writeInt(1000); } else request.writeInt(12);
            boolean handled = binder.transact(start ? 6 : 9, request, reply, 0);
            log.accept((start ? "START" : "STOP") + " transact handled=" + handled + " replyBytes=" + reply.dataSize());
            if (!handled || reply.dataAvail() < 4) return Integer.MIN_VALUE;
            int status = reply.readInt();
            int service = reply.dataAvail() >= 4 ? reply.readInt() : Integer.MIN_VALUE;
            log.accept((start ? "START" : "STOP") + " binderStatus=" + status + " serviceStatus=" + service);
            return status != 0 ? status : service;
        } catch (Exception e) { log.accept("ALGORITHM_TRANSPORT " + Log.getStackTraceString(e)); throw e; }
        finally { request.recycle(); reply.recycle(); }
    }

    private Mapping mapping(int type) {
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        ParcelFileDescriptor owned = null;
        try {
            request.writeInterfaceToken(DESCRIPTOR); request.writeInt(type);
            boolean handled = binder.transact(18, request, reply, 0);
            log.accept("SHM type=" + type + " transact handled=" + handled + " replyBytes=" + reply.dataSize());
            if (!handled || reply.dataAvail() < 12) return null;
            int status = reply.readInt(), service = reply.readInt(), present = reply.readInt();
            log.accept("SHM type=" + type + " binderStatus=" + status + " serviceStatus=" + service + " hasPacket=" + present);
            if (status != 0 || service != 0 || present == 0) return null;
            owned = reply.readFileDescriptor();
            int size = reply.readInt();
            if (owned == null) throw new IOException("Missing FD");
            StructStat stat = Os.fstat(owned.getFileDescriptor());
            log.accept("FD type=" + type + " fd=" + owned.getFd() + " memorySize=" + size + " fstatSize=" + stat.st_size + " errno=0");
            if (size < 20 || size > 16 * 1024 * 1024 || (stat.st_size > 0 && stat.st_size < size)) throw new IOException("Invalid map length");
            Mapping mapping = new Mapping(owned, size);
            owned = null;
            ByteBuffer data = mapping.memory.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            log.accept("MMAP type=" + type + " OK errno=0 version=" + data.getInt(0) + " elementSize=" + data.getInt(4)
                    + " capacity=" + data.getInt(8) + " writeIndex=" + data.getInt(12) + " dataOffset=" + data.getInt(16));
            // Diagnostic-only snapshot: retains source timestamps for cadence analysis, even after wear stops.
            byte[] ring = new byte[size]; data.position(0); data.get(ring);
            dump("ring-" + type, ring);
            return mapping;
        } catch (Exception e) { log.accept("SHM_FAILED type=" + type + " " + Log.getStackTraceString(e)); return null; }
        finally {
            if (owned != null) try { owned.close(); } catch (IOException ignored) {}
            request.recycle(); reply.recycle();
        }
    }

    private void logEye(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        log.accept(String.format(Locale.US, "EYE ts=%d status=%d/%d/%d gazeCombined=%.5f,%.5f,%.5f openness=%.4f/%.4f valid=%s"
                        + " gazeL=%.5f,%.5f,%.5f gazeR=%.5f,%.5f,%.5f pupil=%.4f/%.4f",
                b.getLong(168), b.getInt(0), b.getInt(4), b.getInt(8), b.getFloat(72), b.getFloat(76), b.getFloat(80), b.getFloat(84), b.getFloat(88), TrackingData.eyeValid(raw),
                b.getFloat(48), b.getFloat(52), b.getFloat(56), b.getFloat(60), b.getFloat(64), b.getFloat(68), b.getFloat(92), b.getFloat(96)));
    }

    /**
     * Per-eye diagnostic. Offsets follow the on-device slot layout used by
     * TrackingBuffer/eyeValid: 0/4/8 left/right/combined status, 12/24/36 gaze
     * points, 48/60/72 gaze vectors, 84/88 openness, 92/96 pupil. Relevant
     * EyePoseStatus bits: 0x80 per-eye gaze point, 0x100 per-eye gaze vector,
     * 0x800 pupil diameter. On firmware that does not report per-eye data these
     * stay zero.
     */
    private void logEyeGate(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int left = b.getInt(0), right = b.getInt(4), combined = b.getInt(8);
        log.accept(String.format(Locale.US,
                "EYEGATE ts=%d gateBits L=0x%x/0x%x/0x%x R=0x%x/0x%x/0x%x C=0x%x"
                        + " perEyeVector=%b perEyePoint=%b pupilDiameter=%b"
                        + " gazePointL=%.5f,%.5f,%.5f gazePointR=%.5f,%.5f,%.5f gazePointC=%.5f,%.5f,%.5f"
                        + " gazeVectorL=%.5f,%.5f,%.5f gazeVectorR=%.5f,%.5f,%.5f gazeVectorC=%.5f,%.5f,%.5f"
                        + " pupilL=%.4f pupilR=%.4f",
                b.getLong(168),
                left & 0x80, left & 0x100, left & 0x800,
                right & 0x80, right & 0x100, right & 0x800,
                combined,
                (left & 0x100) != 0 || (right & 0x100) != 0,
                (left & 0x80) != 0 || (right & 0x80) != 0,
                (left & 0x800) != 0 || (right & 0x800) != 0,
                b.getFloat(12), b.getFloat(16), b.getFloat(20),
                b.getFloat(24), b.getFloat(28), b.getFloat(32),
                b.getFloat(36), b.getFloat(40), b.getFloat(44),
                b.getFloat(48), b.getFloat(52), b.getFloat(56),
                b.getFloat(60), b.getFloat(64), b.getFloat(68),
                b.getFloat(72), b.getFloat(76), b.getFloat(80),
                b.getFloat(92), b.getFloat(96)));
    }

    /** True when either eye carries the per-eye gaze-vector validity bit. */
    private static boolean perEyeGateOpen(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        return (b.getInt(0) & 0x100) != 0 || (b.getInt(4) & 0x100) != 0;
    }

    private void dump(String name, byte[] bytes) throws IOException {
        if (bytes == null) return;
        try (FileOutputStream file = context.openFileOutput(name + ".bin", Context.MODE_PRIVATE)) { file.write(bytes); }
        log.accept("SAMPLE_FILE " + name + ".bin bytes=" + bytes.length);
    }

    private void logFace(byte[] raw) {
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        log.accept(String.format(Locale.US, "FACE ts=%d valid=%.2f/%.2f jaw=%.4f smileL=%.4f smileR=%.4f blinkL=%.4f blinkR=%.4f tongue=%.4f",
                b.getLong(0), b.getFloat(296), b.getFloat(300), b.getFloat(36), b.getFloat(84), b.getFloat(92), b.getFloat(120), b.getFloat(160), b.getFloat(212)));
    }

    private static boolean valuesChanged(byte[] old, byte[] current, int from, int to) {
        if (old == null) return false;
        for (int i = from; i < to; i++) if (old[i] != current[i]) return true;
        return false;
    }

    private static String readText(String path) {
        try (FileInputStream input = new FileInputStream(path)) {
            byte[] bytes = new byte[512]; int size = input.read(bytes);
            return new String(bytes, 0, Math.max(0, size)).trim();
        } catch (IOException e) { return e.toString(); }
    }

    private static final class Mapping implements AutoCloseable {
        final ParcelFileDescriptor descriptor;
        final ByteBuffer memory;
        Mapping(ParcelFileDescriptor descriptor, int size) throws IOException {
            this.descriptor = descriptor;
            memory = NativeBindings.map(descriptor.getFd(), size);
        }
        @Override public void close() throws IOException {
            try { NativeBindings.unmap(memory); } finally { descriptor.close(); }
        }
    }
}
