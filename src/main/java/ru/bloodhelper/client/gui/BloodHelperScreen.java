package ru.bloodhelper.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.hud.NotificationWidget;
import ru.bloodhelper.client.hud.Notifications;
import ru.bloodhelper.client.hud.SessionTracker;

import java.io.IOException;
import java.util.*;
import java.util.function.*;

import static ru.bloodhelper.client.gui.UiDraw.*;

/** Expensive 4.0's 400x250 composition, ported at 2x logical resolution to 1.21.11.
 * Source: dest4590/Expensive_4.0, 3cf31b7; see docs/EXPENSIVE_GUI_RU.md.
 * Only visual composition/assets are used. All settings bind our own client config.
 */
public final class BloodHelperScreen extends Screen {
    public static final int MENU_WIDTH = 800, MENU_HEIGHT = 500;
    private static final int W = MENU_WIDTH, H = MENU_HEIGHT;
    private static final int CONTENT_X = 190, CONTENT_TOP = 78, CONTENT_BOTTOM = 478, COLUMN_W = 274;
    private static final int TEXT = 0xFFD4D6E1, MUTED = 0xFF96929C, FAINT = 0xFF797581;
    private static final int PANEL = 0x23110F16, HEADER = 0xF51F1922, LINE = 0xA63C303B;
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("bloodhelper", "textures/logo.png");
    private enum Page { VISUALS, APPEARANCE, PROFILES, ABOUT }
    private enum Module {
        COOLDOWNS("cooldowns"), MUSIC("music"), PVP("pvp"), ARMOR("armor"),
        ITEM_COUNTER("itemCounter"), NOTIFICATIONS("notifications"), PERFORMANCE("performance"), SESSION("session");
        final String id;
        Module(String id) { this.id = id; }
    }
    private static final int MAX_SELECTED_ITEMS = 8;
    private static final List<ItemPreset> ITEM_PRESETS = List.of(
            new ItemPreset("totem", "minecraft:totem_of_undying", "Totem", "Тотем бессмертия"),
            new ItemPreset("pearl", "minecraft:ender_pearl", "Ender pearl", "Жемчуг Эндера"),
            new ItemPreset("gapple", "minecraft:golden_apple", "Golden apple", "Золотое яблоко"),
            new ItemPreset("enchantedGapple", "minecraft:enchanted_golden_apple", "Ench. golden apple", "Зачарованное золотое яблоко"),
            new ItemPreset("arrow", "minecraft:arrow", "Arrow", "Обычная стрела"),
            new ItemPreset("spectralArrow", "minecraft:spectral_arrow", "Spectral arrow", "Спектральная стрела"),
            new ItemPreset("expBottle", "minecraft:experience_bottle", "Experience bottle", "Пузырёк опыта"),
            new ItemPreset("windCharge", "minecraft:wind_charge", "Wind charge", "Заряд ветра"));
    private final Screen parent;
    private final List<Hit> hits = new ArrayList<>();
    private final List<FieldHit> fields = new ArrayList<>();
    private final List<Slider> sliders = new ArrayList<>();
    private final EnumSet<Module> expanded = EnumSet.noneOf(Module.class);
    private final Set<String> advanced = new HashSet<>();
    private final long openedAt = System.nanoTime();
    private Page page = Page.VISUALS;
    private GuiTextField search, profileName, pvpGroup, focused;
    private double menuScale = 1, originX, originY, mouseX, mouseY, scroll, maxScroll, profileScroll;
    private Slider dragging;
    private Dropdown dropdown;
    private boolean contentClip, capturingKey;
    private String notice = "", deleteCandidate = "";
    private long noticeUntil, deleteUntil, notificationPreviewUntil;
    private List<String> profiles = List.of();

