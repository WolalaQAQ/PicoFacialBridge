package dev.pico.facialprobe;

final class ResourceScope {
    private ResourceScope() {}
    static void close(AutoCloseable... resources) throws Exception {
        Exception first = null;
        for (AutoCloseable resource : resources) {
            if (resource == null) continue;
            try { resource.close(); }
            catch (Exception e) { if (first == null) first = e; else first.addSuppressed(e); }
        }
        if (first != null) throw first;
    }
}
