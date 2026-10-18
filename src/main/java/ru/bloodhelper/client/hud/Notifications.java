package ru.bloodhelper.client.hud;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import ru.bloodhelper.client.config.ClientConfig;

/** Watches only the local player's already received inventory and effect state. */
public final class Notifications {
    private static final AlertTracker tracker = new AlertTracker();
    private static final AlertTracker tests = new AlertTracker();
    private static Object worldIdentity, playerIdentity, connectionIdentity;
    private static long lastSoundAt = Long.MIN_VALUE;
    private static int warmupTicks;

    private Notifications() {}
    static long now() { return System.nanoTime() / 1_000_000L; }

    public static void tick(Minecraft mc, ClientConfig config) {
        if (!config.notifications.enabled) { clear(); return; }
        if (mc.player == null || mc.level == null) { clearObserved(); return; }
        if (!mc.player.isAlive()) { clear(); return; }
        if (worldIdentity != mc.level || playerIdentity != mc.player || connectionIdentity != mc.getConnection()) {
            clearObserved();
            worldIdentity = mc.level;
            playerIdentity = mc.player;
            connectionIdentity = mc.getConnection();
            // Inventory/effect packets can arrive on consecutive ticks when entering a world.
            warmupTicks = 20;
        }
        boolean warmingUp = warmupTicks > 0;
        if (warmingUp) warmupTicks--;
        long now = now();
        var settings = config.notifications;
        tracker.expire(now, settings.maxVisible);
        Set<String> present = new HashSet<>();
        boolean newNotice = false;
        var inventory = InventorySnapshot.capture();

        if (settings.armorWarnings) {
            for (var piece : inventory.armor()) {
                var durability = piece.durability();
                if (!durability.damageable()) continue;
                String key = "armor:" + piece.slot() + ":" + piece.id() + ":" + durability.maximum();
                present.add(key);
                double percent = durability.remaining() * 100.0 / durability.maximum();
                boolean crossed = tracker.observe(key, percent, config.armor.warnPercent);
                if (crossed && !warmingUp) {
                    String body = piece.displayName() + " · " + tr(config, "осталось ", "remaining ")
                            + durability.percent() + "%";
                    newNotice |= push(config, key, "Armor", body, "shield", now);
                }
            }
        }

        if (settings.itemWarnings) {
            for (String id : config.itemCounter.items) {
                String key = "item:" + id;
                present.add(key);
                int count = inventory.count(id);
                boolean crossed = tracker.observe(key, count, config.itemCounter.lowCount);
                if (crossed && !warmingUp) {
                    var identifier = net.minecraft.resources.Identifier.tryParse(id);
                    String name = identifier == null ? id : BuiltInRegistries.ITEM.getValue(identifier).getName().getString();
                    String body = name + " · " + tr(config, "осталось ", "remaining ") + count;
                    newNotice |= push(config, key, "Items", body, "modules", now);
                }
            }
        }

        if (settings.effectWarnings) {
            for (var effect : mc.player.getActiveEffects()) {
                if (effect.isInfiniteDuration() || effect.getDuration() <= 0 || !effect.getEffect().value().isBeneficial()) continue;
                String key = "effect:" + BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                present.add(key);
                boolean crossed = tracker.observe(key, effect.getDuration(), settings.effectSeconds * 20.0);
                if (crossed && !warmingUp) {
                    String body = effect.getEffect().value().getDisplayName().getString() + " · "
                            + tr(config, "ещё ", "ends in ") + Math.max(1, (effect.getDuration() + 19) / 20) + "s";
                    newNotice |= push(config, key, "Effects", body, "clock", now);
                }
            }
        }

        tracker.retainObservations(present);
        if (newNotice) playSound(mc, config, now);
    }

    private static boolean push(ClientConfig config, String key, String title, String body, String icon, long now) {
        return tracker.push(key, title, body, icon, now, config.notifications.durationSeconds * 1000L,
                config.notifications.maxVisible);
    }

    private static void playSound(Minecraft mc, ClientConfig config, long now) {
        if (!config.notifications.sound || (lastSoundAt != Long.MIN_VALUE && now - lastSoundAt < 1500)) return;
        lastSoundAt = now;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), .85f, .18f));
    }

    /** Explicit GUI test uses its own short-lived card, also available from the title-screen menu. */
    public static void testNotification(ClientConfig config) {
        if (!config.notifications.enabled) return;
        long now = now();
        if (tests.push("test", "Notifications", tr(config, "Так будут выглядеть уведомления", "This is how notifications will appear"), "info", now, config.notifications.durationSeconds * 1000L, 1))
            playSound(Minecraft.getInstance(), config, now);
    }

    public static List<AlertTracker.Notice> snapshot() {
        long now = now();
        var result = new java.util.ArrayList<>(tracker.snapshot(now)); result.addAll(tests.snapshot(now));
        return List.copyOf(result);
    }

    public static void clear() { clearObserved(); tests.clear(); }
    private static void clearObserved() {
        tracker.clear();
        worldIdentity = playerIdentity = connectionIdentity = null;
        warmupTicks = 0;
        lastSoundAt = Long.MIN_VALUE;
    }

    private static String tr(ClientConfig c, String ru, String en) { return "ru".equals(c.language) ? ru : en; }
}
