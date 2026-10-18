package ru.bloodhelper.client.hud;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiDraw;
import ru.bloodhelper.client.gui.UiIcons;

/** Short stacked cards, using the same OTF metrics and placement as the other HUD widgets. */
public final class NotificationWidget {
    private static final int WIDTH = 214, CARD_HEIGHT = 43, GAP = 5;
    private NotificationWidget() {}

    public static void render(GuiGraphics g, ClientConfig config, boolean preview) {
        var settings = config.notifications;
        if (!settings.enabled && !preview) return;
        List<AlertTracker.Notice> notices = preview ? examples(config) : Notifications.snapshot();
        int count = Math.min(Math.clamp(settings.maxVisible, 1, 4), notices.size());
        if (count == 0) return;
        int height = count * (CARD_HEIGHT + GAP) - GAP;
        HudRenderer.begin(g, "notifications", settings.position, settings.alignment, settings.bottom, WIDTH, height);
        try {
            long now = Notifications.now();
            for (int i = 0; i < count; i++) {
                var notice = notices.get(notices.size() - count + i);
                int y = i * (CARD_HEIGHT + GAP);
                float remaining = preview ? .68f : notice.remaining(now);
                // Animate inside the registered rectangle so small viewports keep the complete card visible.
                double enter = preview || !config.animations ? 1 : Math.clamp((now - notice.createdAt()) / 180.0, 0, 1);
                int inset = (int)Math.round(5 * Math.pow(1 - enter, 3));
                g.pose().pushMatrix();
                g.pose().translate(inset, y);
                try {
                    int width = WIDTH - inset;
                    if (config.background && settings.background) HudStyle.surface(g, 0, 0, width, CARD_HEIGHT, 6, config);
                    UiDraw.rounded(g, 0, 9, 2, CARD_HEIGHT - 18, 1, config.accent);
                    UiIcons.draw(g, notice.icon(), 7, 7, 14, config.accent);
                    HudStyle.strong(g, HudStyle.fitStrong(notice.title(), width - 38), 28, 7, HudStyle.TEXT, settings.shadow);
                    HudStyle.label(g, HudStyle.fitLabel(notice.body(), width - 16), 8, 23, HudStyle.MUTED, settings.shadow);
                    HudStyle.progress(g, 8, CARD_HEIGHT - 5, width - 16, remaining, config.accent);
                } finally { g.pose().popMatrix(); }
            }
        } finally { g.pose().popMatrix(); }
    }

    private static List<AlertTracker.Notice> examples(ClientConfig config) {
        boolean ru = "ru".equals(config.language);
        return List.of(
                new AlertTracker.Notice("preview:armor", "Armor", ru ? "Алмазный нагрудник · осталось 18%" : "Diamond Chestplate · remaining 18%", "shield", 0, 4000),
                new AlertTracker.Notice("preview:items", "Items", ru ? "Тотем бессмертия · осталось 2" : "Totem of Undying · remaining 2", "modules", 0, 4000),
                new AlertTracker.Notice("preview:effect", "Effects", ru ? "Огнестойкость · ещё 10s" : "Fire Resistance · ends in 10s", "clock", 0, 4000));
    }
}
