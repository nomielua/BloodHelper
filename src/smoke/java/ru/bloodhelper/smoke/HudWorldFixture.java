package ru.bloodhelper.smoke;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.hud.InventorySnapshot;
import ru.bloodhelper.client.hud.SessionTracker;

/** Smoke-source-set only: a disposable local world, never a user's world or remote server. */
public final class HudWorldFixture {
    private static String worldName;
    private static CompletableFuture<Void> preparation;
    private static String originalSessionMode;
    private static boolean originalSessionEnabled;

    private HudWorldFixture() {}

    /** Starts asynchronous loading; the caller must tick until ready. */
    public static void start(Minecraft mc) {
        requireIsolatedDirectory(mc);
        if (worldName != null || mc.level != null || mc.getSingleplayerServer() != null)
            throw new IllegalStateException("HUD fixture requires an unused isolated title screen");
        mc.options.pauseOnLostFocus = false;
        mc.options.renderDistance().set(4);
        mc.options.simulationDistance().set(5);
        worldName = "bloodhelper-hud-smoke-" + UUID.randomUUID().toString().substring(0, 8);
        originalSessionMode = BloodHelperClient.config().session.mode;
        originalSessionEnabled = BloodHelperClient.config().session.enabled;
        var settings = new LevelSettings(worldName, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true,
                new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(42121011L, false, false),
                WorldPresets::createFlatWorldDimensions, new TitleScreen());
    }

    /** No fixed loading delay: wait for the actual player, connection, and disposable integrated server. */
    public static boolean ready(Minecraft mc) {
        return worldName != null && mc.player != null && mc.level != null && mc.getConnection() != null
                && isFixtureServer(mc) && mc.getOverlay() == null && mc.screen == null && !mc.isPaused();
    }

