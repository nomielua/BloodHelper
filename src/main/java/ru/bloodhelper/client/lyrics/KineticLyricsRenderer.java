package ru.bloodhelper.client.lyrics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.music.MediaService;
import ru.bloodhelper.client.gui.UiFonts;
import ru.bloodhelper.client.music.MediaSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Local, depth-tested world text. Its anchors never follow the player after spawning. */
public final class KineticLyricsRenderer {
    private record Particle(String text, Vec3 anchor, long startMs, long endMs, int index) {}
    private final LyricsService lyrics;
    private final MediaService media;
    private final Supplier<ClientConfig> config;
    private final List<Particle> particles = new ArrayList<>();
    private String lastTrack = "";
    private String lastDimension = "";
    private int lastIndex = -1;
    private long lastTime = -1;
    private boolean lastSplit;
    private MediaSnapshot observed;
    private long observedAt;

    private KineticLyricsRenderer(LyricsService lyrics, MediaService media, Supplier<ClientConfig> config) {
        this.lyrics = lyrics;
        this.media = media;
        this.config = config;
    }

    /** Register once during client initialization. */
    public static void register(LyricsService lyrics, MediaService media, Supplier<ClientConfig> config) {
        KineticLyricsRenderer renderer = new KineticLyricsRenderer(lyrics, media, config);
        WorldRenderEvents.AFTER_ENTITIES.register(renderer::render);
    }

    private void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        ClientConfig cfg = config.get();
        ClientConfig.MusicSettings settings = cfg.music;
        boolean world = "WORLD".equals(settings.lyricsMode) || "BOTH".equals(settings.lyricsMode);
        if (!settings.enabled || !world || client.level == null || client.player == null || client.options.hideGui) {
            clear();
            return;
        }
        MediaSnapshot snapshot = media.snapshot();
        if (!snapshot.available()) { clear(); return; }
        Camera camera = context.gameRenderer().getMainCamera();
        MultiBufferSource buffers = context.consumers();
        if (camera == null || !camera.isInitialized() || buffers == null) return;
        long now = System.nanoTime();
        if (observed != snapshot) { observed = snapshot; observedAt = now; }
        // Interpolate between the bridge's twice-per-second observations, capped so
        // missing observations cannot make lyrics run indefinitely on their own.
        long playback = snapshot.positionMs() + (snapshot.playing() ? Math.min(750, (now - observedAt) / 1_000_000) : 0);
        if (snapshot.durationMs() > 0) playback = Math.min(playback, snapshot.durationMs());
        long time = Math.max(0, playback + settings.lyricOffsetMs);
        String dimension = client.level.dimension().identifier().toString();
        if (!lyrics.trackKey().equals(lastTrack) || !dimension.equals(lastDimension) || lastSplit != settings.splitLyrics
                || (lastTime >= 0 && (time < lastTime - 400 || time > lastTime + 1800))) {
            particles.clear();
            lastIndex = -1;
        }
        lastTrack = lyrics.trackKey();
        lastDimension = dimension;
        lastSplit = settings.splitLyrics;
        lastTime = time;

