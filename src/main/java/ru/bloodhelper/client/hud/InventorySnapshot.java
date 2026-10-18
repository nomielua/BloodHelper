package ru.bloodhelper.client.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Read-only view of the local player's own items, never a container or carried cursor stack. */
public final class InventorySnapshot {
    public record CountedItem(String id, int count) {}

    public record Durability(boolean damageable, int remaining, int maximum, boolean unbreakable) {
        public int percent() {
            return damageable && maximum > 0 ? (int)Math.ceil(remaining * 100.0 / maximum) : -1;
        }
        public float fraction() {
            return damageable && maximum > 0 ? Math.clamp((float)remaining / maximum, 0, 1) : 1;
        }
        public boolean low(int thresholdPercent) {
            return damageable && maximum > 0 && remaining * 100L <= maximum * (long)thresholdPercent;
        }
        public String display(String mode) {
            if (unbreakable) return "∞";
            if (!damageable) return "—";
            return "POINTS".equals(mode) ? Integer.toString(remaining) : percent() + "%";
        }
    }

    public record ArmorPiece(EquipmentSlot slot, String id, String displayName, ItemStack item,
                             Durability durability) {}

    public record Snapshot(Map<String, Integer> counts, Map<String, ItemStack> icons, List<ArmorPiece> armor) {
        public Snapshot {
            counts = Map.copyOf(counts);
            icons = Map.copyOf(icons);
            armor = List.copyOf(armor);
        }
        public int count(String id) { return counts.getOrDefault(id, 0); }
    }

    private InventorySnapshot() {}

    public static Snapshot capture() {
        var player = Minecraft.getInstance().player;
        if (player == null) return new Snapshot(Map.of(), Map.of(), List.of());
        List<CountedItem> main = new ArrayList<>(36);
        Map<String, ItemStack> icons = new LinkedHashMap<>();
        // This is exactly the 36 main slots, including the selected hotbar slot once.
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            CountedItem item = counted(stack);
            if (item != null) {
                main.add(item);
                icons.computeIfAbsent(item.id(), ignored -> stack.copyWithCount(1));
            }
        }
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        CountedItem offhandCount = counted(offhand);
        if (offhandCount != null) icons.computeIfAbsent(offhandCount.id(), ignored -> offhand.copyWithCount(1));
        List<ArmorPiece> armor = new ArrayList<>(4);
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack item = player.getItemBySlot(slot);
            armor.add(armorPiece(slot, item));
        }
        return new Snapshot(countOwned(main, offhandCount), icons, armor);
    }

    /** IDs intentionally aggregate server custom items that share the same registered base item. */
    public static Map<String, Integer> countOwned(List<CountedItem> mainInventory, CountedItem offhand) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (CountedItem item : mainInventory) add(result, item);
        add(result, offhand);
        return Map.copyOf(result);
    }

    public static Durability durability(boolean damageable, boolean unbreakable, int maximum, int damage) {
        if (unbreakable) return new Durability(false, 0, 0, true);
        if (!damageable || maximum <= 0) return new Durability(false, 0, 0, false);
        int remaining = (int)Math.clamp((long)maximum - damage, 0, maximum);
        return new Durability(true, remaining, maximum, false);
    }

    static ArmorPiece armorPiece(EquipmentSlot slot, ItemStack item) {
        if (item == null || item.isEmpty())
            return new ArmorPiece(slot, "", "", ItemStack.EMPTY, durability(false, false, 0, 0));
        ItemStack copy = item.copyWithCount(1);
        return new ArmorPiece(slot, BuiltInRegistries.ITEM.getKey(copy.getItem()).toString(),
                copy.getHoverName().getString(), copy,
                durability(copy.isDamageableItem(), copy.has(DataComponents.UNBREAKABLE), copy.getMaxDamage(), copy.getDamageValue()));
    }

    private static CountedItem counted(ItemStack stack) {
        return stack == null || stack.isEmpty() ? null
                : new CountedItem(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount());
    }
    private static void add(Map<String, Integer> target, CountedItem item) {
        if (item == null || item.id() == null || item.id().isBlank() || item.count() <= 0) return;
        target.merge(item.id(), item.count(), (left, right) -> (int)Math.min(Integer.MAX_VALUE, (long)left + right));
    }
}
