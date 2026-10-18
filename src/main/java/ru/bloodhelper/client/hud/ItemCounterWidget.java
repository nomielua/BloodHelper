package ru.bloodhelper.client.hud;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiIcons;

/** User-selected registry item counts, including main inventory and offhand exactly once. */
public final class ItemCounterWidget {
    private record Entry(ItemStack icon, String name, int count) {}
    private static final int[] PREVIEW_COUNTS = {2, 16, 24, 64, 6, 12, 8, 32};
    private ItemCounterWidget() {}

    public static void render(GuiGraphics g, ClientConfig c, boolean preview) {
        var settings = c.itemCounter;
        var snapshot = preview ? null : InventorySnapshot.capture();
        List<Entry> entries = new ArrayList<>(8);
        for (String raw : new LinkedHashSet<>(settings.items)) {
            if (entries.size() == 8) break;
            Identifier id = raw == null ? null : Identifier.tryParse(raw);
            // DefaultedRegistry#getValue silently returns AIR for unknown keys, so check first.
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
            ItemStack defaultIcon = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
            if (defaultIcon.isEmpty()) continue;
            int count = preview ? PREVIEW_COUNTS[entries.size()] : snapshot.count(id.toString());
            if (settings.hideZero && count == 0) continue;
            ItemStack icon = preview ? defaultIcon : snapshot.icons().getOrDefault(id.toString(), defaultIcon);
            entries.add(new Entry(icon, defaultIcon.getHoverName().getString(), count));
        }
        if (entries.isEmpty() && !preview) return;
        int cellWidth = settings.showNames ? 174 : 43;
        int columns = settings.showNames ? 1 : Math.min(4, Math.max(1, entries.size()));
        int width = Math.max(113, columns * (cellWidth + 3) - 3);
        int rowHeight = settings.showNames ? 24 : 42;
        int rows = Math.max(1, (entries.size() + columns - 1) / columns);
        int height = 21 + rows * rowHeight;
        HudRenderer.begin(g, "itemCounter", settings.position, settings.alignment, settings.bottom, width, height);
        try {
            if (settings.background) HudStyle.surface(g, 0, 0, width, 18, 6, c);
            UiIcons.draw(g, "modules", 4, 3, 13, c.accent);
            HudStyle.separator(g, 19, 0, 18);
            HudStyle.strong(g, "Item Counter", 25, 3, HudStyle.TEXT, settings.shadow);
            if (entries.isEmpty()) {
                if (settings.background) HudStyle.surface(g, 0, 21, width, rowHeight - 3, 5, c);
                HudStyle.label(g, HudStyle.fitLabel(HudStyle.tr("Выбери предметы", "Select items"), width - 12),
                        6, 27, HudStyle.MUTED, settings.shadow);
            }
            for (int i = 0; i < entries.size(); i++) {
                Entry item = entries.get(i);
                int x = (i % columns) * (cellWidth + 3), y = 21 + (i / columns) * rowHeight;
                int actualWidth = columns == 1 ? width : cellWidth;
                if (settings.background) HudStyle.surface(g, x, y, actualWidth, rowHeight - 3, 5, c);
                int color = item.count() <= settings.lowCount ? 0xFFFF6570 : HudStyle.TEXT;
                String count = Integer.toString(item.count());
                if (settings.showNames) {
                    g.renderItem(item.icon(), x + 4, y + 2);
                    int countX = x + actualWidth - 7 - HudStyle.strongWidth(count);
                    HudStyle.text(g, HudStyle.fit(item.name(), countX - x - 32), x + 26, y + 3, HudStyle.MUTED, settings.shadow);
                    HudStyle.strong(g, count, countX, y + 3, color, settings.shadow);
                } else {
                    g.renderItem(item.icon(), x + (actualWidth - 16) / 2, y + 3);
                    count = HudStyle.fitStrong(count, actualWidth - 6);
                    HudStyle.strong(g, count, x + (actualWidth - HudStyle.strongWidth(count)) / 2, y + 23, color, settings.shadow);
                }
            }
        } finally { g.pose().popMatrix(); }
    }
}
