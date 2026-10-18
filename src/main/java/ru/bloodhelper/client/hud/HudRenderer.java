package ru.bloodhelper.client.hud;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.config.ClientConfig;

/** Coordinates widgets; each widget owns its presentation, using one placement model. */
public final class HudRenderer {
    public record Bounds(int x, int y, int width, int height) {
        public boolean contains(double px, double py) { return px >= x && py >= y && px < x + width && py < y + height; }
    }
    private static final Map<String, Bounds> bounds = new LinkedHashMap<>();
    private HudRenderer() {}
    public static Map<String, Bounds> bounds() { return Collections.unmodifiableMap(bounds); }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        PerformanceTracker.frame();
        var mc = Minecraft.getInstance();
        if (!BloodHelperClient.config().music.enabled) MusicWidget.releaseCover();
        if (mc.player == null || mc.options.hideGui
                || mc.screen instanceof ru.bloodhelper.client.gui.HudEditorScreen
                || mc.screen instanceof ru.bloodhelper.client.gui.BloodHelperScreen) {
            bounds.clear();
            MusicWidget.clearControls();
            return;
        }
        renderAll(g, false);
    }
    public static void renderPreview(GuiGraphics g) { renderAll(g, true); }

    private static void renderAll(GuiGraphics g, boolean preview) {
        var c = BloodHelperClient.config();
        bounds.clear();
        MusicWidget.clearControls();
        if (!c.music.enabled) MusicWidget.releaseCover();
        var snapshot = CooldownTracker.snapshot(c);
        if (c.cooldowns.enabled || preview) {
            var items = snapshot.items();
            if (preview && items.isEmpty()) items = List.of(
                    new CooldownTracker.Entry(Identifier.withDefaultNamespace("ender_pearl"), new ItemStack(Items.ENDER_PEARL), HudStyle.tr("Эндер-жемчуг", "Ender Pearl"), 8.4, .65f, true),
                    new CooldownTracker.Entry(Identifier.withDefaultNamespace("golden_apple"), new ItemStack(Items.GOLDEN_APPLE), HudStyle.tr("Золотое яблоко", "Golden Apple"), 25, .4f, true));
            if (!items.isEmpty()) CooldownWidget.render(g, items, c);
        }
        if ((c.pvp.enabled && snapshot.pvp() != null) || preview) {
            var entry = snapshot.pvp();
            if (entry == null) entry = new CooldownTracker.Entry(Identifier.withDefaultNamespace("trident"), ItemStack.EMPTY, "PvP", 19.2, .64f, true);
            PvpWidget.render(g, entry, c);
        }
        if (c.music.enabled || preview) MusicWidget.render(g, c, preview);
        if (c.armor.enabled || preview) ArmorWidget.render(g, c, preview);
        if (c.itemCounter.enabled || preview) ItemCounterWidget.render(g, c, preview);
        if (c.performance.enabled || preview) PerformanceWidget.render(g, c, preview);
        if (c.session.enabled || preview) SessionWidget.render(g, c, preview);
        if (c.notifications.enabled || preview) NotificationWidget.render(g, c, preview);
    }

    public static boolean clickMusic(double x, double y, int button) {
        return MusicWidget.click(x, y, button);
    }
    static double effectiveScale(GuiGraphics g, int w, int h, ClientConfig.HudPosition p) {
        return HudLayout.scale(g.guiWidth(), g.guiHeight(), w, h, p.scale);
    }
    static Bounds begin(GuiGraphics g, String key, ClientConfig.HudPosition p, String align, boolean bottom, int w, int h) {
        var placement = HudLayout.place(g.guiWidth(), g.guiHeight(), w, h, p.scale, p.x, p.y, align, bottom);
        Bounds result = new Bounds(placement.x(), placement.y(), placement.width(), placement.height());
        bounds.put(key, result);
        g.pose().pushMatrix();
        g.pose().translate(placement.x(), placement.y());
        g.pose().scale((float)placement.scale(), (float)placement.scale());
        return result;
    }
}