    public BloodHelperScreen() { this(null); }
    public BloodHelperScreen(Screen parent) { super(Component.literal("BLOODHELPER")); this.parent = parent; }
    private ClientConfig cfg() { return BloodHelperClient.config(); }
    private boolean ru() { return !"en".equals(cfg().language); }
    private String tr(String russian, String english) { return ru() ? russian : english; }
    private int accent() { return alpha(cfg().accent, 255); }
    private int softAccent() { return blend(cfg().accent, 0xFFFFFF, .32); }

    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        String name = profileName == null ? "" : profileName.getValue();
        search = new GuiTextField(612, 20, 128, 18, "Search"); search.setMaxLength(80); search.setTextSize(13);
        search.setValue(query);
        search.setResponder(value -> { scroll = 0; dropdown = null; });
        profileName = new GuiTextField(209, 135, 300, 20, "Profile name");
        profileName.setMaxLength(40); profileName.setValue(name);
        profileName.setFilter(value -> value.matches("[\\p{L}\\p{N} _-]*"));
        pvpGroup = new GuiTextField(0, 0, COLUMN_W - 36, 20, "minecraft:trident");
        pvpGroup.setMaxLength(128); pvpGroup.setTextSize(13); pvpGroup.setValue(cfg().pvp.cooldownGroup);
        pvpGroup.setResponder(value -> { if (Identifier.tryParse(value) != null) cfg().pvp.cooldownGroup = value; });
        focus(null); refreshProfiles(); updateTransform();
    }

    public static double fittedScale(int width, int height, double preferred) {
        double fit = Math.min((width - 12.) / W, (height - 12.) / H);
        return Math.max(.1, Math.min(fit, fit * .96 * preferred));
    }
    private void updateTransform() {
        if (dragging != null) return;
        menuScale = fittedScale(width, height, cfg().guiScale);
        originX = (width - W * menuScale) / 2;
        originY = (height - H * menuScale) / 2;
        if (cfg().animations) {
            double t = Math.clamp((System.nanoTime() - openedAt) / 180_000_000., 0, 1);
            originY += 10 * Math.pow(1 - t, 3) * menuScale;
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        updateTransform(); mouseX = (mx - originX) / menuScale; mouseY = (my - originY) / menuScale;
        hits.clear(); fields.clear(); sliders.clear(); contentClip = false;
        g.fill(0, 0, width, height, 0x98000000);
        g.pose().pushMatrix();
        g.pose().translate((float)originX, (float)originY); g.pose().scale((float)menuScale, (float)menuScale);
        texture(g, "background", 0, 0, W, H, 800, 500, 0xFFFFFFFF);
        sidebar(g); header(g);
        switch (page) {
            case VISUALS -> modules(g);
            case APPEARANCE -> appearance(g);
            case PROFILES -> profiles(g);
            case ABOUT -> about(g);
        }
        if (System.currentTimeMillis() < noticeUntil) {
            rounded(g, 190, 481, 568, 17, 3, 0xED15111B);
            label(g, fit(notice, 11, 548), 200, 483, 11, MUTED);
        }
        drawDropdown(g);
        g.pose().popMatrix();
        if (notificationPreviewUntil != 0 && System.nanoTime() < notificationPreviewUntil) NotificationWidget.render(g, cfg(), false);
    }

    private static void frame(GuiGraphics g, int x, int y, int w, int h, int radius) {
        rounded(g,x,y,w,h,radius,PANEL);
        outline(g,x,y,w,h,radius,LINE);
    }
    private static void texture(GuiGraphics g, String name, int x, int y, int w, int h, int sw, int sh, int color) {
        Identifier id = Identifier.fromNamespaceAndPath("bloodhelper", "textures/expensive/" + name + ".png");
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, sw, sh, sw, sh, color);
    }
    private void logo(GuiGraphics g, int x, int y, int w) {
        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0, 0, w, Math.round(w * 336f / 2088), 2088, 336, 2088, 336);
    }
    private static void label(GuiGraphics g, String s, float x, float y, float size, int color) {
        CustomFont.regular().draw(g, s, x, y, size, color);
    }
    private static void strong(GuiGraphics g, String s, float x, float y, float size, int color) {
        CustomFont.medium().draw(g, s, x, y, size, color);
    }
    private static String fit(String s, float size, float width) { return CustomFont.regular().ellipsize(s, size, width); }
    private void sidebar(GuiGraphics g) {
        logo(g, 14, 22, 142);
        nav(g, Page.VISUALS, "Visuals", "render", 100);
        nav(g, Page.APPEARANCE, "Appearance", "palette", 138);
        nav(g, Page.PROFILES, "Profiles", "settings", 176);
        nav(g, Page.ABOUT, "About", "about", 214);
        button(g, "nav.hud", 18, 378, 134, 30, "HUD Editor", () -> BloodHelperClient.openHudEditor(this));
        texture(g, "steve", 18, 452, 28, 28, 50, 50, 0xFFFFFFFF);
        strong(g, fit(minecraft.getUser().getName(), 13, 102), 54, 453, 13, TEXT);
        label(g, "Fabric 1.21.11", 54, 470, 10, FAINT);
    }
    private void nav(GuiGraphics g, Page target, String title, String icon, int y) {
        boolean active = page == target;
        if (active) texture(g, "tab", 12, y, 146, 34, 146, 33, 0xFFFFFFFF);
        else if (contains(12, y, 146, 34)) rounded(g, 12, y, 146, 34, 5, 0x381F1A29);
        if (icon.equals("about")) UiIcons.draw(g, "about", 26, y + 8, 18, active ? softAccent() : TEXT);
        else texture(g, icon, 26, y + 8, 18, 18, icon.equals("render") ? 18 : 14, icon.equals("render") ? 18 : 14,
                active ? softAccent() : TEXT);
        strong(g, title, 56, y + 9, 14, active ? softAccent() : TEXT);
        hit("nav." + target.name().toLowerCase(Locale.ROOT), 12, y, 146, 34, () -> {
            page = target; scroll = 0; profileScroll = 0; dropdown = null; focus(null);
            if (page == Page.PROFILES) refreshProfiles();
        });
    }
    private void header(GuiGraphics g) {
        String title = switch (page) { case VISUALS -> "Visuals"; case APPEARANCE -> "Appearance"; case PROFILES -> "Profiles"; case ABOUT -> "About"; };
        strong(g, title, 190, 21, 16, TEXT);
        button(g, "language", 536, 13, 52, 30, ru() ? "RU" : "EN", () -> { cfg().language = ru() ? "en" : "ru"; save(); });
        if (page == Page.VISUALS) {
            border(g, 600, 13, 160, 30, 12, 0x54191A28, focused == search ? LINE : 0xFF11121C);
            field(g, search, 612, 19, 122, "search");
            texture(g, "search", 740, 23, 11, 11, 11, 11, TEXT);
        }
        UiIcons.draw(g, "close", 775, 22, 12, MUTED);
        hit("close", 770, 12, 26, 32, this::onClose);
    }
    private String moduleName(Module m) { return switch (m) {
        case COOLDOWNS -> "Cooldowns"; case MUSIC -> "Dynamic Island"; case PVP -> "PvP Timer";
        case ARMOR -> "Armor HUD"; case ITEM_COUNTER -> "Item Counter"; case NOTIFICATIONS -> "Notifications";
        case PERFORMANCE -> "Performance"; case SESSION -> "Session Stats";
    }; }
    private String moduleDescription(Module m) { return switch (m) {
        case COOLDOWNS -> "Серверные перезарядки предметов.";
        case PVP -> "Отсчёт PvP-режима сервера.";
        case MUSIC -> "Музыка, управление и текст песни.";
        case ARMOR -> "Прочность надетой брони.";
        case ITEM_COUNTER -> "Запас выбранных предметов.";
        case NOTIFICATIONS -> "Броня, предметы и ванильные эффекты.";
        case PERFORMANCE -> "FPS, задержка и время кадра.";
        case SESSION -> "Статистика текущей игровой сессии.";
    }; }
    private boolean enabled(Module m) { return switch (m) {
        case COOLDOWNS -> cfg().cooldowns.enabled; case MUSIC -> cfg().music.enabled; case PVP -> cfg().pvp.enabled;
        case ARMOR -> cfg().armor.enabled; case ITEM_COUNTER -> cfg().itemCounter.enabled;
        case NOTIFICATIONS -> cfg().notifications.enabled; case PERFORMANCE -> cfg().performance.enabled;
        case SESSION -> cfg().session.enabled;
    }; }
    private void toggleModule(Module m) {
        switch (m) {
            case COOLDOWNS -> cfg().cooldowns.enabled = !cfg().cooldowns.enabled;
            case MUSIC -> cfg().music.enabled = !cfg().music.enabled;
            case PVP -> cfg().pvp.enabled = !cfg().pvp.enabled;
            case ARMOR -> cfg().armor.enabled = !cfg().armor.enabled;
            case ITEM_COUNTER -> cfg().itemCounter.enabled = !cfg().itemCounter.enabled;
            case NOTIFICATIONS -> cfg().notifications.enabled = !cfg().notifications.enabled;
            case PERFORMANCE -> cfg().performance.enabled = !cfg().performance.enabled;
            case SESSION -> cfg().session.enabled = !cfg().session.enabled;
        }
        save();
    }
    private boolean matches(Module m, String query) {
        String alias = switch (m) {
            case COOLDOWNS -> "cooldowns кулдауны перезарядки предметы items";
            case MUSIC -> "dynamic island music музыка lyrics тексты песни";
            case PVP -> "pvp пвп timer таймер";
            case ARMOR -> "armor armour броня брони прочность durability percent points";
            case ITEM_COUNTER -> "item counter items предметы счетчик счётчик запас totem тотем pearl жемчуг apple яблоко arrow стрела опыт wind ветер";
            case NOTIFICATIONS -> "notifications alerts уведомления предупреждения sound звук effects эффекты";
            case PERFORMANCE -> "performance fps ping frametime frame time graph производительность фпс пинг задержка график кадр";
            case SESSION -> "session stats statistics сессия статистика ffa smp meetups митап убийства kills deaths смерти kd streak серия время";
        };
        return query.isBlank() || (moduleName(m).toLowerCase(Locale.ROOT) + " " + alias).contains(query);
    }
    private void modules(GuiGraphics g) {
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        int[] bottoms = {CONTENT_TOP, CONTENT_TOP};
        // Alternate visible results, including filtered searches. Four collapsed
        // cards per column occupy 390px of the existing 400px content viewport.
        List<Card> cards = new ArrayList<>();
        for (Module module : Module.values()) {
            if (!matches(module, query)) continue;
            List<Row> rows = expanded.contains(module) ? rows(module) : List.of();
            int column = cards.size() % 2;
            int height = 90 + rows.stream().mapToInt(Row::height).sum();
            cards.add(new Card(module, CONTENT_X + column * 294, bottoms[column], height, rows));
            bottoms[column] += height + 10;
        }
        maxScroll = Math.max(0, Math.max(bottoms[0], bottoms[1]) - 10 - CONTENT_BOTTOM);
        scroll = Math.clamp(scroll, 0, maxScroll);
        contentClip = true; g.enableScissor(180, CONTENT_TOP - 4, 773, CONTENT_BOTTOM);
        for (Card card : cards) renderCard(g, card, card.y - (int)scroll);
        g.disableScissor(); contentClip = false;
        if (cards.isEmpty()) label(g, tr("Ничего не найдено", "No matching modules"), 208, 110, 14, MUTED);
        scrollbar(g, 774, CONTENT_TOP, CONTENT_BOTTOM - CONTENT_TOP, scroll, maxScroll);
    }
    private void renderCard(GuiGraphics g, Card card, int y) {
        int x = card.x, w = COLUMN_W;
        frame(g, x, y, w, card.height, 10);
        rounded(g, x + 1, y + 1, w - 2, 35, 9, HEADER);
        g.fill(x + 1, y + 24, x + w - 1, y + 36, HEADER);
        texture(g, "ico", x + 18, y + 9, 18, 18, 18, 18, TEXT);
        strong(g, moduleName(card.module), x + 46, y + 10, 14, TEXT);
        UiIcons.draw(g, expanded.contains(card.module) ? "back" : "chevron", x + w - 29, y + 11, 13, FAINT);
        String id = card.module.id;
        hit(id + ".expand", x, y, w, 36, () -> { if (!expanded.remove(card.module)) expanded.add(card.module); dropdown = null; focus(null); });
        strong(g, "Enable", x + 18, y + 48, 14, TEXT);
        label(g, fit(moduleDescription(card.module), 11, w - 38), x + 18, y + 69, 11, MUTED);
        checkbox(g, id + ".enabled", x + w - 33, y + 48, enabled(card.module), () -> toggleModule(card.module));
        int rowY = y + 88;
        for (Row row : card.rows) { row.draw(g, x, rowY, w); rowY += row.height(); }
    }

    private List<Row> rows(Module m) {
        List<Row> r = new ArrayList<>();
        switch (m) {
        case COOLDOWNS -> {
            var c = cfg().cooldowns;
            r.add(bool("cooldowns.names", "Item names", "Названия рядом с иконками", () -> c.showNames, v -> c.showNames = v));
            r.add(bool("cooldowns.progress", "Progress bar", "Оставшееся время полосой", () -> c.showProgress, v -> c.showProgress = v));
            r.add(disclosure("cooldowns.advanced", "More settings"));
            if (advanced.contains("cooldowns.advanced")) {
                r.add(bool("cooldowns.shadow", "Text shadow", "Тень за текстом", () -> c.shadow, v -> c.shadow = v));
                r.add(bool("cooldowns.background", "Background", "Подложки таймеров", () -> c.background, v -> c.background = v));
                r.add(bool("cooldowns.unknown", "Unknown items", "Неизвестные группы сервера", () -> c.showUnknown, v -> c.showUnknown = v));
                r.add(choice("cooldowns.align", "Alignment", "Привязка по горизонтали", () -> c.alignment, v -> c.alignment = v, new String[]{"LEFT","CENTER","RIGHT"}, new String[]{"Left","Center","Right"}));
                r.add(bool("cooldowns.bottom", "Bottom anchor", "Отступ от нижнего края", () -> c.bottom, v -> c.bottom = v));
                positions(r, "cooldowns", c.position);
            }
        }
        case PVP -> {
            var p = cfg().pvp;
            r.add(bool("pvp.progress", "Progress bar", "Оставшееся время PvP", () -> p.showProgress, v -> p.showProgress = v));
            r.add(disclosure("pvp.advanced", "More settings"));
            if (advanced.contains("pvp.advanced")) {
                r.add(new Row(65, (g,x,y,w) -> {
                    strong(g, "Cooldown group", x + 18, y + 2, 14, TEXT);
                    border(g, x + 18, y + 26, w - 36, 28, 5, 0xFF15121C, Identifier.tryParse(pvpGroup.getValue()) == null ? 0xFFDC5867 : LINE);
                    field(g, pvpGroup, x + 24, y + 30, w - 48, "pvp.group");
                }));
                r.add(bool("pvp.shadow", "Text shadow", "Тень за текстом", () -> p.shadow, v -> p.shadow = v));
                r.add(bool("pvp.background", "Background", "Подложка таймера", () -> p.background, v -> p.background = v));
                r.add(choice("pvp.align", "Alignment", "Привязка по горизонтали", () -> p.alignment, v -> p.alignment = v, new String[]{"LEFT","CENTER","RIGHT"}, new String[]{"Left","Center","Right"}));
                r.add(bool("pvp.bottom", "Bottom anchor", "Отступ от нижнего края", () -> p.bottom, v -> p.bottom = v));
                positions(r, "pvp", p.position);
            }
        }
        case MUSIC -> {
            var p = cfg().music;
            r.add(choice("music.lyrics", "Lyrics", "Где показывать текст", () -> p.lyricsMode, v -> p.lyricsMode = v,
                    new String[]{"OFF","ISLAND","WORLD","BOTH"}, new String[]{"Off","Island","World (3D)","Island + 3D"}));
            r.add(bool("music.cover", "Album artwork", "Обложка текущего трека", () -> p.showCover, v -> p.showCover = v));
            r.add(bool("music.controls", "Playback controls", "Кнопки плеера в HUD", () -> p.showControls, v -> p.showControls = v));
            r.add(bool("music.progress", "Track progress", "Время и прогресс трека", () -> p.showProgress, v -> p.showProgress = v));
            r.add(bool("music.compact", "Compact island", "Уменьшенный вид островка", () -> p.compact, v -> p.compact = v));
            r.add(disclosure("music.advanced", "More settings"));
            if (advanced.contains("music.advanced")) {
                r.add(bool("music.idle", "Hide when idle", "Скрывать без музыки", () -> p.hideWhenIdle, v -> p.hideWhenIdle = v));
                if (!p.lyricsMode.equals("OFF")) {
                    r.add(choice("music.animation", "Animation", "Появление строк песни", () -> p.lyricAnimation, v -> p.lyricAnimation = v,
                            new String[]{"FLOW","FADE","TYPEWRITER","POP","SLIDE"}, new String[]{"Flow","Fade","Typewriter","Pop","Slide"}));
                    r.add(choice("music.color", "Lyric color", "Цвет текста песни", () -> p.lyricColor, v -> p.lyricColor = v,
                            new String[]{"THEME","GRADIENT","WHITE"}, new String[]{"Theme","Gradient","White"}));
                    r.add(bool("music.split", "Split long lines", "Разделять длинные строки", () -> p.splitLyrics, v -> p.splitLyrics = v));
                    r.add(slider("music.offset", "Lyrics offset", "Сдвиг синхронизации", () -> p.lyricOffsetMs, v -> p.lyricOffsetMs = (int)v, -5000,5000,100," ms"));
                }
                if (p.lyricsMode.equals("WORLD") || p.lyricsMode.equals("BOTH")) {
                    r.add(slider("music.lines", "World lines", "Число строк в мире", () -> p.lyricLines, v -> p.lyricLines = (int)v,1,6,1,""));
                    r.add(slider("music.distance", "Distance", "Расстояние до текста", () -> p.lyricDistance, v -> p.lyricDistance = v,1.2,7,.1," m"));
                    r.add(slider("music.height", "Height", "Высота текста", () -> p.lyricFloat, v -> p.lyricFloat = v,0,2,.1," m"));
                    r.add(slider("music.spread", "Spread", "Ширина строк вокруг игрока", () -> p.lyricSpread, v -> p.lyricSpread = v,20,85,1,"°"));
                }
                positions(r, "music", p.position);
            }
            if (BloodHelperClient.media().snapshot().available()) r.add(new Row(36, (g,x,y,w) -> {
                transport(g,"previous",x+18,y+3,68); transport(g,BloodHelperClient.media().snapshot().playing()?"pause":"play",x+103,y+3,68); transport(g,"next",x+188,y+3,68);
            }));
            r.add(new Row(32, (g,x,y,w) -> label(g, fit(musicStatus(),11,w-36),x+18,y+8,11,FAINT)));
        }
        case ARMOR -> {
            var p = cfg().armor;
            r.add(choice("armor.mode", "Durability", "Остаток прочности", () -> p.durabilityMode, v -> p.durabilityMode = v,
                    new String[]{"PERCENT","POINTS"}, new String[]{"Percent","Points"}));
            r.add(bool("armor.names", "Item names", "Названия надетой брони", () -> p.showNames, v -> p.showNames = v));
            r.add(bool("armor.hideEmpty", "Hide when empty", "Скрывать без надетой брони", () -> p.hideWhenEmpty, v -> p.hideWhenEmpty = v));
            r.add(slider("armor.warnPercent", "Warning level", "Порог низкой прочности", () -> p.warnPercent, v -> p.warnPercent = (int)v,1,90,1,"%"));
            widgetSettings(r, "armor", p);
        }
        case ITEM_COUNTER -> {
            var p = cfg().itemCounter;
            r.add(bool("itemCounter.names", "Item names", "Названия рядом с количеством", () -> p.showNames, v -> p.showNames = v));
            r.add(bool("itemCounter.hideZero", "Hide zero", "Скрывать отсутствующие предметы", () -> p.hideZero, v -> p.hideZero = v));
            r.add(slider("itemCounter.lowCount", "Low count", "Порог малого запаса", () -> p.lowCount, v -> p.lowCount = (int)v,0,64,1,""));
            r.add(new Row(27, (g,x,y,w) -> strong(g, "Selected: " + p.items.size() + " / " + MAX_SELECTED_ITEMS, x+18,y+5,13,TEXT)));
            r.add(disclosure("itemCounter.items", "Select items"));
            if (advanced.contains("itemCounter.items")) itemSelection(r);
            widgetSettings(r, "itemCounter", p);
        }
        case NOTIFICATIONS -> {
            var p = cfg().notifications;
            r.add(bool("notifications.armor", "Armor warnings", "Порог задаётся в Armor HUD", () -> p.armorWarnings, v -> p.armorWarnings = v));
            r.add(bool("notifications.items", "Item warnings", "Порог задаётся в Item Counter", () -> p.itemWarnings, v -> p.itemWarnings = v));
            r.add(bool("notifications.effects", "Effect warnings", "Истечение ванильных эффектов", () -> p.effectWarnings, v -> p.effectWarnings = v));
            r.add(bool("notifications.sound", "Sound", "Звук нового уведомления", () -> p.sound, v -> p.sound = v));
            r.add(slider("notifications.effectSeconds", "Effect warning", "До конца эффекта", () -> p.effectSeconds, v -> p.effectSeconds = (int)v,1,60,1," s"));
            r.add(slider("notifications.durationSeconds", "Duration", "Время показа", () -> p.durationSeconds, v -> p.durationSeconds = (int)v,2,10,1," s"));
            r.add(slider("notifications.maxVisible", "Visible notices", "Одновременно", () -> p.maxVisible, v -> p.maxVisible = (int)v,1,4,1,""));
            r.add(new Row(42, (g,x,y,w) -> button(g,"notifications.test",x+18,y+3,w-36,29,"Test notification",this::testNotification)));
            widgetSettings(r, "notifications", p);
        }
        case PERFORMANCE -> {
            var p = cfg().performance;
            r.add(bool("performance.fps", "FPS", "Частота кадров", () -> p.showFps, v -> p.showFps = v));
            r.add(bool("performance.ping", "Ping", "Задержка до сервера", () -> p.showPing, v -> p.showPing = v));
            r.add(bool("performance.frameTime", "Frame time", "Время отрисовки кадра", () -> p.showFrameTime, v -> p.showFrameTime = v));
            r.add(bool("performance.graph", "Frame graph", "График времени кадра", () -> p.showGraph, v -> p.showGraph = v));
            widgetSettings(r, "performance", p);
        }
        case SESSION -> {
            var p = cfg().session;
            r.add(choice("session.mode", "Mode", "Режим статистики", () -> p.mode, v -> p.mode = v,
                    new String[]{"FFA","SMP","MEETUPS"}, new String[]{"FFA","SMP","Meetups"}));
            r.add(info("Смена режима сбрасывает статистику."));
            if ("FFA".equals(p.mode)) {
                r.add(info("Всего убийств, наградные и время.", "Сообщения FFA: автоматический учёт."));
            } else {
                r.add(info("Ванильные убийства и смерти игроков.", "K/D и серия убийств без смерти."));
            }
            r.add(bool("session.time", "Session time", "Время в текущей сессии", () -> p.showTime, v -> p.showTime = v));
            if (!"FFA".equals(p.mode)) {
                r.add(bool("session.kd", "K/D", "Соотношение убийств и смертей", () -> p.showKd, v -> p.showKd = v));
                r.add(bool("session.streak", "Kill streak", "Серия убийств без смерти", () -> p.showStreak, v -> p.showStreak = v));
            }
            r.add(bool("session.resetOnDisconnect", "Disconnect reset", "Сбрасывать при выходе с сервера", () -> p.resetOnDisconnect, v -> p.resetOnDisconnect = v));
            r.add(new Row(42, (g,x,y,w) -> button(g,"session.reset",x+18,y+3,w-36,29,"Reset session",()->{
                SessionTracker.reset(); showNotice("Статистика сессии сброшена");
            })));
            widgetSettings(r, "session", p);
        }
        }
        return r;
    }
    private void widgetSettings(List<Row> r, String id, ClientConfig.WidgetSettings p) {
        r.add(disclosure(id + ".advanced", "More settings"));
        if (!advanced.contains(id + ".advanced")) return;
        r.add(bool(id + ".background", "Background", "Подложка блока", () -> p.background, v -> p.background = v));
        r.add(bool(id + ".shadow", "Text shadow", "Тень за текстом", () -> p.shadow, v -> p.shadow = v));
        r.add(choice(id + ".align", "Alignment", "Привязка по горизонтали", () -> p.alignment, v -> p.alignment = v,
                new String[]{"LEFT","CENTER","RIGHT"}, new String[]{"Left","Center","Right"}));
        r.add(bool(id + ".bottom", "Bottom anchor", "Отступ от нижнего края", () -> p.bottom, v -> p.bottom = v));
        positions(r, id, p.position);
    }
    private Row info(String... lines) {
        return new Row(10 + lines.length * 16, (g,x,y,w) -> {
            for (int i = 0; i < lines.length; i++) label(g,fit(lines[i],11,w-36),x+18,y+3+i*16,11,MUTED);
        });
    }
    private void itemSelection(List<Row> r) {
        r.add(info("Один тип суммируется по всему инвентарю.", "Названия и свойства не разделяют счётчик."));
        for (ItemPreset preset : ITEM_PRESETS) {
            r.add(bool("itemCounter.preset." + preset.key, preset.name, preset.description,
                    () -> cfg().itemCounter.items.contains(preset.itemId), v -> selectItem(preset.itemId, v)));
        }
        r.add(new Row(42, (g,x,y,w) -> button(g,"itemCounter.addHeld",x+18,y+3,w-36,29,"Add held item",this::addHeldItem)));
        r.add(info("Добавить предмет из основной руки.", "До 8 типов: сначала сними лишний выбор."));
        for (String itemId : List.copyOf(cfg().itemCounter.items)) {
            if (ITEM_PRESETS.stream().anyMatch(p -> p.itemId.equals(itemId))) continue;
            Identifier parsed = Identifier.tryParse(itemId);
            // DefaultedRegistry returns AIR for missing IDs: never use that as a label.
            boolean available = parsed != null && BuiltInRegistries.ITEM.containsKey(parsed)
                    && !new ItemStack(BuiltInRegistries.ITEM.getValue(parsed)).isEmpty();
            String name = available ? BuiltInRegistries.ITEM.getValue(parsed).getName().getString() : "Missing item";
            r.add(new Row(52, (g,x,y,w) -> {
                strong(g,CustomFont.medium().ellipsize(name,13,w-124),x+18,y+4,13,available?TEXT:0xFFDC5867);
                label(g,fit(itemId,10,w-36),x+18,y+29,10,MUTED);
                button(g,"itemCounter.remove."+itemId,x+w-94,y+1,76,25,"Remove",()->{selectItem(itemId,false);save();});
            }));
            if (!available) r.add(info("Предмет недоступен и не учитывается."));
        }
    }
    private void selectItem(String itemId, boolean selected) {
        var items = cfg().itemCounter.items;
        if (!selected) { items.remove(itemId); return; }
        if (items.contains(itemId)) return;
        if (items.size() >= MAX_SELECTED_ITEMS) { showNotice("Уже выбрано 8 типов. Сначала убери один предмет."); return; }
        Identifier parsed = Identifier.tryParse(itemId);
        if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)
                || new ItemStack(BuiltInRegistries.ITEM.getValue(parsed)).isEmpty()) {
            showNotice("Этот предмет недоступен в текущей версии игры"); return;
        }
        items.add(itemId);
    }
    private void addHeldItem() {
        if (minecraft.player == null || minecraft.player.getMainHandItem().isEmpty()) {
            showNotice("Возьми нужный предмет в основную руку в игре"); return;
        }
        String itemId = BuiltInRegistries.ITEM.getKey(minecraft.player.getMainHandItem().getItem()).toString();
        if (cfg().itemCounter.items.contains(itemId)) { showNotice("Этот тип предмета уже выбран"); return; }
        int before = cfg().itemCounter.items.size();
        selectItem(itemId, true);
        if (cfg().itemCounter.items.size() > before) { save(); showNotice("Предмет добавлен в счётчик"); }
    }
    private void testNotification() {
        if (!cfg().notifications.enabled) { showNotice("Включи Notifications для проверки"); return; }
        Notifications.testNotification(cfg());
        notificationPreviewUntil = System.nanoTime() + cfg().notifications.durationSeconds * 1_000_000_000L;
    }
    private void positions(List<Row> r, String id, ClientConfig.HudPosition p) {
        r.add(slider(id+".scale", "Scale", "Размер блока на экране", () -> p.scale, v -> p.scale=v,.5,2,.05,"×"));
        r.add(slider(id+".x", "Horizontal", "Положение по горизонтали", () -> p.x*100, v -> p.x=v/100,0,100,1,"%"));
        r.add(slider(id+".y", "Vertical", "Положение по вертикали", () -> p.y*100, v -> p.y=v/100,0,100,1,"%"));
        r.add(new Row(42,(g,x,y,w)->button(g,id+".editor",x+18,y+3,w-36,29,"Open HUD Editor",()->BloodHelperClient.openHudEditor(this))));
    }
    private Row bool(String id,String name,String desc,BooleanSupplier get,Consumer<Boolean> set) {
        return new Row(40,(g,x,y,w)->{
            strong(g,CustomFont.medium().ellipsize(name,14,w-60),x+18,y+3,14,TEXT);
            label(g,fit(desc,11,w-54),x+18,y+22,11,MUTED);
            checkbox(g,id,x+w-33,y+7,get.getAsBoolean(),()->{set.accept(!get.getAsBoolean());save();});
        });
    }
    private Row choice(String id,String name,String desc,Supplier<String> get,Consumer<String> set,String[] values,String[] names) {
        return new Row(46,(g,x,y,w)->{
            strong(g,name,x+18,y+3,14,TEXT);
            label(g,fit(desc,10,110),x+18,y+24,10,MUTED);
            int index=Math.max(0,Arrays.asList(values).indexOf(get.get()));
            int bx=x+w-150, by=y+2;
            border(g,bx,by,132,28,6,0xFF18141F,LINE);
            label(g,fit(names[index],12,104),bx+8,by+6,12,TEXT);
            UiIcons.draw(g,"chevron",bx+115,by+8,11,FAINT);
            hit(id,bx,by,132,28,()->{
                focus(null);
                if(dropdown!=null&&dropdown.id.equals(id)) dropdown=null;
                else dropdown=new Dropdown(id,bx,by+31,132,values,names,value->{set.accept(value);save();});
            });
        });
    }
    private Row slider(String id,String name,String desc,DoubleSupplier get,DoubleConsumer set,double min,double max,double step,String unit) {
        return new Row(46,(g,x,y,w)->{
            strong(g,name,x+18,y+3,14,TEXT);
            label(g,fit(desc,10,w-125),x+18,y+24,10,MUTED);
            double value=get.getAsDouble();
            String val=(step>=1?Long.toString(Math.round(value)):String.format(Locale.ROOT,"%.2f",value))+unit;
            label(g,val,x+w-18-CustomFont.regular().width(val,12),y+3,12,softAccent());
            int sx=x+w-108,sy=y+27;
            g.fill(sx,sy,sx+90,sy+2,0xFF38303D);
            int progress=(int)Math.round(90*Math.clamp((value-min)/(max-min),0,1));
            g.fill(sx,sy,sx+progress,sy+2,accent());
            rounded(g,sx+progress-4,sy-3,8,8,4,0xFF0B080E);
            rounded(g,sx+progress-2,sy-1,4,4,2,softAccent());
            if(visible(sy-7,18)) sliders.add(new Slider(id,sx,sy-7,90,18,min,max,step,set,contentClip));
        });
    }
    private Row disclosure(String id,String name) {
        return new Row(35,(g,x,y,w)->{
            label(g,name,x+18,y+8,12,MUTED);
            UiIcons.draw(g,advanced.contains(id)?"back":"chevron",x+w-31,y+9,12,FAINT);
            hit(id,x+12,y+2,w-24,29,()->{if(!advanced.remove(id))advanced.add(id);dropdown=null;focus(null);});
        });
    }
    private void checkbox(GuiGraphics g,String id,int x,int y,boolean value,Runnable action) {
        border(g,x,y,16,16,4,value?accent():0xFF16131C,value?softAccent():0xFF403745);
        if(value)texture(g,"check",x+4,y+5,8,6,8,6,0xFFFFFFFF);
        hit(id,x-5,y-5,26,26,action);
    }
    private void button(GuiGraphics g,String id,int x,int y,int w,int h,String text,Runnable action) {
        boolean hover=contains(x,y,w,h)&&(!contentClip||mouseY>=clipTop()&&mouseY<CONTENT_BOTTOM);
        border(g,x,y,w,h,6,hover?0xFF28202D:0xFF19141F,hover?0xFF55404E:LINE);
        String shown=fit(text,13,w-16);
        label(g,shown,x+(w-CustomFont.regular().width(shown,13))/2,y+(h-16)/2f,13,hover?TEXT:MUTED);
        hit(id,x,y,w,h,action);
    }
    private void transport(GuiGraphics g,String command,int x,int y,int w) {
        rounded(g,x,y,w,28,5,0xFF201923);
        UiIcons.draw(g,command,x+(w-14)/2,y+7,14,TEXT);
        hit("media."+command,x,y,w,28,()->BloodHelperClient.musicCommand(command.equals("play")||command.equals("pause")?"toggle":command));
    }
    private void drawDropdown(GuiGraphics g) {
        if(dropdown==null)return;
        Dropdown d=dropdown;
        int h=d.values.length*26+8;
        int x=Math.clamp(d.x,180,W-d.w-12),y=Math.clamp(d.y,60,H-h-10);
        border(g,x,y,d.w,h,7,0xFF1A141F,0xFF503747);
        for(int i=0;i<d.values.length;i++){
            int yy=y+4+i*26; String value=d.values[i];
            if(contains(x+3,yy,d.w-6,26))rounded(g,x+3,yy,d.w-6,26,4,alpha(cfg().accent,45));
            label(g,d.names[i],x+10,yy+5,12,TEXT);
            hit(d.id+".option."+value,x+3,yy,d.w-6,26,()->{d.select.accept(value);dropdown=null;});
        }
    }
    private void appearance(GuiGraphics g) {
        List<Row> a=new ArrayList<>(),b=new ArrayList<>();
        a.add(new Row(64,(gg,x,y,w)->{
            strong(gg,"Accent",x+18,y+4,14,TEXT);
            int[] colors={0xCC171D,0xD56178,0x8187FF,0x639AE3,0x4FACAA,0xCFA160};
            for(int i=0;i<colors.length;i++){
                int xx=x+18+i*40,color=colors[i];
                border(gg,xx,y+28,28,22,4,alpha(color,255),(cfg().accent&0xFFFFFF)==color?TEXT:LINE);
                hit("appearance.accent."+i,xx,y+28,28,22,()->{cfg().accent=color;save();});
            }
        }));
        a.add(slider("appearance.scale","Interface scale","Размер меню",()->cfg().guiScale,v->cfg().guiScale=v,.65,1.05,.05,"×"));
        a.add(bool("appearance.motion","Animations","Плавные переходы",()->cfg().animations,v->cfg().animations=v));
        a.add(choice("appearance.language","HUD language","Язык HUD и сообщений",()->cfg().language,v->cfg().language=v,new String[]{"ru","en"},new String[]{"Russian","English"}));
        a.add(new Row(56,(gg,x,y,w)->{
            strong(gg,"Menu key",x+18,y+5,14,TEXT);
            button(gg,"appearance.key",x+122,y+1,w-140,30,capturingKey?tr("Нажми…","Press…"):menuKeyName(),()->{capturingKey=true;focus(null);});
            label(gg,tr("Backspace — Right Shift","Backspace restores Right Shift"),x+18,y+36,10,MUTED);
        }));
        b.add(bool("appearance.background","HUD backgrounds","Подложки всех блоков",()->cfg().background,v->cfg().background=v));
        b.add(bool("appearance.shadow","Text shadow","Тень текста в HUD",()->cfg().textShadow,v->cfg().textShadow=v));
        b.add(slider("appearance.opacity","Opacity","Прозрачность подложек",()->cfg().backgroundAlpha,v->cfg().backgroundAlpha=(int)v,0,255,1,""));
        b.add(new Row(63,(gg,x,y,w)->{
            strong(gg,"Background color",x+18,y+3,14,TEXT);
            int[] colors={0x100D0E,0x000000,0x191A28,0x182223,0x281B1D,0x333336};
            for(int i=0;i<colors.length;i++){
                int xx=x+18+i*40,color=colors[i];
                border(gg,xx,y+29,28,22,4,alpha(color,255),(cfg().backgroundColor&0xFFFFFF)==color?TEXT:LINE);
                hit("appearance.background."+i,xx,y+29,28,22,()->{cfg().backgroundColor=color;save();});
            }
        }));
        b.add(new Row(42,(gg,x,y,w)->button(gg,"appearance.hud",x+18,y+4,w-36,30,"Open HUD Editor",()->BloodHelperClient.openHudEditor(this))));
        simpleCard(g,"Interface",190,78,274,a); simpleCard(g,"HUD",484,78,274,b);
    }
    private void simpleCard(GuiGraphics g,String title,int x,int y,int w,List<Row>rows){
        int h=46+rows.stream().mapToInt(Row::height).sum();
        frame(g,x,y,w,h,10);rounded(g,x+1,y+1,w-2,35,9,HEADER);g.fill(x+1,y+24,x+w-1,y+36,HEADER);
        texture(g,"ico",x+18,y+9,18,18,18,18,TEXT);strong(g,title,x+46,y+10,14,TEXT);
        int yy=y+45;for(Row row:rows){row.draw(g,x,yy,w);yy+=row.height();}
    }
    private void profiles(GuiGraphics g){
        frame(g,190,78,568,115,10);strong(g,"Save current settings",208,96,15,TEXT);
        border(g,208,130,334,32,6,0xFF15111C,focused==profileName?softAccent():LINE);
        field(g,profileName,217,136,312,"profile.name");
        button(g,"profile.save",556,130,184,32,"Save profile",this::saveProfile);
        label(g,tr("Модули, оформление и расположение HUD","Modules, appearance and HUD positions"),208,174,11,MUTED);
        strong(g,"Saved profiles",190,215,14,TEXT);
        contentClip=true;g.enableScissor(188,244,762,CONTENT_BOTTOM);
        int y=250-(int)profileScroll;
        for(String name:profiles){
            frame(g,190,y,568,52,7);strong(g,fit(name,14,294),208,y+17,14,TEXT);
            button(g,"profile.load."+name,548,y+10,85,31,"Load",()->loadProfile(name));
            boolean confirming=name.equals(deleteCandidate)&&System.currentTimeMillis()<deleteUntil;
            button(g,"profile.delete."+name,645,y+10,94,31,confirming?"Confirm?":"Delete",()->deleteProfile(name));
            y+=64;
        }
        if(profiles.isEmpty())label(g,tr("Нет сохранённых профилей","No saved profiles"),208,265,14,MUTED);
        g.disableScissor();contentClip=false;
    }
    private void about(GuiGraphics g){
        logo(g,211,102,510);
        strong(g,"BLOODHELPER "+BloodHelperClient.VERSION,211,216,18,TEXT);
        label(g,tr("Визуальный клиент для Bloodstone","Visual client for Bloodstone"),211,245,14,MUTED);
        label(g,"Minecraft 1.21.11 · Fabric",211,270,13,FAINT);
        g.fill(211,311,737,312,LINE);
        strong(g,"Credits",211,331,14,TEXT);
        label(g,"GUI / SF Pro fonts — Expensive 4.0",211,359,13,MUTED);
        label(g,"Bloodstone — Trard/bloodstone3",211,385,13,MUTED);
        label(g,"Lyrics — LRCLIB",211,411,13,MUTED);
    }
    private String musicStatus(){return switch(BloodHelperClient.mediaStatus()){
        case "playing"->"Playing";case "paused"->"Paused";
        case "unsupported_os"->tr("Музыка доступна в Windows","Music is available on Windows");
        case "no_session"->tr("Открой музыку в своём плеере","Open music in your player");
        case "starting"->tr("Подключение к плееру…","Connecting to player…");
        default->tr("Нет активной медиасессии","No active media session");};}
    private String menuKeyName(){String name=InputConstants.Type.KEYSYM.getOrCreate(cfg().menuKey).getName().replace("key.keyboard.","");return Arrays.stream(name.split("[._]")).filter(s->!s.isBlank()).map(s->Character.toUpperCase(s.charAt(0))+s.substring(1)).reduce((a,b)->a+" "+b).orElse(name);}
    private void refreshProfiles(){profiles=List.copyOf(BloodHelperClient.profiles());}
    private void saveProfile(){String name=profileName.getValue().strip();if(name.isEmpty()){showNotice(tr("Введи название профиля","Enter a profile name"));focus(profileName);return;}try{BloodHelperClient.saveProfile(name);refreshProfiles();showNotice(tr("Сохранено: ","Saved: ")+name);}catch(IOException|RuntimeException e){showNotice(tr("Не удалось сохранить профиль","Could not save profile"));}}
    private void loadProfile(String name){if(BloodHelperClient.loadProfile(name)){pvpGroup.setValue(cfg().pvp.cooldownGroup);showNotice(tr("Загружено: ","Loaded: ")+name);}else showNotice(tr("Не удалось загрузить профиль","Could not load profile"));}
    private void deleteProfile(String name){if(!name.equals(deleteCandidate)||System.currentTimeMillis()>=deleteUntil){deleteCandidate=name;deleteUntil=System.currentTimeMillis()+5000;return;}boolean ok=BloodHelperClient.deleteProfile(name);deleteCandidate="";refreshProfiles();profileScroll=Math.min(profileScroll,Math.max(0,profiles.size()*64-230));showNotice(ok?tr("Профиль удалён","Profile deleted"):tr("Не удалось удалить профиль","Could not delete profile"));}
    private void save(){BloodHelperClient.saveConfig();}
    private void showNotice(String message){notice=message;noticeUntil=System.currentTimeMillis()+5000;}
    private void focus(GuiTextField next){if(focused!=null){focused.setFocused(false);if(focused==pvpGroup&&focused!=next)save();}focused=next;if(next!=null)next.setFocused(true);}
    private void field(GuiGraphics g,GuiTextField box,int x,int y,int w,String id){box.setX(x);box.setY(y);box.setWidth(w);box.render(g,(int)mouseX,(int)mouseY,0);if(visible(y,20))fields.add(new FieldHit(id,box,x-3,y-3,w+6,24,contentClip));}
    private boolean contains(int x,int y,int w,int h){return mouseX>=x&&mouseX<x+w&&mouseY>=y&&mouseY<y+h;}
    private int clipTop(){return page==Page.PROFILES?244:CONTENT_TOP-4;}
    private boolean visible(int y,int h){return !contentClip||y+h>clipTop()&&y<CONTENT_BOTTOM;}
    private void hit(String id,int x,int y,int w,int h,Runnable action){if(contentClip){int b=Math.min(y+h,CONTENT_BOTTOM);y=Math.max(y,clipTop());h=b-y;}if(h>0)hits.add(new Hit(id,x,y,w,h,action));}
    private void scrollbar(GuiGraphics g,int x,int y,int h,double value,double max){if(max<=0)return;int thumb=Math.max(24,(int)(h*h/(h+max)));int yy=y+(int)((h-thumb)*value/max);rounded(g,x,y,2,h,1,0x403D303E);rounded(g,x,yy,2,thumb,1,0xFF706075);}

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        mouseX=(event.x()-originX)/menuScale;mouseY=(event.y()-originY)/menuScale;
        if(event.button()!=0)return true;
        if(dropdown!=null){
            for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(h.id.startsWith(dropdown.id+".option.")&&contains(h.x,h.y,h.w,h.h)){h.action.run();return true;}}
            dropdown=null;return true;
        }
        capturingKey=false;
        for(FieldHit f:fields){if(f.clipped&&(mouseY<clipTop()||mouseY>=CONTENT_BOTTOM))continue;if(contains(f.x,f.y,f.w,f.h)){focus(f.box);f.box.mouseClicked(new MouseButtonEvent(Math.clamp(mouseX,f.box.getX(),f.box.getX()+f.box.getWidth()-.01),Math.clamp(mouseY,f.box.getY(),f.box.getY()+f.box.getHeight()-.01),event.buttonInfo()),doubled);return true;}}
        focus(null);
        for(Slider s:sliders){if(s.clipped&&(mouseY<clipTop()||mouseY>=CONTENT_BOTTOM))continue;if(contains(s.x-4,s.y,s.w+8,s.h)){dragging=s;s.set(mouseX);return true;}}
        for(int i=hits.size()-1;i>=0;i--){Hit h=hits.get(i);if(contains(h.x,h.y,h.w,h.h)){h.action.run();return true;}}
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(dragging!=null){dragging.set((event.x()-originX)/menuScale);return true;}return focused!=null&&focused.mouseDragged(new MouseButtonEvent((event.x()-originX)/menuScale,(event.y()-originY)/menuScale,event.buttonInfo()),dx/menuScale,dy/menuScale);}
    @Override public boolean mouseReleased(MouseButtonEvent event){if(dragging!=null){dragging=null;save();return true;}if(focused!=null)focused.mouseReleased(new MouseButtonEvent((event.x()-originX)/menuScale,(event.y()-originY)/menuScale,event.buttonInfo()));return true;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){mouseX=(x-originX)/menuScale;mouseY=(y-originY)/menuScale;if(dropdown!=null){dropdown=null;return true;}if(page==Page.VISUALS&&contains(180,CONTENT_TOP-4,596,CONTENT_BOTTOM-CONTENT_TOP+4)){scroll=Math.clamp(scroll-vertical*32,0,maxScroll);focus(null);return true;}if(page==Page.PROFILES&&contains(188,244,574,234)){profileScroll=Math.clamp(profileScroll-vertical*36,0,Math.max(0,profiles.size()*64-230));return true;}return false;}
    @Override public boolean keyPressed(KeyEvent event){
        if(capturingKey){capturingKey=false;if(event.key()!=GLFW.GLFW_KEY_ESCAPE){cfg().menuKey=event.key()==GLFW.GLFW_KEY_BACKSPACE?GLFW.GLFW_KEY_RIGHT_SHIFT:event.key();save();}return true;}
        if(event.key()==GLFW.GLFW_KEY_ESCAPE){if(dropdown!=null){dropdown=null;return true;}onClose();return true;}
        if(focused==null&&event.key()==cfg().menuKey){onClose();return true;}
        if(event.key()==GLFW.GLFW_KEY_ENTER&&focused==profileName){saveProfile();return true;}
        if(event.key()==GLFW.GLFW_KEY_TAB&&!fields.isEmpty()){int n=-1;for(int i=0;i<fields.size();i++)if(fields.get(i).box==focused)n=i;focus(fields.get((n+1)%fields.size()).box);return true;}
        return focused!=null&&focused.keyPressed(event);
    }
    @Override public boolean charTyped(CharacterEvent event){return focused!=null&&focused.charTyped(event);}
    @Override public void onClose(){save();minecraft.setScreen(parent);}
    @Override public void removed(){save();}
    @Override public boolean isPauseScreen(){return false;}
    private record Hit(String id,int x,int y,int w,int h,Runnable action){}
    private record FieldHit(String id,GuiTextField box,int x,int y,int w,int h,boolean clipped){}
    private record Slider(String id,int x,int y,int w,int h,double min,double max,double step,DoubleConsumer setter,boolean clipped){void set(double mouse){double v=min+Math.clamp((mouse-x)/w,0,1)*(max-min);setter.accept(Math.clamp(min+Math.round((v-min)/step)*step,min,max));}}
    private record Dropdown(String id,int x,int y,int w,String[] values,String[] names,Consumer<String> select){}
    @FunctionalInterface private interface RowDraw{void draw(GuiGraphics g,int x,int y,int w);}
    private record Row(int height,RowDraw renderer){void draw(GuiGraphics g,int x,int y,int w){renderer.draw(g,x,y,w);}}
    private record Card(Module module,int x,int y,int height,List<Row> rows){}
    private record ItemPreset(String key,String itemId,String name,String description){}
}
