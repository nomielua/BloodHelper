package ru.bloodhelper.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import ru.bloodhelper.client.config.*;
import ru.bloodhelper.client.gui.*;
import ru.bloodhelper.client.hud.HudRenderer;
import ru.bloodhelper.client.hud.Notifications;
import ru.bloodhelper.client.hud.SessionTracker;
import ru.bloodhelper.client.music.*;
import ru.bloodhelper.client.lyrics.LyricsService;
import ru.bloodhelper.client.lyrics.KineticLyricsRenderer;

public final class BloodHelperClient implements ClientModInitializer {
    public static final String MOD_ID = "bloodhelper";
    public static final String VERSION = "1.1.0";
    private static final MediaService MEDIA = new MediaService();
    private static final LyricsService LYRICS = new LyricsService();
    private static KeyMapping menu;
    private static MediaSnapshot lastMedia;
    private static long observedAt;
    private static int appliedKey;
    @Override public void onInitializeClient() {
        ConfigStore.init(FabricLoader.getInstance().getConfigDir());
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        appliedKey = config().menuKey;
        menu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bloodhelper.menu", InputConstants.Type.KEYSYM, appliedKey, category));
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"), HudRenderer::render);
        SessionTracker.register();
        KineticLyricsRenderer.register(LYRICS, MEDIA, BloodHelperClient::config);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (menu.consumeClick()) {
                if (client.screen instanceof BloodHelperScreen screen) screen.onClose();
                else client.setScreen(new BloodHelperScreen(client.screen));
            }
            Notifications.tick(client, config());
            SessionTracker.tick(client, config());
            MEDIA.setEnabled(config().music.enabled);
            LYRICS.update(MEDIA.snapshot(), config().music);
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof ChatScreen) {
                ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !HudRenderer.clickMusic(event.x(), event.y(), event.button()));
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { ConfigStore.save(); LYRICS.close(); MEDIA.close(); });
    }
    public static ClientConfig config() { return ConfigStore.get(); }
    public static void saveConfig() {
        ConfigStore.save();
        if (menu != null && appliedKey != config().menuKey) {
            appliedKey = config().menuKey;
            menu.setKey(InputConstants.Type.KEYSYM.getOrCreate(appliedKey));
            KeyMapping.resetMapping();
        }
    }
    public static List<String> profiles() { return ConfigStore.profiles(); }
    public static void saveProfile(String name) throws java.io.IOException { ConfigStore.saveProfile(name); }
    public static boolean loadProfile(String name) { boolean result = ConfigStore.loadProfile(name); if (result) saveConfig(); return result; }
    public static boolean deleteProfile(String name) { return ConfigStore.deleteProfile(name); }
    public static void openHudEditor(Screen parent) { Minecraft.getInstance().setScreen(new HudEditorScreen(parent)); }
    public static MediaService media() { return MEDIA; }
    public static LyricsService lyrics() { return LYRICS; }
    public static String mediaStatus() { return MEDIA.status(); }
    public static void musicCommand(String command) {
        switch (command) { case "previous" -> MEDIA.previous(); case "next" -> MEDIA.next(); default -> MEDIA.playPause(); }
    }
    public static long playbackPosition() {
        return playbackPosition(MEDIA.snapshot());
    }
    public static long playbackPosition(MediaSnapshot sample) {
        long now = System.nanoTime();
        if (sample != lastMedia) { lastMedia = sample; observedAt = now; }
        long position = sample.positionMs() + (sample.playing() ? Math.min(1500, (now - observedAt) / 1_000_000) : 0);
        return sample.durationMs() > 0 ? Math.min(position, sample.durationMs()) : position;
    }
}
