package ru.bloodhelper.client.hud;

import java.util.List;
import ru.bloodhelper.client.config.ClientConfig;

/** One registry for editor labels, anchors, positions and reset behavior. */
public final class HudWidgets {
    public record Widget(String key, String title, ClientConfig.HudPosition position,
                         String alignment, boolean bottom) {}
    private HudWidgets() {}
    public static List<Widget> all(ClientConfig c) {
        return List.of(
                new Widget("cooldowns", "Cooldowns", c.cooldowns.position, c.cooldowns.alignment, c.cooldowns.bottom),
                new Widget("pvp", "PvP Timer", c.pvp.position, c.pvp.alignment, c.pvp.bottom),
                new Widget("music", "Dynamic Island", c.music.position, "CENTER", false),
                widget("armor", "Armor HUD", c.armor), widget("itemCounter", "Item Counter", c.itemCounter),
                widget("notifications", "Notifications", c.notifications),
                widget("performance", "Performance", c.performance), widget("session", "Session Stats", c.session));
    }
    public static Widget get(String key, ClientConfig c) {
        return all(c).stream().filter(w -> w.key.equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown HUD widget: " + key));
    }
    private static Widget widget(String key, String title, ClientConfig.WidgetSettings c) {
        return new Widget(key, title, c.position, c.alignment, c.bottom);
    }
    public static void reset(ClientConfig c) {
        ClientConfig d = new ClientConfig();
        c.cooldowns.position = d.cooldowns.position; c.cooldowns.alignment = d.cooldowns.alignment; c.cooldowns.bottom = d.cooldowns.bottom;
        c.pvp.position = d.pvp.position; c.pvp.alignment = d.pvp.alignment; c.pvp.bottom = d.pvp.bottom;
        c.music.position = d.music.position;
        reset(c.armor, d.armor); reset(c.itemCounter, d.itemCounter); reset(c.notifications, d.notifications);
        reset(c.performance, d.performance); reset(c.session, d.session);
    }
    private static void reset(ClientConfig.WidgetSettings c, ClientConfig.WidgetSettings d) {
        c.position = d.position; c.alignment = d.alignment; c.bottom = d.bottom;
    }
}
