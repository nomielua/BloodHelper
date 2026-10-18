package ru.bloodhelper.client.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConfigStoreTest {
    @TempDir Path temporary;
    @Test void corruptConfigIsPreservedAndDefaultsRemainUsable() throws Exception {
        Path directory = temporary.resolve("bloodhelper");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("client.json"), "{broken json");
        ConfigStore.init(temporary);
        assertNotNull(ConfigStore.get().music);
        try (var files = Files.list(directory)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("client-invalid-")));
        }
        ConfigStore.save();
        assertTrue(Files.readString(directory.resolve("client.json")).contains("lyricsMode"));
    }
    @Test void profileRoundTripRestoresMusicAndHudSettings() throws Exception {
        ConfigStore.init(temporary);
        ConfigStore.get().music.lyricsMode = "BOTH";
        ConfigStore.get().music.position.x = .73;
        ConfigStore.get().cooldowns.enabled = false;
        ConfigStore.saveProfile("Музыка");
        ConfigStore.get().music.lyricsMode = "OFF";
        ConfigStore.get().music.position.x = .1;
        ConfigStore.get().cooldowns.enabled = true;
        assertTrue(ConfigStore.loadProfile("Музыка"));
        assertEquals("BOTH", ConfigStore.get().music.lyricsMode);
        assertEquals(.73, ConfigStore.get().music.position.x);
        assertFalse(ConfigStore.get().cooldowns.enabled);
        assertEquals(java.util.List.of("Музыка"), ConfigStore.profiles());
    }
    @Test void invalidProfileCannotEscapeProfileDirectoryOrReplaceCurrentConfig() throws Exception {
        ConfigStore.init(temporary);
        assertThrows(IllegalArgumentException.class, () -> ConfigStore.saveProfile("../escape"));
        assertThrows(IllegalArgumentException.class, () -> ConfigStore.saveProfile("/tmp/escape"));
        ClientConfig before = ConfigStore.get();
        assertFalse(ConfigStore.loadProfile("../escape"));
        assertSame(before, ConfigStore.get());
        assertFalse(Files.exists(temporary.resolve("escape.json")));
    }
    @Test void oldOrManuallyEditedConfigCannotIntroduceInvalidRenderValues() {
        ClientConfig config = new ClientConfig();
        config.music.position.scale = Double.NaN;
        config.music.position.x = Double.POSITIVE_INFINITY;
        config.music.lyricLines = Integer.MAX_VALUE;
        config.music.lyricsMode = null;
        config.pvp.cooldownGroup = "../bad";
        config.cooldowns = null;
        config.sanitize();
        assertTrue(Double.isFinite(config.music.position.scale));
        assertTrue(Double.isFinite(config.music.position.x));
        assertEquals(6, config.music.lyricLines);
        assertNotNull(config.music.lyricsMode);
        assertNotNull(config.cooldowns);
        assertEquals("minecraft:trident", config.pvp.cooldownGroup);
    }

    @Test void version104ConfigKeepsChosenThemeAndMusicWhileAddingNewHudDefaults() throws Exception {
        Path directory = temporary.resolve("bloodhelper");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("client.json"), """
                {
                  "schemaVersion": 2,
                  "accent": %d,
                  "backgroundColor": %d,
                  "guiScale": 0.85,
                  "cooldowns": {"enabled": false, "alignment": "RIGHT", "bottom": true,
                    "position": {"x": 0.1, "y": 0.2, "scale": 0.75}},
                  "music": {"lyricsMode": "BOTH", "lyricOffsetMs": 750,
                    "position": {"x": 0.73, "y": 0.11, "scale": 1.25}}
                }
                """.formatted(0xFF43785F, 0xFF20100C));
        ConfigStore.init(temporary);
        ClientConfig c = ConfigStore.get();
        assertEquals(0xFF43785F, c.accent);
        assertEquals(0xFF20100C, c.backgroundColor);
        assertEquals(.85, c.guiScale);
        assertFalse(c.cooldowns.enabled);
        assertEquals("RIGHT", c.cooldowns.alignment);
        assertTrue(c.cooldowns.bottom);
        assertEquals(.1, c.cooldowns.position.x);
        assertEquals(.75, c.cooldowns.position.scale);
        assertEquals("BOTH", c.music.lyricsMode);
        assertEquals(750, c.music.lyricOffsetMs);
        assertEquals(.73, c.music.position.x);
        assertEquals(1.25, c.music.position.scale);
        assertTrue(c.armor.enabled);
        assertTrue(c.itemCounter.enabled);
        assertTrue(c.notifications.enabled);
        assertTrue(c.performance.enabled);
        assertTrue(c.session.enabled);
        assertFalse(c.notifications.sound);
        assertEquals("FFA", c.session.mode);
        assertEquals(java.util.List.of("minecraft:totem_of_undying", "minecraft:ender_pearl",
                "minecraft:golden_apple", "minecraft:arrow"), c.itemCounter.items);
        ConfigStore.save();
        ConfigStore.init(temporary);
        assertEquals("BOTH", ConfigStore.get().music.lyricsMode);
        assertEquals("FFA", ConfigStore.get().session.mode);
        assertEquals(0xFF43785F, ConfigStore.get().accent);
    }

    @Test void profileRoundTripRestoresAllNewModulesAndPersistsLoadedProfile() throws Exception {
        ConfigStore.init(temporary);
        ClientConfig c = ConfigStore.get();
        c.armor.enabled = false;
        c.armor.durabilityMode = "POINTS";
        c.armor.showNames = true;
        c.armor.warnPercent = 27;
        c.armor.position.x = .34;
        c.armor.position.scale = 1.4;
        c.itemCounter.enabled = false;
        c.itemCounter.items = new java.util.ArrayList<>(java.util.List.of("minecraft:wind_charge", "bloodstone:token"));
        c.itemCounter.lowCount = 5;
        c.itemCounter.hideZero = true;
        c.itemCounter.alignment = "RIGHT";
        c.itemCounter.bottom = false;
        c.itemCounter.position.y = .24;
        c.notifications.enabled = false;
        c.notifications.effectWarnings = false;
        c.notifications.sound = true;
        c.notifications.maxVisible = 4;
        c.notifications.durationSeconds = 7;
        c.notifications.position.x = .8;
        c.performance.enabled = false;
        c.performance.showGraph = false;
        c.performance.showPing = false;
        c.performance.position.y = .45;
        c.session.enabled = false;
        c.session.mode = "MEETUPS";
        c.session.showKd = false;
        c.session.resetOnDisconnect = false;
        c.session.killMessageTemplate = "[PvP] {killer} убил {victim}";
        c.session.position.scale = .8;
        ConfigStore.saveProfile("Meetups HUD");

        ConfigStore.init(temporary); // Only the profile exists; the live config starts with defaults.
        assertTrue(ConfigStore.get().armor.enabled);
        assertEquals("FFA", ConfigStore.get().session.mode);
        assertTrue(ConfigStore.loadProfile("Meetups HUD"));
        ConfigStore.init(temporary); // Loading also saves the active config for the next launch.
        ClientConfig loaded = ConfigStore.get();
        assertFalse(loaded.armor.enabled);
        assertEquals("POINTS", loaded.armor.durabilityMode);
        assertTrue(loaded.armor.showNames);
        assertEquals(27, loaded.armor.warnPercent);
        assertEquals(.34, loaded.armor.position.x);
        assertEquals(1.4, loaded.armor.position.scale);
        assertFalse(loaded.itemCounter.enabled);
        assertEquals(java.util.List.of("minecraft:wind_charge", "bloodstone:token"), loaded.itemCounter.items);
        assertEquals(5, loaded.itemCounter.lowCount);
        assertTrue(loaded.itemCounter.hideZero);
        assertEquals("RIGHT", loaded.itemCounter.alignment);
        assertFalse(loaded.itemCounter.bottom);
        assertEquals(.24, loaded.itemCounter.position.y);
        assertFalse(loaded.notifications.enabled);
        assertFalse(loaded.notifications.effectWarnings);
        assertTrue(loaded.notifications.sound);
        assertEquals(4, loaded.notifications.maxVisible);
        assertEquals(7, loaded.notifications.durationSeconds);
        assertEquals(.8, loaded.notifications.position.x);
        assertFalse(loaded.performance.enabled);
        assertFalse(loaded.performance.showGraph);
        assertFalse(loaded.performance.showPing);
        assertEquals(.45, loaded.performance.position.y);
        assertFalse(loaded.session.enabled);
        assertEquals("MEETUPS", loaded.session.mode);
        assertFalse(loaded.session.showKd);
        assertFalse(loaded.session.resetOnDisconnect);
        assertEquals("[PvP] {killer} убил {victim}", loaded.session.killMessageTemplate);
        assertEquals(.8, loaded.session.position.scale);
        loaded.itemCounter.items.add("minecraft:arrow"); // The GUI can edit a deserialized list.
        assertEquals(3, loaded.itemCounter.items.size());
    }

    @Test void newHudSettingsRejectInvalidPositionsThresholdsAndItemIds() {
        ClientConfig c = new ClientConfig();
        c.armor.position = null;
        c.armor.durabilityMode = "BROKEN";
        c.armor.warnPercent = -10;
        c.itemCounter.position.x = Double.NaN;
        c.itemCounter.position.y = 42;
        c.itemCounter.position.scale = Double.POSITIVE_INFINITY;
        c.itemCounter.alignment = null;
        c.itemCounter.lowCount = 999;
        c.itemCounter.items = new java.util.ArrayList<>(java.util.Arrays.asList(null, "", " ../escape ",
                "MINECRAFT:arrow", "minecraft:arrow", " minecraft:arrow ", "minecraft:ender_pearl",
                "minecraft:golden_apple", "minecraft:totem_of_undying", "minecraft:wind_charge",
                "minecraft:experience_bottle", "minecraft:spectral_arrow", "minecraft:enchanted_golden_apple",
                "minecraft:diamond"));
        c.notifications.alignment = "SIDEWAYS";
        c.notifications.effectSeconds = 0;
        c.notifications.durationSeconds = 999;
        c.notifications.maxVisible = -1;
        c.performance.position.scale = -4;
        c.session.mode = "unknown";
        c.session.killMessageTemplate = "  {killer}\n killed\t {victim}  ";
        c.sanitize();
        assertEquals(new ClientConfig.ArmorSettings().position.x, c.armor.position.x);
        assertEquals(new ClientConfig.ArmorSettings().position.y, c.armor.position.y);
        assertEquals("PERCENT", c.armor.durabilityMode);
        assertEquals(1, c.armor.warnPercent);
        assertEquals(new ClientConfig.ItemCounterSettings().position.x, c.itemCounter.position.x);
        assertEquals(1, c.itemCounter.position.y);
        assertEquals(1, c.itemCounter.position.scale);
        assertEquals("CENTER", c.itemCounter.alignment);
        assertEquals(64, c.itemCounter.lowCount);
        assertEquals(java.util.List.of("minecraft:arrow", "minecraft:ender_pearl", "minecraft:golden_apple",
                "minecraft:totem_of_undying", "minecraft:wind_charge", "minecraft:experience_bottle",
                "minecraft:spectral_arrow", "minecraft:enchanted_golden_apple"), c.itemCounter.items);
        assertEquals("RIGHT", c.notifications.alignment);
        assertEquals(1, c.notifications.effectSeconds);
        assertEquals(10, c.notifications.durationSeconds);
        assertEquals(1, c.notifications.maxVisible);
        assertEquals(.5, c.performance.position.scale);
        assertEquals("FFA", c.session.mode);
        assertEquals("{killer} killed {victim}", c.session.killMessageTemplate);
        c.itemCounter.items.clear();
        c.session.killMessageTemplate = "x".repeat(257);
        c.sanitize();
        assertTrue(c.itemCounter.items.isEmpty()); // Intentionally hiding all tracked items is valid.
        assertEquals("", c.session.killMessageTemplate);
    }

    @Test void explicitNullNewModuleSectionsAndItemListRecoverToUsableDefaults() {
        ClientConfig c = new ClientConfig();
        c.armor = null;
        c.itemCounter = null;
        c.notifications = null;
        c.performance = null;
        c.session = null;
        c.sanitize();
        assertNotNull(c.armor.position);
        assertNotNull(c.itemCounter.position);
        assertNotNull(c.notifications.position);
        assertNotNull(c.performance.position);
        assertNotNull(c.session.position);
        c.itemCounter.items = null;
        c.session.killMessageTemplate = null;
        c.sanitize();
        assertEquals(4, c.itemCounter.items.size());
        assertEquals("", c.session.killMessageTemplate);
    }
}
