package dev.pico.facialprobe;

import android.os.IBinder;
import java.io.IOException;
import java.nio.ByteBuffer;

final class NativeBindings {
    static { System.loadLibrary("pico_probe"); }
    static native IBinder lookupService();
    /** transport status, first reply status, second (function) status. */
    static native int[] algorithm(IBinder binder, boolean start);
    static native ByteBuffer map(int fd, int size) throws IOException;
    static native void unmap(ByteBuffer memory) throws IOException;
}
