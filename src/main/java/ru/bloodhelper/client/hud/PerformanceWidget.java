package ru.bloodhelper.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiDraw;
import ru.bloodhelper.client.gui.UiIcons;

public final class PerformanceWidget {
    private record Metric(String label, String value) {}
    private PerformanceWidget() {}
    public static void render(GuiGraphics g, ClientConfig c, boolean preview) {
        var s = c.performance;
        var sample = preview ? preview() : PerformanceTracker.snapshot();
        List<Metric> metrics = new ArrayList<>(3);
        if (s.showFps) metrics.add(new Metric("FPS", sample.fps() < 0 ? "—" : Integer.toString(sample.fps())));
        if (s.showPing) metrics.add(new Metric("Ping", sample.pingMs() < 0 ? "—" : sample.pingMs() + " ms"));
        if (s.showFrameTime) metrics.add(new Metric("Frame", Double.isFinite(sample.frameTimeMs())
                ? String.format(Locale.ROOT, "%.1f ms", sample.frameTimeMs()) : "—"));
        boolean compact = g.guiWidth() < 550;
        int width = compact ? 118 : Math.max(136, metrics.size() * 57 + 14);
        int metricsHeight = compact ? metrics.size() * 15 : metrics.isEmpty() ? 0 : 31;
        int height = 25 + metricsHeight + (s.showGraph ? 31 : 0);
        HudRenderer.begin(g, "performance", s.position, s.alignment, s.bottom, width, height);
        try {
            if (c.background && s.background) HudStyle.panel(g, width, height, c);
            UiIcons.draw(g, "grid", 6, 5, 13, c.accent);
            HudStyle.strong(g, "Performance", 24, 6, HudStyle.TEXT, s.shadow);
            int y = 26;
            for (int i = 0; i < metrics.size(); i++) {
                int x = compact ? 8 : 8 + i * ((width - 16) / metrics.size());
                Metric m = metrics.get(i);
                HudStyle.label(g, m.label(), x, compact ? y + i * 15 : y, HudStyle.FAINT, s.shadow);
                String value = HudStyle.fitStrong(m.value(), compact ? 67 : (width - 16) / metrics.size() - 4);
                HudStyle.strong(g, value, compact ? width - 8 - HudStyle.strongWidth(value) : x, compact ? y + i * 15 : y + 12, HudStyle.TEXT, s.shadow);
            }
            if (s.showGraph) drawGraph(g, sample.frames(), 8, 28 + metricsHeight, width - 16, 22, c.accent);
        } finally { g.pose().popMatrix(); }
    }
    private static void drawGraph(GuiGraphics g, double[] values, int x, int y, int width, int height, int color) {
        UiDraw.rounded(g, x, y, width, height, 3, 0x4423222A);
        if (values.length == 0) {
            HudStyle.label(g, "—", x + width / 2 - 3, y + 6, HudStyle.FAINT, false);
            return;
        }
        double max = 33.333;
        for (double v : values) max = Math.max(max, v);
        int baseline = y + height - 2;
        int reference = baseline - (int)Math.round((height - 4) * 16.667 / max);
        g.fill(x + 2, reference, x + width - 2, reference + 1, 0x445D5660);
        for (int column = 0; column < width - 4; column++) {
            // Newest samples stay at the right; one-column peak preserves short stalls.
            int index = (int)((long)column * values.length / (width - 4));
            int next = Math.max(index + 1, (int)((long)(column + 1) * values.length / (width - 4)));
            double peak = values[index];
            for (int i = index + 1; i < Math.min(next, values.length); i++) peak = Math.max(peak, values[i]);
            int h = Math.max(1, (int)Math.round((height - 4) * peak / max));
            g.fill(x + 2 + column, baseline - h, x + 3 + column, baseline, UiDraw.alpha(color, 160));
            g.fill(x + 2 + column, baseline - h, x + 3 + column, baseline - h + 1, color);
        }
    }
    private static PerformanceTracker.Snapshot preview() {
        double[] values = new double[90];
        for (int i = 0; i < values.length; i++) values[i] = 6.2 + Math.sin(i * .35) * .7 + (i == 62 ? 6 : 0);
        return new PerformanceTracker.Snapshot(144, 42, 6.9, values);
    }
}
