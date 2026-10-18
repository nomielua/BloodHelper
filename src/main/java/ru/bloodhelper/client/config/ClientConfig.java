package ru.bloodhelper.client.config;

public final class ClientConfig {
    public int schemaVersion = 2;
    public String language = "ru";
    public int accent = 0xFFCC171D;
    public boolean textShadow = true;
    public boolean background = true;
    public int backgroundColor = 0xFF100D0E;
    public int backgroundAlpha = 200;
    public boolean animations = true;
    public double guiScale = 1.0;
    public int menuKey = 344;
    public CooldownSettings cooldowns = new CooldownSettings();
    public PvpSettings pvp = new PvpSettings();
    public MusicSettings music = new MusicSettings();
    public ArmorSettings armor = new ArmorSettings();
    public ItemCounterSettings itemCounter = new ItemCounterSettings();
    public NotificationSettings notifications = new NotificationSettings();
    public PerformanceSettings performance = new PerformanceSettings();
    public SessionSettings session = new SessionSettings();

    public static class HudPosition {
        public double x;
        public double y;
        public double scale = 1;
        public HudPosition() {}
        public HudPosition(double x, double y) { this.x = x; this.y = y; }
    }
    public static final class CooldownSettings {
        public boolean enabled = true;
        public boolean showNames = true;
        public boolean showProgress = true;
        public boolean showUnknown = true;
        public boolean shadow = true;
        public boolean background = true;
        public String alignment = "LEFT";
        public boolean bottom = false;
        public HudPosition position = new HudPosition(0.02, 0.35);
    }
    public static final class PvpSettings {
        public boolean enabled = true;
        public String cooldownGroup = "minecraft:trident";
        public boolean showProgress = true;
        public boolean shadow = true;
        public boolean background = true;
        public String alignment = "RIGHT";
        public boolean bottom = false;
        public HudPosition position = new HudPosition(0.98, 0.08);
    }
    public static final class MusicSettings {
        public boolean enabled = true;
        public boolean showCover = true;
        public boolean showProgress = true;
        public boolean showControls = true;
        public boolean hideWhenIdle = false;
        public boolean compact = false;
        public String lyricsMode = "ISLAND";
        public String lyricAnimation = "FLOW";
        public String lyricColor = "THEME";
        public boolean splitLyrics = true;
        public int lyricOffsetMs = 0;
        public int lyricLines = 3;
        public double lyricDistance = 3.0;
        public double lyricSpread = 65.0;
        public double lyricFloat = 0.3;
        public HudPosition position = new HudPosition(0.5, 0.035);
    }

    public static class WidgetSettings {
        public boolean enabled = true;
        public boolean background = true;
        public boolean shadow = true;
        public String alignment = "LEFT";
        public boolean bottom = false;
        public HudPosition position = new HudPosition(.02, .5);
    }
    public static final class ArmorSettings extends WidgetSettings {
        public String durabilityMode = "PERCENT";
        public boolean showNames = false;
        public boolean hideWhenEmpty = true;
        public int warnPercent = 20;
        public ArmorSettings() { position = new HudPosition(.02, .63); }
    }
    public static final class ItemCounterSettings extends WidgetSettings {
        public java.util.List<String> items = new java.util.ArrayList<>(java.util.List.of(
                "minecraft:totem_of_undying", "minecraft:ender_pearl", "minecraft:golden_apple", "minecraft:arrow"));
        public boolean showNames = false;
        public boolean hideZero = false;
        public int lowCount = 3;
        public ItemCounterSettings() { position = new HudPosition(.5, .12); alignment = "CENTER"; bottom = true; }
    }
    public static final class NotificationSettings extends WidgetSettings {
        public boolean armorWarnings = true;
        public boolean itemWarnings = true;
        public boolean effectWarnings = true;
        public boolean sound = false;
        public int effectSeconds = 10;
        public int durationSeconds = 4;
        public int maxVisible = 3;
        public NotificationSettings() { position = new HudPosition(.98, .78); alignment = "RIGHT"; }
    }
    public static final class PerformanceSettings extends WidgetSettings {
        public boolean showFps = true;
        public boolean showPing = true;
        public boolean showFrameTime = true;
        public boolean showGraph = true;
        public PerformanceSettings() { position = new HudPosition(.02, .035); }
    }
    public static final class SessionSettings extends WidgetSettings {
        public String mode = "FFA";
        public boolean showTime = true;
        public boolean showKd = true;
        public boolean showStreak = true;
        public boolean resetOnDisconnect = true;
        // Literal server-message template. {killer} and {victim} are player-name slots.
        public String killMessageTemplate = "";
        public SessionSettings() { position = new HudPosition(.98, .35); alignment = "RIGHT"; }
    }

