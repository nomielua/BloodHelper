package ru.bloodhelper.client.hud;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiDraw;
import ru.bloodhelper.client.gui.UiIcons;
import ru.bloodhelper.client.music.MediaSnapshot;

/** Compact music Island. Editor previews never register controls or touch the media session. */
public final class MusicWidget {
    private static final Identifier COVER = Identifier.fromNamespaceAndPath("bloodhelper", "media/cover");
    private static final int[] STILL_BARS = {5, 10, 7, 4};
    private static final Map<String, HudRenderer.Bounds> controls = new LinkedHashMap<>();
    private static long coverRevision = Long.MIN_VALUE;
    private static int coverWidth, coverHeight;
    private static float islandWidth = 214, islandHeight = 75;
    private static long frameTime;

    private MusicWidget() { }

    public static void render(GuiGraphics g, ClientConfig c, boolean preview) {
        clearControls();
        MediaSnapshot media = preview
                ? new MediaSnapshot(HudStyle.tr("Ночной город", "City at night"),
                    HudStyle.tr("Демонстрация", "Preview"), 84000, 212000, true, null, -1)
                : BloodHelperClient.media().snapshot();
        if (!preview && !media.available()) releaseCover();
        if (!preview && c.music.hideWhenIdle && !media.available()) {
            frameTime = 0;
            return;
        }
        long position = preview ? media.positionMs() : BloodHelperClient.playbackPosition(media);
        String lyric = "";
        if (c.music.lyricsMode.equals("ISLAND") || c.music.lyricsMode.equals("BOTH")) {
            lyric = preview ? HudStyle.tr("Огни останутся с нами", "The lights stay with us")
                    : BloodHelperClient.lyrics().currentLine(position, c.music);
        }
        renderFrame(g, c, media, position, lyric, preview);
    }

    private static void renderFrame(GuiGraphics g, ClientConfig c, MediaSnapshot media,
                                    long position, String lyric, boolean preview) {
        var settings = c.music;
        boolean expanded = !settings.compact;
        boolean showLyric = expanded && !lyric.isBlank();
        int rowY = settings.showProgress ? 44 : 38;
        int bodyBottom = settings.showControls ? rowY + 22 : settings.showProgress ? rowY + 12 : 38;
        int lyricY = bodyBottom + 7;
        int targetWidth = expanded ? 214 : 190;
        int targetHeight = expanded ? (showLyric ? lyricY + 20 : bodyBottom + 9) : 36;
        long now = System.nanoTime();
        double dt = frameTime == 0 ? 1 : Math.min(.1, (now - frameTime) / 1e9);
        double blend = c.animations && !preview && frameTime != 0 ? 1 - Math.exp(-dt * 16) : 1;
        frameTime = now;
        islandWidth += (targetWidth - islandWidth) * blend;
        islandHeight += (targetHeight - islandHeight) * blend;
        int w = Math.round(islandWidth), h = Math.round(islandHeight);
        double scale = HudRenderer.effectiveScale(g, w, h, settings.position);
        HudRenderer.Bounds box = HudRenderer.begin(g, "music", settings.position, "CENTER", false, w, h);

        int radius = expanded ? 14 : 20;
        HudStyle.surface(g, 0, 0, w, h, radius, c);
        // Height transitions must not leave newly appearing lyrics outside the pill.
        g.enableScissor(1, 1, w - 1, h - 1);
        int coverSize = expanded ? 28 : 22;
        int coverX = 11, coverY = 7;
        int textX = settings.showCover ? coverX + coverSize + 8 : 14;
        int textWidth = Math.max(0, w - textX - 33);
        if (!preview) updateCover(media);
        if (settings.showCover) drawCover(g, coverX, coverY, coverSize, c, preview);
        String title = media.available() ? media.title() : "Music";
        if (title.isBlank()) title = HudStyle.tr("Без названия", "Untitled");
        String artist = media.available() ? media.artist() : mediaStatus();
        if (media.available() && artist.isBlank()) artist = HudStyle.tr("Неизвестный исполнитель", "Unknown artist");
        HudStyle.strong(g, HudStyle.fitStrong(title, textWidth), textX, expanded ? 9 : 7,
                HudStyle.TEXT, true);
        HudStyle.label(g, HudStyle.fitLabel(artist, textWidth), textX, expanded ? 23 : 21,
                HudStyle.MUTED, true);
        drawActivity(g, w - 28, expanded ? 21 : 18, media.playing(), c, preview, now);

        if (expanded) {
            if (settings.showProgress) {
                float progress = media.durationMs() > 0 ? (float) position / media.durationMs() : 0;
                HudStyle.progress(g, 13, 40, w - 26, progress, c.accent);
            }
            if (settings.showControls || settings.showProgress) {
                int timeY = rowY + (settings.showControls ? 7 : 1);
                String current = media.available() ? time(position) : "—:—";
                String total = media.available() && media.durationMs() > 0 ? time(media.durationMs()) : "—:—";
                // Very long streams still cannot run into the center transport buttons.
                current = HudStyle.fitLabel(current, 49);
                total = HudStyle.fitLabel(total, 49);
                HudStyle.label(g, current, 14, timeY, HudStyle.MUTED, true);
                HudStyle.label(g, total, w - 14 - HudStyle.labelWidth(total), timeY, HudStyle.MUTED, true);
            }
            if (settings.showControls) {
                boolean active = media.available();
                control(g, box, "previous", w / 2 - 41, rowY, 22, scale, active, media.playing(), preview);
                control(g, box, "toggle", w / 2 - 13, rowY, 26, scale, active, media.playing(), preview);
                control(g, box, "next", w / 2 + 19, rowY, 22, scale, active, media.playing(), preview);
            }
            if (showLyric) renderIslandLyric(g, lyric, position, 14, lyricY, w - 28, c, preview);
        }
        g.disableScissor();
        g.pose().popMatrix();
    }

