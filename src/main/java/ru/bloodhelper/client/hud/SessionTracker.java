package ru.bloodhelper.client.hud;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.storage.LevelResource;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.config.ClientConfig;

/** Reads confirmed server messages and our own life state; never infers kills from combat. */
public final class SessionTracker {
    public record Snapshot(String mode, int kills, int rewardedKills, int deaths, int streak,
                           long playSeconds, boolean killFeedAvailable) {}
    private static final SessionState STATE = new SessionState();
    private static boolean registered;
    private static final MessageDeliveryGuard DELIVERIES = new MessageDeliveryGuard();
    private static String deliveryContext = "";
    private static String templateText = "";
    private static KillMessageTemplate template = KillMessageTemplate.compile("");
    private static final Set<String> DIRECT = Set.of("mob", "player", "arrow", "trident", "thrown", "indirectMagic",
            "fireball", "witherSkull", "explosion.player", "sonic_boom", "sting", "thorns", "mace_smash", "spear");
    private static final Set<String> ASSISTED = Set.of("inFire", "lightningBolt", "onFire", "lava", "hotFloor", "inWall",
            "cramming", "drown", "starve", "cactus", "fall", "flyIntoWall", "outOfWorld", "generic", "magic", "wither",
            "anvil", "fallingBlock", "fallingStalactite", "dragonBreath", "dryout", "sweetBerryBush", "freeze",
            "stalagmite", "outsideBorder", "fireworks", "genericKill", "sonic_boom", "sting");
    private SessionTracker() {}
    public static void register() {
        if (registered) return;
        registered = true;
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            var mc = Minecraft.getInstance();
            var config = BloodHelperClient.config();
            if (mc.player == null || mc.level == null || !config.session.enabled || mc.isPaused()) return;
            tick(mc, config);
            if (!DELIVERIES.accept(message)) return;
            long now = System.nanoTime();
            if ("FFA".equals(config.session.mode)) {
                FfaKillReceipt.parse(message.getString()).ifPresent(receipt -> STATE.confirmFfa(receipt, true, false, now));
            } else {
                Optional<KillMessageTemplate.Kill> kill = translatedKill(message);
                if (kill.isPresent() && mc.getConnection().getPlayerInfo(kill.get().victim()) == null) kill = Optional.empty();
                if (kill.isEmpty()) kill = template.parse(message.getString());
                kill.ifPresent(value -> STATE.confirm(value, true, false, now));
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STATE.disconnect(BloodHelperClient.config().session.resetOnDisconnect);
            DELIVERIES.clear(); deliveryContext = "";
            PerformanceTracker.reset();
        });
    }
    public static void tick(Minecraft mc, ClientConfig config) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null) {
            STATE.disconnect(false); // A loading/respawn gap is not a network disconnect.
            return;
        }
        if (!config.session.killMessageTemplate.equals(templateText)) {
            templateText = config.session.killMessageTemplate;
            template = KillMessageTemplate.compile(templateText);
        }
        String identity = identity(mc);
        String context = identity + "|" + config.session.mode;
        if (!context.equals(deliveryContext)) { DELIVERIES.clear(); deliveryContext = context; }
        STATE.tick(identity, mc.player.getGameProfile().name(), config.session.mode, mc.player.isAlive(),
                config.session.enabled, mc.isPaused(), System.nanoTime());
    }
    public static void reset() { STATE.reset(); DELIVERIES.clear(); }
    public static Snapshot snapshot() {
        var s = STATE.snapshot();
        return new Snapshot(s.mode(), s.kills(), s.rewardedKills(), s.deaths(), s.streak(), s.playSeconds(), s.killFeedAvailable());
    }
    private static String identity(Minecraft mc) {
        String server;
        if (mc.getSingleplayerServer() != null) server = "local:" + mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        else if (mc.getCurrentServer() != null) server = "remote:" + mc.getCurrentServer().ip.toLowerCase(Locale.ROOT).strip();
        else server = "connection:" + System.identityHashCode(mc.getConnection());
        return server + "|" + mc.player.getUUID();
    }
    private static Optional<KillMessageTemplate.Kill> translatedKill(Component message) {
        // A chat wrapper or sibling prefix is not a death event, even if it contains death-looking text.
        if (!message.getSiblings().isEmpty() || !(message.getContents() instanceof TranslatableContents contents)) return Optional.empty();
        String key = contents.getKey();
        boolean item = key.endsWith(".item");
        String base = item ? key.substring(0, key.length() - 5) : key;
        boolean known = base.equals("death.fell.assist") || base.equals("death.fell.finish");
        if (base.startsWith("death.attack.")) {
            String type = base.substring("death.attack.".length());
            known = DIRECT.contains(type)
                    || (type.endsWith(".player") && ASSISTED.contains(type.substring(0, type.length() - 7)))
                    || (item && (type.equals("fireworks") || type.equals("onFire")));
        }
        Object[] args = contents.getArgs();
        if (!known || args.length != (item ? 3 : 2)) return Optional.empty();
        String victim = argumentName(args[0]), killer = argumentName(args[1]);
        if (!KillMessageTemplate.isName(victim) || !KillMessageTemplate.isName(killer)) return Optional.empty();
        return Optional.of(new KillMessageTemplate.Kill(killer, victim));
    }
    private static String argumentName(Object value) {
        return value instanceof Component component ? component.getString() : value instanceof String name ? name : "";
    }
}