    public void sanitize() {
        if (schemaVersion < 2) {
            // Upgrade the former defaults while retaining explicitly chosen colours.
            if ((accent & 0xFFFFFF) == 0xE13F4F) accent = 0xFFCC171D;
            if ((backgroundColor & 0xFFFFFF) == 0x101116) backgroundColor = 0xFF100D0E;
        }
        schemaVersion = 2;
        language = "en".equals(language) ? "en" : "ru";
        accent |= 0xFF000000;
        backgroundColor |= 0xFF000000;
        backgroundAlpha = Math.clamp(backgroundAlpha, 0, 255);
        guiScale = finite(guiScale, 1, 0.65, 1.05);
        if (menuKey < 32 || menuKey > 348) menuKey = 344;
        if (cooldowns == null) cooldowns = new CooldownSettings();
        if (pvp == null) pvp = new PvpSettings();
        if (music == null) music = new MusicSettings();
        cooldowns.position = position(cooldowns.position, .02, .35);
        pvp.position = position(pvp.position, .98, .08);
        music.position = position(music.position, .5, .035);
        cooldowns.alignment = alignment(cooldowns.alignment);
        pvp.alignment = alignment(pvp.alignment);
        if (pvp.cooldownGroup == null || !pvp.cooldownGroup.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) pvp.cooldownGroup = "minecraft:trident";
        music.lyricsMode = oneOf(music.lyricsMode, "OFF", "ISLAND", "WORLD", "BOTH");
        music.lyricAnimation = oneOf(music.lyricAnimation, "FLOW", "TYPEWRITER", "POP", "SLIDE", "FADE");
        music.lyricColor = oneOf(music.lyricColor, "THEME", "GRADIENT", "WHITE");
        music.lyricOffsetMs = Math.clamp(music.lyricOffsetMs, -5000, 5000);
        music.lyricLines = Math.clamp(music.lyricLines, 1, 6);
        music.lyricDistance = finite(music.lyricDistance, 3, 1.2, 7);
        music.lyricSpread = finite(music.lyricSpread, 65, 20, 85);
        music.lyricFloat = finite(music.lyricFloat, .3, 0, 2);
        if (armor == null) armor = new ArmorSettings();
        if (itemCounter == null) itemCounter = new ItemCounterSettings();
        if (notifications == null) notifications = new NotificationSettings();
        if (performance == null) performance = new PerformanceSettings();
        if (session == null) session = new SessionSettings();
        widget(armor, new ArmorSettings()); widget(itemCounter, new ItemCounterSettings());
        widget(notifications, new NotificationSettings()); widget(performance, new PerformanceSettings());
        widget(session, new SessionSettings());
        armor.durabilityMode = oneOf(armor.durabilityMode, "PERCENT", "POINTS");
        armor.warnPercent = Math.clamp(armor.warnPercent, 1, 90);
        itemCounter.lowCount = Math.clamp(itemCounter.lowCount, 0, 64);
        if (itemCounter.items == null) itemCounter.items = new ItemCounterSettings().items;
        itemCounter.items = new java.util.ArrayList<>(itemCounter.items.stream()
                .filter(java.util.Objects::nonNull).map(String::strip)
                .filter(id -> id.length() <= 128 && id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
                .distinct().limit(8).toList());
        notifications.effectSeconds = Math.clamp(notifications.effectSeconds, 1, 60);
        notifications.durationSeconds = Math.clamp(notifications.durationSeconds, 2, 10);
        notifications.maxVisible = Math.clamp(notifications.maxVisible, 1, 4);
        session.mode = oneOf(session.mode, "FFA", "SMP", "MEETUPS");
        if (session.killMessageTemplate == null) session.killMessageTemplate = "";
        session.killMessageTemplate = session.killMessageTemplate.replaceAll("[\\p{Cntrl}]", "").strip();
        if (session.killMessageTemplate.length() > 256) session.killMessageTemplate = "";

    }
    private static void widget(WidgetSettings value, WidgetSettings defaults) {
        value.position = position(value.position, defaults.position.x, defaults.position.y);
        value.alignment = oneOf(value.alignment, defaults.alignment, "LEFT", "CENTER", "RIGHT");
    }
    private static HudPosition position(HudPosition value, double x, double y) {
        if (value == null) value = new HudPosition(x, y);
        value.x = finite(value.x, x, 0, 1);
        value.y = finite(value.y, y, 0, 1);
        value.scale = finite(value.scale, 1, .5, 2);
        return value;
    }
    private static double finite(double value, double fallback, double min, double max) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
    private static String alignment(String value) { return oneOf(value, "LEFT", "CENTER", "RIGHT"); }
    private static String oneOf(String value, String... options) {
        for (String option : options) if (option.equals(value)) return option;
        return options[0];
    }
}
