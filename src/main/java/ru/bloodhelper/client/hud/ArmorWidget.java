package ru.bloodhelper.client.hud;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiIcons;

/** Four real equipped armor slots with an optional compact item-name layout. */
public final class ArmorWidget {
    private static final int WARNING = 0xFFFF6570;
    private ArmorWidget() {}

    public static void render(GuiGraphics g, ClientConfig c, boolean preview) {
        var settings = c.armor;
        var armor = preview ? previewArmor() : InventorySnapshot.capture().armor();
        long equipped = armor.stream().filter(piece -> !piece.item().isEmpty()).count();
        if (!preview && (armor.isEmpty() || (settings.hideWhenEmpty && equipped == 0))) return;
        int width = settings.showNames ? 188 : 160;
        int height = settings.showNames ? 21 + armor.size() * 25 : 66;
        HudRenderer.begin(g, "armor", settings.position, settings.alignment, settings.bottom, width, height);
        try {
            if (settings.background) HudStyle.surface(g, 0, 0, width, 18, 6, c);
            UiIcons.draw(g, "shield", 4, 3, 13, c.accent);
            HudStyle.separator(g, 19, 0, 18);
            HudStyle.strong(g, "Armor HUD", 25, 3, HudStyle.TEXT, settings.shadow);
            String count = equipped + "/4";
            HudStyle.label(g, count, width - 7 - HudStyle.labelWidth(count), 4, HudStyle.FAINT, settings.shadow);
            for (int i = 0; i < armor.size(); i++) {
                var piece = armor.get(i);
                if (settings.showNames) renderRow(g, c, piece, 21 + i * 25, width);
                else renderCell(g, c, piece, i * 40, 21, 37);
            }
        } finally { g.pose().popMatrix(); }
    }

    private static void renderCell(GuiGraphics g, ClientConfig c, InventorySnapshot.ArmorPiece piece, int x, int y, int width) {
        var settings = c.armor;
        if (settings.background) HudStyle.surface(g, x, y, width, 45, 5, c);
        if (!piece.item().isEmpty()) g.renderItem(piece.item(), x + (width - 16) / 2, y + 3);
        else UiIcons.draw(g, "shield", x + (width - 12) / 2, y + 5, 12, HudStyle.FAINT);
        var durability = piece.durability();
        int color = durability.low(settings.warnPercent) ? WARNING : HudStyle.MUTED;
        String value = durability.display(settings.durabilityMode);
        value = HudStyle.fitStrong(value, width - 6);
        HudStyle.strong(g, value, x + (width - HudStyle.strongWidth(value)) / 2, y + 23, color, settings.shadow);
        if (durability.damageable()) HudStyle.progress(g, x + 5, y + 40, width - 10, durability.fraction(), color == WARNING ? color : c.accent);
    }

    private static void renderRow(GuiGraphics g, ClientConfig c, InventorySnapshot.ArmorPiece piece, int y, int width) {
        var settings = c.armor;
        if (settings.background) HudStyle.surface(g, 0, y, width, 22, 5, c);
        if (!piece.item().isEmpty()) g.renderItem(piece.item(), 4, y + 2);
        else UiIcons.draw(g, "shield", 6, y + 4, 12, HudStyle.FAINT);
        var durability = piece.durability();
        String value = durability.display(settings.durabilityMode);
        int valueX = width - 7 - HudStyle.strongWidth(value);
        String name = piece.item().isEmpty() ? emptySlot(piece.slot()) : piece.displayName();
        HudStyle.text(g, HudStyle.fit(name, valueX - 32), 26, y + 3, HudStyle.TEXT, settings.shadow);
        int color = durability.low(settings.warnPercent) ? WARNING : HudStyle.MUTED;
        HudStyle.strong(g, value, valueX, y + 3, color, settings.shadow);
        if (durability.damageable()) HudStyle.progress(g, 26, y + 18, width - 33, durability.fraction(), color == WARNING ? color : c.accent);
    }

    private static String emptySlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HudStyle.tr("Нет шлема", "No helmet");
            case CHEST -> HudStyle.tr("Нет нагрудника", "No chestplate");
            case LEGS -> HudStyle.tr("Нет поножей", "No leggings");
            default -> HudStyle.tr("Нет ботинок", "No boots");
        };
    }

    private static List<InventorySnapshot.ArmorPiece> previewArmor() {
        ItemStack helmet = new ItemStack(Items.NETHERITE_HELMET);
        ItemStack chest = new ItemStack(Items.NETHERITE_CHESTPLATE);
        ItemStack legs = new ItemStack(Items.NETHERITE_LEGGINGS);
        ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
        helmet.setDamageValue(Math.round(helmet.getMaxDamage() * .16f));
        chest.setDamageValue(Math.round(chest.getMaxDamage() * .83f));
        legs.setDamageValue(Math.round(legs.getMaxDamage() * .42f));
        boots.setDamageValue(Math.round(boots.getMaxDamage() * .07f));
        return List.of(InventorySnapshot.armorPiece(EquipmentSlot.HEAD, helmet),
                InventorySnapshot.armorPiece(EquipmentSlot.CHEST, chest),
                InventorySnapshot.armorPiece(EquipmentSlot.LEGS, legs),
                InventorySnapshot.armorPiece(EquipmentSlot.FEET, boots));
    }
}