    /** Runs normal server commands inside the fixture world and waits for real inventory packets. */
    public static void prepare(Minecraft mc) {
        requireIsolatedDirectory(mc);
        if (!ready(mc) || preparation != null) throw new IllegalStateException("HUD fixture is not ready to prepare");
        var server = mc.getSingleplayerServer();
        var playerId = mc.player.getUUID();
        preparation = new CompletableFuture<>();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new AssertionError("Fixture player missing from integrated server");
                var source = server.createCommandSourceStack().withEntity(player).withLevel(player.level())
                        .withPosition(player.position()).withSuppressedOutput();
                // These commands act on @s in this disposable local server only.
                for (String command : List.of(
                        "give @s minecraft:totem_of_undying 3",
                        "give @s minecraft:ender_pearl 8",
                        "give @s minecraft:golden_apple 12",
                        "give @s minecraft:arrow 64",
                        "item replace entity @s weapon.offhand with minecraft:ender_pearl 4",
                        "item replace entity @s armor.head with minecraft:diamond_helmet[minecraft:damage=300]",
                        "item replace entity @s armor.chest with minecraft:diamond_chestplate[minecraft:damage=180]",
                        "item replace entity @s armor.legs with minecraft:diamond_leggings[minecraft:damage=100]",
                        "item replace entity @s armor.feet with minecraft:diamond_boots[minecraft:damage=40]",
                        "effect give @s minecraft:speed 300 0 true",
                        "time set day",
                        "weather clear",
                        "tp @s 0 -60 0 35 12")) {
                    AtomicReference<Boolean> succeeded = new AtomicReference<>();
                    server.getCommands().performPrefixedCommand(source.withCallback((ok, result) -> succeeded.set(ok)), command);
                    if (!Boolean.TRUE.equals(succeeded.get())) throw new AssertionError("Fixture command failed: " + command);
                }
                preparation.complete(null);
            } catch (Throwable failure) {
                preparation.completeExceptionally(failure);
            }
        });
    }

    /** Server completion alone does not guarantee the client has received the inventory update. */
    public static boolean prepared(Minecraft mc) {
        if (!ready(mc) || preparation == null || !preparation.isDone()) return false;
        preparation.join();
        var snapshot = InventorySnapshot.capture();
        return snapshot.count("minecraft:ender_pearl") == 12 && snapshot.count("minecraft:totem_of_undying") == 3
                && snapshot.count("minecraft:golden_apple") == 12 && snapshot.count("minecraft:arrow") == 64
                && mc.player.hasEffect(net.minecraft.world.effect.MobEffects.SPEED)
                && snapshot.armor().stream().anyMatch(piece -> piece.slot() == EquipmentSlot.HEAD
                    && piece.durability().maximum() == 363 && piece.durability().remaining() == 63);
    }

    /** Checks real read-only inventory state, then injects messages through the actual Fabric GAME event. */
    public static void verify(Minecraft mc) {
        if (!prepared(mc)) throw new AssertionError("Fixture inventory has not synchronized");
        var inventory = InventorySnapshot.capture();
        int mainPearls = mc.player.getInventory().getNonEquipmentItems().stream()
                .filter(stack -> stack.is(net.minecraft.world.item.Items.ENDER_PEARL)).mapToInt(stack -> stack.getCount()).sum();
        if (mainPearls != 8 || mc.player.getOffhandItem().getCount() != 4 || inventory.count("minecraft:ender_pearl") != 12)
            throw new AssertionError("Item Counter must include the offhand exactly once");
        if (inventory.count("minecraft:diamond_helmet") != 0)
            throw new AssertionError("Equipped armor must not enter Item Counter");
        int[] remaining = {63, 348, 395, 389};
        int[] maximum = {363, 528, 495, 429};
        for (int i = 0; i < 4; i++) {
            var durability = inventory.armor().get(i).durability();
            if (durability.remaining() != remaining[i] || durability.maximum() != maximum[i])
                throw new AssertionError("Live armor durability mismatch at slot " + i);
        }
        var config = BloodHelperClient.config();
        config.session.enabled = true;
        config.session.mode = "FFA";
        SessionTracker.reset();
        SessionTracker.tick(mc, config);
        receive(Component.literal("Ты получил 18 монет за убийство игрока HumsterMangok с множителем x1.5"), false);
        Component denial = Component.literal("За этого игрока пока нельзя получить монеты. Подожди 340 секунд");
        receive(denial, false);
        receive(denial, false); // Re-delivery of this same event object must not count twice.
        receive(Component.literal(denial.getString()), false); // A separate, equally worded receipt is another kill.
        receive(Component.translatable("death.attack.player", Component.literal("HumsterMangok"),
                Component.literal(mc.player.getGameProfile().name())), false);
        receive(Component.literal("Ты получил 18 монет за убийство игрока HumsterMangok с множителем x1.5"), true);
        var session = SessionTracker.snapshot();
        if (!session.mode().equals("FFA") || session.kills() != 3 || session.rewardedKills() != 1)
            throw new AssertionError("Live FFA receipt integration: expected 3 total, 1 rewarded, got " + session);
        System.out.println("[BLOODHELPER_SMOKE] isolated-world inventory, armor, and FFA listener checks passed");
    }

    /** Optional cleanup after screenshots; keeps the disposable save as evidence. */
    public static void finish(Minecraft mc) {
        requireIsolatedDirectory(mc);
        if (!isFixtureServer(mc)) throw new IllegalStateException("Refusing to disconnect a non-fixture server");
        BloodHelperClient.config().session.mode = originalSessionMode;
        BloodHelperClient.config().session.enabled = originalSessionEnabled;
        mc.level.disconnect(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
        mc.disconnect(new TitleScreen(), false);
    }

    private static void receive(Component message, boolean overlay) {
        ClientReceiveMessageEvents.GAME.invoker().onReceiveGameMessage(message, overlay);
    }

    private static boolean isFixtureServer(Minecraft mc) {
        if (worldName == null || mc.getSingleplayerServer() == null) return false;
        Path expected = mc.gameDirectory.toPath().toAbsolutePath().normalize().resolve("saves").resolve(worldName);
        return mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(expected);
    }

    private static void requireIsolatedDirectory(Minecraft mc) {
        Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
        if (directory.getFileName() == null || !directory.getFileName().toString().equals("run-smoke"))
            throw new IllegalStateException("HUD fixture can run only in the isolated run-smoke directory");
    }
}
