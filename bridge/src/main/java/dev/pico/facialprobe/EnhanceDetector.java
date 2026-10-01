package dev.pico.facialprobe;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Reads the device-side enhancement state. Every probe is best effort and none of them requires
 * root: the enhancement module publishes the state it actually applied as namespaced, transient
 * system properties (any process can read a property; they do not survive a reboot, so a removed
 * or disabled module can never leave a stale "enhanced" behind).
 */
final class EnhanceDetector {
    static final String MODE_PROPERTY = "picoet.enhance.mode";
    static final String GATE_PROPERTY = "picoet.enhance.gate";
    // Magisk puts su at /sbin/su here; the others cover other installs. File.exists() needs no root.
    private static final String[] SU_PATHS = {"/sbin/su", "/system/bin/su", "/system/xbin/su", "/debug_ramdisk/su", "/system/sbin/su"};

    private EnhanceDetector() {}

    static TrackingMode.Info detect() {
        String plugin = property(MODE_PROPERTY), gate = property(GATE_PROPERTY);
        boolean published = !plugin.isEmpty() || !gate.isEmpty();
        // A published module state already proves the device is rooted; the su probe is best effort
        // because SELinux may hide even an existing binary from the app domain.
        return TrackingMode.detect(rooted() || published, published, gate, plugin);
    }

    static boolean rooted() {
        for (String path : SU_PATHS) {
            try { if (new File(path).exists()) return true; }
            catch (RuntimeException ignored) { /* a denied stat is simply "not found" */ }
        }
        return false;
    }

    static String property(String name) {
        String reflected = reflectProperty(name);
        return reflected != null ? reflected.trim() : execProperty(name);
    }

    /** SystemProperties is hidden but greylisted; when a firmware blocks it, fall back to getprop. */
    private static String reflectProperty(String name) {
        try {
            Class<?> type = Class.forName("android.os.SystemProperties");
            Object value = type.getMethod("get", String.class).invoke(null, name);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) { return null; }
    }

    private static String execProperty(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop", name).redirectErrorStream(true).start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (int i = 0; i < 4; i++) { String line = reader.readLine(); if (line == null) break; out.append(line.trim()); }
            }
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroy();
            return out.toString().trim();
        } catch (Exception ignored) { return ""; }
        finally { if (process != null) process.destroy(); }
    }
}
