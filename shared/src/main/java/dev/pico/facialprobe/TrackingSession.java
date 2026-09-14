package dev.pico.facialprobe;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;
import java.util.Collections;
import java.util.List;

/** Owns one algorithm subscription and two mappings, all accessed on a single worker thread. */
final class TrackingSession implements AutoCloseable {
    private static final String DESCRIPTOR = "pvr.IEyeTrackingService";
    private final Consumer<String> log;
    private IBinder binder;
    private boolean started;
    private Mapping eye, face;
    TrackingSession(Consumer<String> log) { this.log = log; }

    void open() throws Exception {
        binder = NativeBindings.lookupService();
        if (binder == null) binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getDeclaredMethod("checkService", String.class).invoke(null, "pxreyetrackingservice");
        if (binder == null) throw new IOException("Eye tracking service not visible to app UID");
        int[] status = NativeBindings.algorithm(binder, true);
        log.accept("START transport=" + status[0] + " binder=" + status[1] + " service=" + status[2]);
        if (status[0] != 0 || status[1] != 0 || status[2] != 0) throw new IOException("StartAlgorithm failed; wake/wear headset and retry");
        started = true;
        try { eye = map(2, 200); face = map(3, 384); }
        catch (Exception e) { close(); throw e; }
    }

    byte[] eye() { return eye == null ? null : TrackingBuffer.latest(eye.memory, 200, 168); }
    byte[] face() { return face == null ? null : TrackingBuffer.latest(face.memory, 384, 0); }
    boolean isAlive() { return binder != null && binder.isBinderAlive(); }
    List<byte[]> readEyes() { return eye==null?Collections.emptyList():eye.cursor.read(); }
    List<byte[]> readFaces() { return face==null?Collections.emptyList():face.cursor.read(); }
    long overruns(){return (eye==null?0:eye.cursor.overruns())+(face==null?0:face.cursor.overruns());}

    private Mapping map(int kind, int minimum) throws Exception {
        Parcel request = Parcel.obtain(), reply = Parcel.obtain();
        ParcelFileDescriptor fd = null;
        ByteBuffer memory = null;
        try {
            request.writeInterfaceToken(DESCRIPTOR); request.writeInt(kind);
            if (!binder.transact(18, request, reply, 0) || reply.dataAvail() < 12) throw new IOException("Shared-memory transaction failed");
            int transport = reply.readInt(), service = reply.readInt(), present = reply.readInt();
            if (transport != 0 || service != 0 || present == 0) throw new IOException("SHM " + kind + " status=" + transport + "/" + service);
            fd = reply.readFileDescriptor();
            int size = reply.readInt();
            if (fd == null) throw new IOException("SHM missing FD");
            memory = NativeBindings.map(fd.getFd(), size);
            ByteBuffer header = memory.duplicate().order(ByteOrder.LITTLE_ENDIAN);
            int stride = header.getInt(4);
            // Fail clearly on an unknown ABI rather than silently moving facial data to wrong fields.
            if ((kind == 2 && stride != 200) || (kind == 3 && stride != 896)) throw new UnsupportedOperationException("Unsupported firmware slot size kind=" + kind + " stride=" + stride);
            TrackingBuffer.latest(memory, minimum, kind == 2 ? 168 : 0);
            log.accept("SHM kind=" + kind + " FD=" + fd.getFd() + " size=" + size + " stride=" + stride + " mmap=OK");
            Mapping result = new Mapping(fd, memory, minimum, kind==2?168:0); fd = null; memory = null; return result;
        } finally {
            if (memory != null) NativeBindings.unmap(memory);
            if (fd != null) fd.close();
            request.recycle(); reply.recycle();
        }
    }

    @Override public void close() {
        if (eye != null) { eye.close(log); eye = null; }
        if (face != null) { face.close(log); face = null; }
        if (started) {
            started = false;
            try {
                int[] status = NativeBindings.algorithm(binder, false);
                log.accept("STOP transport=" + status[0] + " binder=" + status[1] + " service=" + status[2]);
            } catch (RuntimeException e) { log.accept("STOP error=" + e); }
        }
        binder = null;
    }
    private static final class Mapping {
        final ParcelFileDescriptor fd;
        final ByteBuffer memory;
        final TrackingBuffer.Cursor cursor;
        Mapping(ParcelFileDescriptor fd, ByteBuffer memory, int minimum, int timestampOffset) {
            this.fd = fd; this.memory = memory; this.cursor = new TrackingBuffer.Cursor(memory,minimum,timestampOffset);
        }
        void close(Consumer<String> log) {
            try { NativeBindings.unmap(memory); } catch (IOException e) { log.accept(e.toString()); }
            try { fd.close(); } catch (IOException e) { log.accept(e.toString()); }
        }
    }
}
