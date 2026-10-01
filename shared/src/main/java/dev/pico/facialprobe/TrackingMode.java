package dev.pico.facialprobe;

/**
 * Which capability set the headset side can offer, and the exact control line it advertises to the
 * PC module. Pure logic (no Android or file APIs) so the host tests cover it directly.
 *
 * <p>normal: rootless, or rooted without an active enhancement module. The bridge forwards the
 * fused gaze, the facial expression prefix and the per-eye openness the firmware exposes without
 * its per-eye gate.
 *
 * <p>enhance: rooted and an active enhancement module opens the per-eye gate inside the
 * eye-tracking service. Real pupil diameter becomes available, and in dual mode the service emits
 * true per-eye gaze instead of its fixed-depth split of the fused gaze.
 */
public final class TrackingMode {
    public static final String PREFIX = "PXR_MODE";
    public static final String NORMAL = "normal", ENHANCE = "enhance";
    public static final String OFF = "off", LEFT = "left", RIGHT = "right", DUAL = "dual";
    public static final String GATE_ON = "on", GATE_OFF = "off";
    private TrackingMode() {}

    /** Immutable snapshot of what the bridge detected on the device. */
    public static final class Info {
        public final boolean rooted, enhanceModule, gateOn, enhance;
        public final String plugin, label;
        Info(boolean rooted, boolean enhanceModule, boolean gateOn, boolean enhance, String plugin) {
            this.rooted = rooted; this.enhanceModule = enhanceModule; this.gateOn = gateOn; this.enhance = enhance;
            this.plugin = normalizePlugin(plugin);
            this.label = enhance ? ENHANCE : NORMAL;
        }
        /** True only when the enhancement plugin really computes independent per-eye gaze. */
        public boolean keepPerEyeGaze() { return enhance && DUAL.equals(plugin); }
        public boolean sameAs(Info other) {
            return other != null && rooted == other.rooted && enhanceModule == other.enhanceModule
                    && gateOn == other.gateOn && enhance == other.enhance && plugin.equals(other.plugin);
        }
    }

    public static Info normal() { return new Info(false, false, false, false, OFF); }

    /** Only root + a published enhancement state + a mounted gate reaches enhance. */
    public static Info detect(boolean rooted, boolean modulePresent, String gate, String plugin) {
        boolean gateOn = modulePresent && GATE_ON.equals(trim(gate));
        return new Info(rooted, modulePresent, gateOn, rooted && modulePresent && gateOn, plugin);
    }

    /** The control datagram the bridge sends; stock modules ignore its length and keep working. */
    public static String advertise(Info info) {
        return PREFIX + " v=1 mode=" + info.label + " rooted=" + (info.rooted ? 1 : 0)
                + " enhance=" + (info.enhanceModule ? 1 : 0) + " gate=" + (info.gateOn ? 1 : 0)
                + " plugin=" + info.plugin;
    }

    /** Mirror of the PC-side parser, keeping one format on both ends. Null when it is not ours. */
    public static Info parse(String line) {
        if (line == null) return null;
        String text = line.trim();
        if (!text.startsWith(PREFIX)) return null;
        boolean rooted = false, module = false, gate = false, enhance = false, hasMode = false;
        String plugin = "";
        for (String token : text.substring(PREFIX.length()).trim().split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals <= 0) continue;
            String key = token.substring(0, equals), value = token.substring(equals + 1).trim();
            switch (key) {
                case "rooted": rooted = "1".equals(value); break;
                case "enhance": module = "1".equals(value); break;
                case "gate": gate = "1".equals(value); break;
                case "plugin": plugin = value; break;
                case "mode": hasMode = true; enhance = ENHANCE.equals(value); break;
                default: break;
            }
        }
        if (!hasMode) enhance = gate && module;
        if (plugin.isEmpty()) plugin = OFF; // an unknown plugin never unlocks per-eye gaze
        return new Info(rooted, module, gate, enhance, plugin);
    }

    /** Reads one key out of a "mode=dual gate=on" style status line; empty when absent. */
    public static String statusValue(String statusLine, String key) {
        if (statusLine == null) return "";
        for (String token : statusLine.trim().split("\\s+")) {
            int equals = token.indexOf('=');
            if (equals > 0 && token.substring(0, equals).equals(key)) return token.substring(equals + 1).trim();
        }
        return "";
    }

    private static String normalizePlugin(String plugin) {
        String value = trim(plugin);
        switch (value) {
            case LEFT: case RIGHT: case DUAL: return value;
            default: return OFF;
        }
    }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