    private static void drawCover(GuiGraphics g, int x, int y, int size, ClientConfig c, boolean preview) {
        UiDraw.rounded(g, x, y, size, size, 7, 0xFF242730);
        if (!preview && coverWidth > 0 && coverHeight > 0) {
            // Fit the full artwork without stretching non-square media thumbnails.
            double fit = (double) (size - 4) / Math.max(coverWidth, coverHeight);
            int drawWidth = Math.max(1, (int) Math.round(coverWidth * fit));
            int drawHeight = Math.max(1, (int) Math.round(coverHeight * fit));
            g.blit(RenderPipelines.GUI_TEXTURED, COVER, x + (size - drawWidth) / 2,
                    y + (size - drawHeight) / 2, 0, 0, drawWidth, drawHeight,
                    coverWidth, coverHeight, coverWidth, coverHeight);
        } else {
            UiIcons.draw(g, "music", x + 3, y + 3, size - 6,
                    preview ? c.accent : HudStyle.MUTED);
        }
    }

    private static void drawActivity(GuiGraphics g, int x, int centerY, boolean playing,
                                     ClientConfig c, boolean preview, long now) {
        for (int i = 0; i < 4; i++) {
            int height = playing ? (c.animations && !preview
                    ? (int) (3 + 8 * (.5 + .5 * Math.sin(now / 1.8e8 + i * 1.7)))
                    : STILL_BARS[i]) : 3;
            UiDraw.rounded(g, x + i * 4, centerY - height / 2, 2, height, 1,
                    playing ? c.accent : HudStyle.FAINT);
        }
    }

    private static void renderIslandLyric(GuiGraphics g, String lyric, long position, int x,
                                          int y, int width, ClientConfig c, boolean preview) {
        String text = HudStyle.fit(lyric, width);
        var active = preview ? null : BloodHelperClient.lyrics().current(position, c.music);
        double enter = 1, leave = 1;
        if (active != null && c.animations) {
            long time = position + c.music.lyricOffsetMs;
            double ramp = Math.min(350, (active.endMs() - active.line().timestampMs()) * .25);
            enter = Math.clamp((time - active.line().timestampMs()) / Math.max(1, ramp), 0, 1);
            leave = Math.clamp((active.endMs() - time) / Math.max(1, ramp), 0, 1);
        }
        double eased = 1 - Math.pow(1 - enter, 3);
        int opacity = (int) (255 * Math.min(eased, leave));
        if (opacity < 5) return;
        double scale = 1, offsetX = 0, offsetY = 0;
        if (c.animations && !preview) {
            switch (c.music.lyricAnimation) {
                case "TYPEWRITER" -> {
                    int points = text.codePointCount(0, text.length());
                    text = text.substring(0, text.offsetByCodePoints(0,
                            Math.clamp((int) Math.ceil(points * enter), 0, points)));
                }
                case "POP" -> scale = .75 + .25 * eased;
                case "SLIDE" -> offsetX = 12 * (1 - eased);
                case "FLOW" -> offsetY = 4 * (1 - eased);
                default -> { }
            }
        }
        g.pose().pushMatrix();
        g.pose().translate((float) (x + offsetX), (float) (y + offsetY));
        g.pose().scale((float) scale, (float) scale);
        if (c.music.lyricColor.equals("GRADIENT")) {
            int[] points = text.codePoints().toArray();
            StringBuilder preceding = new StringBuilder();
            for (int i = 0; i < points.length; i++) {
                String glyph = new String(Character.toChars(points[i]));
                int color = UiDraw.blend(c.accent, 0xFFFFFFFF,
                        .15f + .55f * i / Math.max(1, points.length - 1));
                // Prefix metrics avoid accumulating fractional scale rounding between glyphs.
                HudStyle.text(g, glyph, HudStyle.width(preceding.toString()), 0,
                        UiDraw.alpha(color, opacity), true);
                preceding.append(glyph);
            }
        } else {
            int color = c.music.lyricColor.equals("WHITE") ? HudStyle.TEXT : c.accent;
            HudStyle.text(g, text, 0, 0, UiDraw.alpha(color, opacity), true);
        }
        g.pose().popMatrix();
    }

