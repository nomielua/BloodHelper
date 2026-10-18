package ru.bloodhelper.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ru.bloodhelper.client.config.ClientConfig;

class HudWidgetsTest {
    @Test void resetRestoresEveryWidgetAnchorWithoutChangingModuleOrThemeChoices() {
        ClientConfig c = new ClientConfig();
        c.accent = 0xFF337755;
        c.cooldowns.enabled = false;
        c.pvp.enabled = false;
        c.music.enabled = false;
        c.music.lyricsMode = "BOTH";
        c.armor.enabled = false;
        c.armor.durabilityMode = "POINTS";
        c.itemCounter.enabled = false;
        c.itemCounter.items = new java.util.ArrayList<>(List.of("minecraft:wind_charge"));
        c.notifications.enabled = false;
        c.notifications.sound = true;
        c.performance.enabled = false;
        c.performance.showGraph = false;
        c.session.enabled = false;
        c.session.mode = "MEETUPS";
        c.cooldowns.alignment = "RIGHT";
        c.cooldowns.bottom = true;
        c.pvp.alignment = "LEFT";
        c.pvp.bottom = true;
        for (var settings : List.of(c.armor, c.itemCounter, c.notifications, c.performance, c.session)) {
            settings.alignment = "CENTER";
            settings.bottom = !settings.bottom;
            settings.background = false;
            settings.shadow = false;
        }
        var before = HudWidgets.all(c);
        assertEquals(8, before.size());
        assertEquals(Set.of("cooldowns", "pvp", "music", "armor", "itemCounter", "notifications", "performance", "session"),
                before.stream().map(HudWidgets.Widget::key).collect(java.util.stream.Collectors.toSet()));
        for (var widget : before) {
            widget.position().x = .44;
            widget.position().y = .66;
            widget.position().scale = 1.7;
        }

        HudWidgets.reset(c);

        ClientConfig defaults = new ClientConfig();
        for (var widget : HudWidgets.all(c)) {
            var expected = HudWidgets.get(widget.key(), defaults);
            assertEquals(expected.position().x, widget.position().x, widget.key());
            assertEquals(expected.position().y, widget.position().y, widget.key());
            assertEquals(expected.position().scale, widget.position().scale, widget.key());
            assertEquals(expected.alignment(), widget.alignment(), widget.key());
            assertEquals(expected.bottom(), widget.bottom(), widget.key());
            assertNotSame(before.stream().filter(old -> old.key().equals(widget.key())).findFirst().orElseThrow().position(),
                    widget.position(), widget.key());
        }
        assertEquals(0xFF337755, c.accent);
        assertFalse(c.cooldowns.enabled);
        assertFalse(c.pvp.enabled);
        assertFalse(c.music.enabled);
        assertEquals("BOTH", c.music.lyricsMode);
        for (var settings : List.of(c.armor, c.itemCounter, c.notifications, c.performance, c.session)) {
            assertFalse(settings.enabled);
            assertFalse(settings.background);
            assertFalse(settings.shadow);
        }
        assertEquals("POINTS", c.armor.durabilityMode);
        assertEquals(List.of("minecraft:wind_charge"), c.itemCounter.items);
        assertTrue(c.notifications.sound);
        assertFalse(c.performance.showGraph);
        assertEquals("MEETUPS", c.session.mode);
    }
}
