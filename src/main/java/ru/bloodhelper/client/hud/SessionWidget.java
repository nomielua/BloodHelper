package ru.bloodhelper.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiIcons;

public final class SessionWidget {
    private record Row(String label, String value, int color) {}
    private SessionWidget() {}
    public static void render(GuiGraphics g, ClientConfig c, boolean preview) {
        var s = c.session;
        var data = preview ? new SessionTracker.Snapshot(s.mode, 12, 8, 3, 4, 1452, true) : SessionTracker.snapshot();
        boolean ffa = "FFA".equals(s.mode);
        List<Row> rows = new ArrayList<>();
        if (s.showTime) rows.add(new Row("Time", duration(data.playSeconds()), HudStyle.TEXT));
        if (ffa) {
            rows.add(new Row("Total kills", Integer.toString(data.kills()), HudStyle.TEXT));
            rows.add(new Row("Rewarded kills", Integer.toString(data.rewardedKills()), c.accent));
        } else {
            rows.add(new Row("Kills", data.killFeedAvailable() ? Integer.toString(data.kills()) : "—", HudStyle.TEXT));
            rows.add(new Row("Deaths", Integer.toString(data.deaths()), HudStyle.TEXT));
            if (s.showKd) rows.add(new Row("K/D", data.killFeedAvailable()
                    ? data.deaths() == 0 ? data.kills() == 0 ? "0.00" : "∞"
                    : String.format(Locale.ROOT, "%.2f", (double)data.kills() / data.deaths()) : "—", HudStyle.MUTED));
            if (s.showStreak) rows.add(new Row("Streak", data.killFeedAvailable() ? Integer.toString(data.streak()) : "—", c.accent));
        }
        boolean waiting = !ffa && !data.killFeedAvailable();
        int width = 154, height = 27 + rows.size() * 16 + (waiting ? 14 : 0);
        HudRenderer.begin(g, "session", s.position, s.alignment, s.bottom, width, height);
        try {
            if (c.background && s.background) HudStyle.panel(g, width, height, c);
            UiIcons.draw(g, "clock", 6, 5, 13, c.accent);
            HudStyle.strong(g, "Session", 24, 6, HudStyle.TEXT, s.shadow);
            String mode = "MEETUPS".equals(s.mode) ? "Meetups" : s.mode;
            HudStyle.label(g, mode, width - 8 - HudStyle.labelWidth(mode), 7, HudStyle.FAINT, s.shadow);
            int y = 28;
            for (Row row : rows) {
                HudStyle.text(g, row.label(), 9, y, HudStyle.MUTED, s.shadow);
                HudStyle.strong(g, row.value(), width - 9 - HudStyle.strongWidth(row.value()), y, row.color(), s.shadow);
                y += 16;
            }
            if (waiting) HudStyle.label(g, HudStyle.fitLabel(HudStyle.tr("Нет данных об убийствах", "Kill feed unavailable"), width - 18), 9, y, HudStyle.FAINT, s.shadow);
        } finally { g.pose().popMatrix(); }
    }
    private static String duration(long seconds) {
        seconds = Math.max(0, seconds);
        long hours = seconds / 3600, minutes = seconds / 60 % 60;
        return hours == 0 ? String.format(Locale.ROOT, "%02d:%02d", minutes, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds % 60);
    }
}