    private static void control(GuiGraphics g, HudRenderer.Bounds box, String command, int x,
                                 int y, int width, double scale, boolean active, boolean playing,
                                 boolean preview) {
        boolean main = command.equals("toggle");
        if (main) UiDraw.rounded(g, x, y, width, 22, 5, active ? 0xFF302326 : 0xFF1E191A);
        int color = active ? (main ? HudStyle.TEXT : HudStyle.MUTED) : HudStyle.FAINT;
        int centerX = x + width / 2, centerY = y + 11;
        UiIcons.draw(g, main ? (playing ? "pause" : "play") : command,
                centerX - 7, centerY - 7, 14, color);
        if (active && !preview) {
            int left = box.x() + (int) Math.floor(x * scale);
            int top = box.y() + (int) Math.floor(y * scale);
            int right = box.x() + (int) Math.ceil((x + width) * scale);
            int bottom = box.y() + (int) Math.ceil((y + 22) * scale);
            // Clip clickable controls to the visible part of an animated or resized Island.
            right = Math.min(right, box.x() + box.width());
            bottom = Math.min(bottom, box.y() + box.height());
            if (right > left && bottom > top) controls.put(command,
                    new HudRenderer.Bounds(left, top, right - left, bottom - top));
        }
    }

    public static boolean click(double x, double y, int button) {
        var mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            clearControls();
            return false;
        }
        var settings = BloodHelperClient.config().music;
        if (button != 0 || !settings.enabled || settings.compact || !settings.showControls
                || !BloodHelperClient.media().snapshot().available()) return false;
        for (var entry : controls.entrySet()) if (entry.getValue().contains(x, y)) {
            switch (entry.getKey()) {
                case "previous" -> BloodHelperClient.media().previous();
                case "next" -> BloodHelperClient.media().next();
                default -> BloodHelperClient.media().playPause();
            }
            return true;
        }
        return false;
    }

    public static void clearControls() { controls.clear(); }

    private static String time(long ms) {
        long seconds = Math.max(0, ms / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String mediaStatus() {
        return switch (BloodHelperClient.media().status()) {
            case "unsupported_os" -> HudStyle.tr("Медиасессия: Windows", "Media session: Windows");
            case "starting" -> HudStyle.tr("Подключение…", "Connecting…");
            case "unavailable" -> HudStyle.tr("Плеер недоступен", "Player unavailable");
            default -> HudStyle.tr("Нет активного трека", "No active track");
        };
    }

    private static void updateCover(MediaSnapshot media) {
        if (coverRevision == media.revision()) return;
        coverRevision = media.revision();
        var textures = Minecraft.getInstance().getTextureManager();
        if (coverWidth > 0) textures.release(COVER);
        coverWidth = coverHeight = 0;
        byte[] artwork = media.artwork();
        if (artwork == null || artwork.length == 0) return;
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(artwork));
            if (image.getWidth() > 2048 || image.getHeight() > 2048) { image.close(); return; }
            coverWidth = image.getWidth();
            coverHeight = image.getHeight();
            textures.register(COVER, new DynamicTexture(() -> "BloodHelper music cover", image));
        } catch (Exception ignored) {
            coverWidth = coverHeight = 0;
        }
    }

    public static void releaseCover() {
        if (coverWidth > 0) Minecraft.getInstance().getTextureManager().release(COVER);
        coverWidth = coverHeight = 0;
        coverRevision = Long.MIN_VALUE;
    }
}