        LyricsService.ActiveLine active = lyrics.current(playback, settings);
        if (active != null && active.index() != lastIndex) {
            lastIndex = active.index();
            int count = Math.clamp(settings.lyricLines, 1, 6);
            int slot = active.index() % count;
            double offset = count == 1 ? 0 : (slot / (double) (count - 1) - .5) * settings.lyricSpread;
            var look = camera.forwardVector();
            Vec3 forward = new Vec3(look.x(), 0, look.z());
            if (forward.lengthSqr() < .001) forward = new Vec3(0, 0, 1);
            forward = forward.normalize();
            Vec3 side = new Vec3(forward.z, 0, -forward.x);
            double angle = Math.toRadians(offset);
            Vec3 anchor = camera.position().add(forward.scale(Math.cos(angle) * settings.lyricDistance))
                    .add(side.scale(Math.sin(angle) * settings.lyricDistance)).add(0, -.12 + (slot % 2) * .30, 0);
            long end = Math.min(active.endMs() + 450, active.line().timestampMs() + 7500);
            if (end > time) particles.add(new Particle(active.line().text(), anchor, active.line().timestampMs(), end, active.index()));
        }
        final long renderTime = time;
        particles.removeIf(particle -> renderTime >= particle.endMs() || renderTime < particle.startMs()
                || particle.anchor().distanceToSqr(camera.position()) > 48 * 48);
        while (particles.size() > Math.clamp(settings.lyricLines, 1, 6)) particles.removeFirst();
        for (Particle particle : particles) draw(context.matrices(), buffers, camera, UiFonts.world(), particle, time, cfg);
    }

    private void clear() {
        particles.clear();
        lastIndex = -1;
        lastTime = -1;
        lastTrack = "";
        observed = null;
    }

    private static void draw(PoseStack pose, MultiBufferSource buffers, Camera camera, Font font,
                             Particle particle, long time, ClientConfig cfg) {
        ClientConfig.MusicSettings settings = cfg.music;
        double duration = Math.max(1, particle.endMs() - particle.startMs());
        double progress = Math.clamp((time - particle.startMs()) / duration, 0, 1);
        double enter = Math.clamp((time - particle.startMs()) / Math.min(380, duration * .25), 0, 1);
        double leave = Math.clamp((particle.endMs() - time) / Math.min(420, duration * .25), 0, 1);
        double alpha = cfg.animations ? Math.min(ease(enter), ease(leave)) : 1;
        double scale = 1, rise = 0, slide = 0;
        String text = font.plainSubstrByWidth(particle.text(), 230);
        if (text.length() < particle.text().length()) text += "…";
        if (cfg.animations) {
            switch (settings.lyricAnimation) {
                case "TYPEWRITER" -> {
                    int total = text.codePointCount(0, text.length());
                    int visible = Math.clamp((int) Math.ceil(total * Math.min(1, progress / .28)), 0, total);
                    text = text.substring(0, text.offsetByCodePoints(0, visible));
                    rise = progress * settings.lyricFloat * .2;
                }
                case "POP" -> scale = .4 + .6 * back(enter);
                case "SLIDE" -> slide = (1 - ease(enter)) * -.6 + (1 - ease(leave)) * .15;
                case "FADE" -> { }
                default -> {
                    rise = progress * settings.lyricFloat - .08 * (1 - ease(enter));
                    scale = .93 + .07 * back(enter);
                }
            }
        }
        if (alpha < .02 || text.isBlank()) return;
        Vec3 relative = particle.anchor().subtract(camera.position());
        pose.pushPose();
        pose.translate(relative.x, relative.y + rise, relative.z);
        pose.mulPose(camera.rotation());
        pose.translate(slide, 0, 0);
        float worldScale = (float) (.024 * scale);
        pose.scale(-worldScale, -worldScale, worldScale);
        float x = -font.width(text) / 2f;
        int opacity = Math.clamp((int) (255 * alpha), 0, 255);
        if ("GRADIENT".equals(settings.lyricColor)) {
            int points = text.codePointCount(0, text.length()), position = 0;
            for (int codepoint : text.codePoints().toArray()) {
                String glyph = new String(Character.toChars(codepoint));
                double blend = .15 + .55 * (points <= 1 ? 0 : position++ / (double) (points - 1));
                int rgb = blend(cfg.accent, 0xFFFFFF, blend);
                font.drawInBatch(glyph, x, -font.lineHeight / 2f, opacity << 24 | rgb, cfg.textShadow,
                        pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
                x += font.width(glyph);
            }
        } else {
            int rgb = "WHITE".equals(settings.lyricColor) ? 0xFFFFFF : cfg.accent & 0xFFFFFF;
            font.drawInBatch(text, x, -font.lineHeight / 2f, opacity << 24 | rgb, cfg.textShadow,
                    pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        }
        pose.popPose();
    }

    private static double ease(double value) { return 1 - Math.pow(1 - value, 3); }
    private static double back(double value) { double t = value - 1; return 1 + 2.70158 * t * t * t + 1.70158 * t * t; }
    private static int blend(int from, int to, double value) {
        int r = (int) (((from >> 16) & 255) * (1 - value) + ((to >> 16) & 255) * value);
        int g = (int) (((from >> 8) & 255) * (1 - value) + ((to >> 8) & 255) * value);
        int b = (int) ((from & 255) * (1 - value) + (to & 255) * value);
        return r << 16 | g << 8 | b;
    }
}
