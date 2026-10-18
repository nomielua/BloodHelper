package ru.bloodhelper.client.hud;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.mixin.CooldownInstanceAccessor;
import ru.bloodhelper.client.mixin.ItemCooldownsAccessor;

public final class CooldownTracker {
    public record Entry(Identifier group, ItemStack item, String label, double seconds, float remaining, boolean known) {}
    public record Snapshot(List<Entry> items, Entry pvp) {}
    public static Snapshot snapshot(ClientConfig config) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return new Snapshot(List.of(), null);
        var manager = mc.player.getCooldowns();
        var accessor = (ItemCooldownsAccessor) manager;
        int tick = accessor.bloodhelper$tickCount();
        var matchingItems = new HashMap<Identifier, ItemStack>();
        for (ItemStack item : mc.player.getInventory().getNonEquipmentItems()) {
            if (!item.isEmpty()) matchingItems.putIfAbsent(manager.getCooldownGroup(item), item);
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = mc.player.getItemBySlot(slot);
            if (!item.isEmpty()) matchingItems.putIfAbsent(manager.getCooldownGroup(item), item);
        }
        List<Entry> items = new ArrayList<>();
        Entry pvp = null;
        for (var value : accessor.bloodhelper$cooldowns().entrySet()) {
            var cd = (CooldownInstanceAccessor) value.getValue();
            int ticksLeft = cd.bloodhelper$endTime() - tick;
            if (ticksLeft <= 0) continue;
            int duration = Math.max(1, cd.bloodhelper$endTime() - cd.bloodhelper$startTime());
            Identifier group = value.getKey();
            ItemStack stack = matchingItems.getOrDefault(group, ItemStack.EMPTY);
            if (stack.isEmpty()) {
                var item = BuiltInRegistries.ITEM.getOptional(group).orElse(null);
                if (item != null && item != Items.AIR) stack = new ItemStack(item);
            }
            boolean known = !stack.isEmpty();
            String label = known ? stack.getHoverName().getString() : group.getPath();
            Entry entry = new Entry(group, stack, label, ticksLeft / 20.0, Math.clamp((float) ticksLeft / duration, 0, 1), known);
            if (config.pvp.enabled && group.toString().equals(config.pvp.cooldownGroup)) pvp = entry;
            else if (known || config.cooldowns.showUnknown) items.add(entry);
        }
        items.sort(Comparator.comparingDouble(Entry::seconds).thenComparing(e -> e.group().toString()));
        return new Snapshot(List.copyOf(items), pvp);
    }
}
