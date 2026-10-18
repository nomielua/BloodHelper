package ru.bloodhelper.client.hud;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiIcons;

/** Compact item rows; the timer always keeps its own measured space. */
public final class CooldownWidget {
    private static final int HEADER = 21;
    private static final int ROW = 23;
    private static final int BOTTOM = 0;
    private static final int MORE = 15;
    private static final int MAX_ROWS = 12;
    private static final int TEXT_X = 25;
    private static final int RIGHT = 7;

    private CooldownWidget() {}

    public static void render(GuiGraphics g, List<CooldownTracker.Entry> entries, ClientConfig c) {
        if (entries.isEmpty()) return;
        var settings = c.cooldowns;
        String title = "Cooldowns";
        String total = Integer.toString(entries.size());
        int width = 86;
        for (var entry : entries) {
            int nameWidth = settings.showNames ? Math.min(112, HudStyle.width(label(entry))) + 8 : 0;
            width = Math.max(width, TEXT_X + nameWidth + HudStyle.strongWidth(time(entry.seconds())) + RIGHT);
        }
        width = Math.max(width, HudStyle.width(title) + HudStyle.labelWidth(total) + 38);

        // Limit rows using the requested scale after its horizontal viewport fit. Adding
        // the footer must remove a row when necessary, rather than pushing it offscreen.
        double scale = HudLayout.scale(g.guiWidth(), g.guiHeight(), width, 1, settings.position.scale);
        int available = (int)Math.floor(Math.max(1, g.guiHeight() - 16) / scale);
        int capacity = Math.max(1, Math.min(MAX_ROWS, (available - HEADER - BOTTOM) / ROW));
        boolean overflow = entries.size() > capacity;
        if (overflow) capacity = Math.max(1, Math.min(MAX_ROWS,
                (available - HEADER - BOTTOM - MORE) / ROW));
        int count = Math.min(entries.size(), capacity);
        int hidden = entries.size() - count;
        int height = HEADER + count * ROW + BOTTOM + (hidden > 0 ? MORE : 0);

        HudRenderer.begin(g, "cooldowns", settings.position, settings.alignment, settings.bottom, width, height);
        try {
            if (settings.background) HudStyle.surface(g, 0, 0, width, 18, 6, c);
            clock(g, 5, 4, c.accent);
            HudStyle.separator(g, 18, 0, 18);
            HudStyle.strong(g, title, TEXT_X, 5, HudStyle.TEXT, settings.shadow);
            HudStyle.label(g, total, width - RIGHT - HudStyle.labelWidth(total), 5,
                    HudStyle.FAINT, settings.shadow);

            for (int i = 0; i < count; i++) {
                var entry = entries.get(i);
                int y = HEADER + i * ROW;
                if (settings.background) HudStyle.surface(g, 0, y, width, 20, 5, c);
                if (entry.known() && !entry.item().isEmpty()) {
                    g.pose().pushMatrix();
                    g.pose().translate(4, y + 3);
                    g.pose().scale(.75f, .75f);
                    g.renderItem(entry.item(), 0, 0);
                    g.pose().popMatrix();
                } else clock(g, 5, y + 4, HudStyle.MUTED);
                HudStyle.separator(g, 19, y, 20);

                String remaining = time(entry.seconds());
                int timerX = width - RIGHT - HudStyle.strongWidth(remaining);
                int textY = y + 3;
                if (settings.showNames) {
                    HudStyle.text(g, HudStyle.fit(label(entry), Math.max(0, timerX - TEXT_X - 6)),
                            TEXT_X, textY, HudStyle.TEXT, settings.shadow);
                }
                HudStyle.strong(g, remaining, timerX, textY, HudStyle.MUTED, settings.shadow);
                if (settings.showProgress) HudStyle.progress(g, TEXT_X, y + 17,
                        width - TEXT_X - RIGHT, entry.remaining(), c.accent);
            }

            if (hidden > 0) {
                if (settings.background) HudStyle.surface(g, 0, HEADER + count * ROW, width, MORE, 5, c);
                String more = HudStyle.tr("Ещё ", "+ ") + hidden;
                if (!"ru".equals(c.language)) more += " more";
                HudStyle.label(g, HudStyle.fitLabel(more, width - 18), 9,
                        HEADER + count * ROW + 1, HudStyle.MUTED, settings.shadow);
            }
        } finally {
            g.pose().popMatrix();
        }
    }

    private static String label(CooldownTracker.Entry entry) {
        if (entry.label() != null && !entry.label().isBlank()) return entry.label();
        return entry.group() == null ? HudStyle.tr("Кулдаун", "Cooldown") : entry.group().getPath().replace('_', ' ');
    }

    private static void clock(GuiGraphics g, int x, int y, int color) {
        UiIcons.draw(g, "clock", x - 1, y - 1, 12, color);
    }

    private static String time(double seconds) {
        return Double.isFinite(seconds) ? String.format(Locale.ROOT, "%.1fs", Math.max(0, seconds)) : "—";
    }
}
