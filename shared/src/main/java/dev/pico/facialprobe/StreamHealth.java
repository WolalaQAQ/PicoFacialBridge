package dev.pico.facialprobe;

final class StreamHealth {
    private StreamHealth() {}
    static long requiredFreshness(int primaryMask, boolean gazeWanted, long eye, long morphology) {
        // An auxiliary gaze stall must not restart a healthy face-only avatar.
        if (primaryMask != 0) return (primaryMask & 1) != 0 ? Math.min(eye, morphology) : morphology;
        return gazeWanted ? eye : morphology;
    }
    static boolean reconnectDue(boolean active, boolean binderAlive, long nowMillis, long lastFreshMillis) {
        return active && (!binderAlive || nowMillis - lastFreshMillis >= 10000);
    }
}
