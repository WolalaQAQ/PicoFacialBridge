package dev.pico.facialprobe;

final class StreamHealth {
    private StreamHealth() {}
    static boolean reconnectDue(boolean active, boolean binderAlive, long nowMillis, long lastFreshMillis) {
        return active && (!binderAlive || nowMillis - lastFreshMillis >= 10000);
    }
}
