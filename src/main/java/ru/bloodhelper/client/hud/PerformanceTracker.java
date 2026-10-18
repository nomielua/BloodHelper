package ru.bloodhelper.client.hud;

import net.minecraft.client.Minecraft;
import ru.bloodhelper.client.BloodHelperClient;

/** Client measurements only. Ping is the server-provided latency of our own tab-list entry. */
public final class PerformanceTracker {
    public record Snapshot(int fps, int pingMs, double frameTimeMs, double[] frames) {}
    private static final FrameHistory HISTORY = new FrameHistory(90);
    private PerformanceTracker() {}
    public static void frame() {
        var mc = Minecraft.getInstance();
        HISTORY.frame(System.nanoTime(), BloodHelperClient.config().performance.enabled
                && mc.level != null && mc.player != null && !mc.isPaused() && mc.screen == null);
    }
    public static void reset() { HISTORY.reset(); }
    public static Snapshot snapshot() {
        var mc = Minecraft.getInstance();
        boolean active = mc.level != null && mc.player != null && !mc.isPaused() && mc.screen == null;
        int ping = -1;
        if (mc.player != null && mc.getConnection() != null) {
            var entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            if (entry != null && entry.getLatency() >= 0) ping = entry.getLatency();
        }
        return new Snapshot(active ? Math.max(0, mc.getFps()) : -1, ping,
                active ? HISTORY.mean() : Double.NaN, active ? HISTORY.samples() : new double[0]);
    }
}
