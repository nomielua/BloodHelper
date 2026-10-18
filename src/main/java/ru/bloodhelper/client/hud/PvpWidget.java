package ru.bloodhelper.client.hud;

import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiIcons;

/** PvP cooldown presentation; timing remains owned by CooldownTracker. */
public final class PvpWidget {
    private PvpWidget() {}
    public static void render(GuiGraphics g, CooldownTracker.Entry entry, ClientConfig c) {
        var settings = c.pvp;
        String timer = Double.isFinite(entry.seconds())
                ? String.format(Locale.ROOT, "%.1fs", Math.max(0, entry.seconds())) : "—";
        int timerWidth = HudStyle.strongWidth(timer);
        int width = Math.max(86, 25 + HudStyle.strongWidth("PvP") + 12 + timerWidth + 8);
        int height = settings.showProgress ? 25 : 21;
        HudRenderer.begin(g, "pvp", settings.position, settings.alignment, settings.bottom, width, height);
        try {
            if (c.background && settings.background) HudStyle.panel(g, width, height, c);
            UiIcons.draw(g, "shield", 4, 4, 13, c.accent);
            HudStyle.separator(g, 19, 0, 21);
            HudStyle.strong(g, "PvP", 25, 6, HudStyle.TEXT, settings.shadow);
            HudStyle.strong(g, timer, width - 8 - timerWidth, 6, HudStyle.MUTED, settings.shadow);
            if (settings.showProgress) HudStyle.progress(g, 7, 20, width - 14, entry.remaining(), c.accent);
        } finally { g.pose().popMatrix(); }
    }
}
