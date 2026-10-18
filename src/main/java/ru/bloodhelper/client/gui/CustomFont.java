package ru.bloodhelper.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bundled SF Pro outlines, AWT shaping/rasterization and our own texture atlases.
 * Sizes are logical em units; (x,y) is the top-left of a line box, not a baseline.
 * No Minecraft font, text layout, glyph provider or text rendering is involved.
 * Measurement is CPU-only. Drawing must run on the render thread.
 *
 * <p>TextureManager owns every DynamicTexture (including its NativeImage). Pages
 * are append-only and never evicted while GUI submissions can reference them.
 * Normal resource reload leaves these dynamic textures intact. {@link #invalidate()}
 * requests a lazy in-place upload; externally closed pages are rebuilt before use.
 * No GPU texture view is cached by this renderer. TextureManager closes pages at
 * shutdown. The font binaries are immutable classpath resources, not pack fonts.
 */
public final class CustomFont {
    private static final float METRIC_EM = 128f;
    private static final int SUPERSAMPLE = 4;
    private static final int PADDING = 2;
    private static final int MAX_LAYOUTS = 512;
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final AtomicLong UPLOAD_GENERATION = new AtomicLong();
    private static final AtomicLong NEXT_PAGE = new AtomicLong();

    private final Font source;
    private final Font metricFont;
    private final float ascent;
    private final float height;
    private final String resource;
    private final Map<String, Layout> layouts = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Layout> eldest) {
            return size() > MAX_LAYOUTS;
        }
    };
    // Used exclusively on the render thread. A few power-of-two raster sizes
    // cover menu/HUD scaling without allocating a new atlas for each slider step.
    private final Map<Integer, Atlas> atlases = new HashMap<>();
    private TextureManager textureOwner;

    private static final class RegularHolder {
        private static final CustomFont INSTANCE = new CustomFont("sfpromedium.otf");
    }

    private static final class MediumHolder {
        private static final CustomFont INSTANCE = new CustomFont("sfprosemibold.otf");
    }

    private CustomFont(String file) {
        resource = "/assets/bloodhelper/fonts/" + file;
        try (InputStream input = CustomFont.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing bundled font " + resource);
            source = Font.createFont(Font.TRUETYPE_FONT, input);
        } catch (IOException | FontFormatException e) {
            // A missing/broken asset is an installation error, never a request
            // to silently fall back to a system font or the vanilla glyph engine.
            throw new IllegalStateException("Cannot load custom font " + resource, e);
        }
        metricFont = source.deriveFont(METRIC_EM).deriveFont(Map.of(
                TextAttribute.KERNING, TextAttribute.KERNING_ON,
                TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON));
        var metrics = metricFont.getLineMetrics("HgАду", FRC);
        ascent = metrics.getAscent() / METRIC_EM;
        height = metrics.getHeight() / METRIC_EM;
    }

    /** Regular menu face: the donor's actual SF Pro Display Medium. */
    public static CustomFont regular() { return RegularHolder.INSTANCE; }

    /** Emphasized menu face: the donor's actual SF Pro Display Semibold. */
    public static CustomFont medium() { return MediumHolder.INSTANCE; }

    public int sourceGlyphCount() { return source.getNumGlyphs(); }

    public String sourceName() { return source.getFontName(Locale.ROOT); }

    /** Safe on any thread, even between two draws; does not close queued views. */
    public static void invalidate() { UPLOAD_GENERATION.incrementAndGet(); }

    /** Advance width of the widest line; tabs occupy four spaces. */
    public float width(String text, float size) {
        if (text == null || text.isEmpty() || !validSize(size)) return 0;
        float widest = 0;
        for (String line : normalize(text).split("\n", -1)) {
            widest = Math.max(widest, layout(line).advance * size);
        }
        return widest;
    }

    public float lineHeight(float size) { return validSize(size) ? height * size : 0; }

    /**
     * UTF-16 insertion positions for a single-line editor. These come from the
     * same complete shaped layout as drawing, never separately shaped prefixes.
     * Kerning is shared across the boundary between adjacent glyph clusters.
     * Within a ligature, base characters share its shaped span in proportion to
     * their unligated advances; combining marks stay at their base's trailing
     * caret. A surrogate's interior entry repeats the codepoint's leading caret.
     * The returned array is independent of the cached layout and may be retained.
     */
    public float[] caretAdvances(String text, float size) {
        if (text == null) text = "";
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("Caret positions require a single line without tabs");
        }
        float[] result = new float[text.length() + 1];
        if (!validSize(size) || text.isEmpty()) return result;
        float[] positions = layout(text).carets;
        for (int i = 0; i < result.length; i++) result[i] = positions[i] * size;
        return result;
    }

    /** Single-line ellipsis, keeping Unicode character clusters intact. */
    public String ellipsize(String text, float size, float width) {
        if (text == null || text.isEmpty() || !validSize(size) || !(width > 0)) return "";
        String line = normalize(text).replace('\n', ' ');
        if (width(line, size) <= width) return line;
        String suffix = "…";
        if (width(suffix, size) > width) return "";
        List<Integer> boundaries = boundaries(line, false);
        int low = 0, high = boundaries.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (width(line.substring(0, boundaries.get(mid)) + suffix, size) <= width) low = mid;
            else high = mid - 1;
        }
        String prefix = line.substring(0, boundaries.get(low)).stripTrailing();
        // Recheck after removing trailing whitespace: shaping can change kerning.
        while (!prefix.isEmpty() && width(prefix + suffix, size) > width) {
            List<Integer> shorter = boundaries(prefix, false);
            prefix = prefix.substring(0, shorter.get(shorter.size() - 2)).stripTrailing();
        }
        return prefix + suffix;
    }

    /**
     * Wrap at Unicode line opportunities, falling back to character clusters for
     * long words. Explicit blank lines are preserved. An indivisible cluster may
     * exceed the requested width; it is emitted alone to guarantee progress.
     */
    public List<String> wrap(String text, float size, float width) {
        if (text == null || text.isEmpty()) return List.of("");
        if (!validSize(size) || !(width > 0)) return List.of();
        List<String> result = new ArrayList<>();
        for (String paragraph : normalize(text).split("\n", -1)) {
            if (paragraph.isEmpty()) {
                result.add("");
                continue;
            }
            List<Integer> characters = boundaries(paragraph, false);
            List<Integer> words = boundaries(paragraph, true);
            int start = 0;
            while (start < paragraph.length()) {
                int end = start;
                for (int boundary : characters) {
                    if (boundary <= start) continue;
                    if (width(paragraph.substring(start, boundary), size) > width) break;
                    end = boundary;
                }
                if (end == paragraph.length()) {
                    result.add(paragraph.substring(start).stripTrailing());
                    break;
                }
                if (end == start) {
                    for (int boundary : characters) {
                        if (boundary > start) { end = boundary; break; }
                    }
                } else {
                    int wordEnd = start;
                    for (int boundary : words) {
                        if (boundary > end) break;
                        if (boundary > start) wordEnd = boundary;
                    }
                    if (wordEnd > start) end = wordEnd;
                }
                result.add(paragraph.substring(start, end).stripTrailing());
                start = end;
                while (start < paragraph.length()) {
                    int cp = paragraph.codePointAt(start);
                    if (!Character.isWhitespace(cp)) break;
                    start += Character.charCount(cp);
                }
            }
        }
        return List.copyOf(result);
    }

    public void draw(GuiGraphics g, String text, float x, float y, float size, int argb) {
        if (text == null || text.isEmpty() || !validSize(size) || (argb >>> 24) == 0) return;
        if (!Float.isFinite(x) || !Float.isFinite(y)) return;
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        TextureManager manager = minecraft.getTextureManager();
        if (textureOwner != manager) {
            // A replaced manager owns/closes its old textures; never use its pages.
            atlases.clear();
            textureOwner = manager;
        }
        var pose = g.pose();
        double transformScale = Math.max(Math.hypot(pose.m00(), pose.m01()),
                Math.hypot(pose.m10(), pose.m11()));
        double physicalScale = transformScale * minecraft.getWindow().getGuiScale();
        int em = rasterEm(size, physicalScale);
        Atlas atlas = atlases.computeIfAbsent(em, key -> new Atlas(key, manager));
        String[] lines = normalize(text).split("\n", -1);
        Layout[] shaped = new Layout[lines.length];
        for (int line = 0; line < lines.length; line++) {
            shaped[line] = layout(lines[line]);
            for (int code : shaped[line].codes) atlas.glyph(code);
        }
        // Populate all new glyphs before uploading, at most once per dirty page.
        atlas.upload();
        float scale = size * SUPERSAMPLE / em;
        for (int line = 0; line < shaped.length; line++) {
            Layout layout = shaped[line];
            float baseline = y + line * lineHeight(size) + ascent * size;
            for (int i = 0; i < layout.codes.length; i++) {
                Glyph glyph = atlas.glyphs.get(layout.codes[i]);
                if (glyph.page == null) continue; // spaces and invisible glyphs
                pose.pushMatrix();
                try {
                    pose.translate(x + layout.x[i] * size + glyph.left * scale,
                            baseline + layout.y[i] * size + glyph.top * scale);
                    pose.scale(scale, scale);
                    g.blit(RenderPipelines.GUI_TEXTURED, glyph.page.id, 0, 0,
                            glyph.u, glyph.v, glyph.width, glyph.height,
                            glyph.width, glyph.height, glyph.page.side, glyph.page.side, argb);
                } finally {
                    pose.popMatrix();
                }
            }
        }
    }

    public void drawShadow(GuiGraphics g, String text, float x, float y, float size, int argb) {
        int shadow = ((argb >>> 24) * 3 / 5) << 24;
        draw(g, text, x + .75f, y + .75f, size, shadow);
        draw(g, text, x, y, size, argb);
    }

    private static boolean validSize(float size) { return Float.isFinite(size) && size > 0; }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
    }

    private static List<Integer> boundaries(String text, boolean words) {
        BreakIterator iterator = words ? BreakIterator.getLineInstance(Locale.ROOT)
                : BreakIterator.getCharacterInstance(Locale.ROOT);
        iterator.setText(text);
        List<Integer> result = new ArrayList<>();
        for (int end = iterator.first(); end != BreakIterator.DONE; end = iterator.next()) {
            // Never let an iterator implementation split a UTF-16 surrogate pair.
            if (end == 0 || end == text.length() || !Character.isLowSurrogate(text.charAt(end))
                    || !Character.isHighSurrogate(text.charAt(end - 1))) result.add(end);
        }
        return result;
    }

    private synchronized Layout layout(String text) {
        Layout cached = layouts.get(text);
        if (cached != null) return cached;
        char[] chars = text.toCharArray();
        GlyphVector vector = metricFont.layoutGlyphVector(FRC, chars, 0, chars.length,
                Font.LAYOUT_LEFT_TO_RIGHT);
        int count = vector.getNumGlyphs();
        int[] codes = vector.getGlyphCodes(0, count, null);
        float[] x = new float[count], y = new float[count];
        for (int i = 0; i < count; i++) {
            var position = vector.getGlyphPosition(i);
            x[i] = (float) position.getX() / METRIC_EM;
            y[i] = (float) position.getY() / METRIC_EM;
        }
        Layout result = new Layout(codes, x, y, shapedCarets(text, vector),
                (float) vector.getGlyphPosition(count).getX() / METRIC_EM);
        layouts.put(text, result);
        return result;
    }

    private float[] shapedCarets(String text, GlyphVector vector) {
        int length = text.length();
        float[] carets = new float[length + 1];
        if (length == 0) return carets;
        float[] starts = new float[length], ends = new float[length];
        Arrays.fill(starts, Float.NaN);
        Arrays.fill(ends, Float.NEGATIVE_INFINITY);
        for (int i = 0; i < vector.getNumGlyphs(); i++) {
            int character = vector.getGlyphCharIndex(i);
            if (character < 0 || character >= length) continue;
            if (character > 0 && Character.isLowSurrogate(text.charAt(character))
                    && Character.isHighSurrogate(text.charAt(character - 1))) character--;
            // A mark can have its own positioned glyph, or be composed into the
            // preceding glyph. Both representations must expose the same carets.
            while (character > 0 && isCaretContinuation(text.codePointAt(character))) {
                character = text.offsetByCodePoints(character, -1);
            }
            float start = (float) vector.getGlyphPosition(i).getX();
            float end = start + vector.getGlyphMetrics(i).getAdvanceX();
            starts[character] = Float.isNaN(starts[character]) ? start : Math.min(starts[character], start);
            ends[character] = Math.max(ends[character], end);
        }
        // Keep invisible leading characters addressable even if shaping dropped them.
        if (Float.isNaN(starts[0])) { starts[0] = 0; ends[0] = 0; }
        List<Integer> clusters = new ArrayList<>();
        for (int i = 0; i < length; i++) if (!Float.isNaN(starts[i])) clusters.add(i);
        clusters.add(length);
        float advance = (float) vector.getGlyphPosition(vector.getNumGlyphs()).getX();
        carets[length] = advance;
        for (int i = 1; i < clusters.size() - 1; i++) {
            int previous = clusters.get(i - 1), current = clusters.get(i);
            // Match the visual boundary through a kerned gap, including a
            // negative gap: neither independently measured prefix is correct.
            carets[current] = Math.clamp((ends[previous] + starts[current]) * .5f,
                    carets[previous], Math.max(carets[previous], advance));
        }
        for (int i = 0; i < clusters.size() - 1; i++) {
            int start = clusters.get(i), end = clusters.get(i + 1);
            if (text.codePointCount(start, end) == 1) {
                for (int p = start + 1; p < end; p++) carets[p] = carets[start];
                continue;
            }
            float[] weights = new float[end - start];
            float total = 0;
            for (int p = start; p < end;) {
                int cp = text.codePointAt(p);
                if (!isCaretContinuation(cp)) {
                    // createGlyphVector maps outlines without contextual layout;
                    // it is used only to divide an existing ligature's span.
                    GlyphVector plain = metricFont.createGlyphVector(FRC, new String(Character.toChars(cp)));
                    float weight = Math.max(0, (float) plain.getGlyphPosition(plain.getNumGlyphs()).getX());
                    weights[p - start] = weight;
                    total += weight;
                }
                p += Character.charCount(cp);
            }
            float accumulated = 0, left = carets[start], span = carets[end] - left;
            for (int p = start; p < end;) {
                int next = p + Character.charCount(text.codePointAt(p));
                for (int interior = p + 1; interior < next; interior++) carets[interior] = carets[p];
                accumulated += weights[p - start];
                // All-mark clusters have no insertion point inside the mark.
                carets[next] = total > 0 ? left + span * accumulated / total : left + span;
                p = next;
            }
            carets[end] = left + span; // Retain the exact full-layout boundary.
        }
        for (int i = 0; i < carets.length; i++) carets[i] /= METRIC_EM;
        return carets;
    }

    private static boolean isCaretContinuation(int codepoint) {
        int type = Character.getType(codepoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || type == Character.FORMAT;
    }

    private static int rasterEm(float size, double physicalScale) {
        // Rasterize >=4 samples per logical/physical pixel, then average coverage
        // into atlas texels. Linear minification alone would discard those samples.
        // The 2048-em ceiling bounds GPU allocations for pathological transforms.
        double target = size * SUPERSAMPLE * Math.max(1, physicalScale);
        int em = 64;
        while (em < target && em < 2048) em *= 2;
        return em;
    }

    private record Layout(int[] codes, float[] x, float[] y, float[] carets, float advance) {}
    private record Glyph(Page page, int u, int v, int width, int height, int left, int top) {}

    private final class Atlas {
        private final Font font;
        private final int pageSide;
        private final TextureManager manager;
        private final Map<Integer, Glyph> glyphs = new HashMap<>();
        private final List<Page> pages = new ArrayList<>();

        private Atlas(int em, TextureManager manager) {
            font = source.deriveFont((float) em);
            pageSide = Math.max(512, em * 2 / SUPERSAMPLE);
            this.manager = manager;
        }

        private Glyph glyph(int code) {
            Glyph cached = glyphs.get(code);
            if (cached != null) return cached;
            GlyphVector vector = font.createGlyphVector(FRC, new int[]{code});
            Rectangle bounds = vector.getGlyphPixelBounds(0, FRC, 0, 0);
            if (bounds.isEmpty()) {
                Glyph empty = new Glyph(null, 0, 0, 0, 0, 0, 0);
                glyphs.put(code, empty);
                return empty;
            }
            // Align the coverage grid before padding, including negative bearings.
            int left = Math.floorDiv(bounds.x, SUPERSAMPLE) - PADDING;
            int top = Math.floorDiv(bounds.y, SUPERSAMPLE) - PADDING;
            int right = Math.ceilDiv(bounds.x + bounds.width, SUPERSAMPLE) + PADDING;
            int bottom = Math.ceilDiv(bounds.y + bounds.height, SUPERSAMPLE) + PADDING;
            int w = right - left, h = bottom - top;
            int rasterWidth = w * SUPERSAMPLE, rasterHeight = h * SUPERSAMPLE;
            BufferedImage bitmap = new BufferedImage(rasterWidth, rasterHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = bitmap.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                graphics.setColor(Color.WHITE);
                graphics.drawGlyphVector(vector, -left * SUPERSAMPLE, -top * SUPERSAMPLE);
            } finally {
                graphics.dispose();
            }
            Page page = null;
            for (Page candidate : pages) {
                if (candidate.reserve(w, h)) { page = candidate; break; }
            }
            if (page == null) {
                int side = pageSide;
                while (side < Math.max(w, h)) side *= 2;
                page = new Page(manager, side);
                pages.add(page);
                page.reserve(w, h);
            }
            int u = page.reservedX, v = page.reservedY;
            int[] pixels = bitmap.getRGB(0, 0, rasterWidth, rasterHeight, null, 0, rasterWidth);
            int samples = SUPERSAMPLE * SUPERSAMPLE;
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    int coverage = 0;
                    for (int sy = 0; sy < SUPERSAMPLE; sy++) {
                        int offset = (row * SUPERSAMPLE + sy) * rasterWidth + col * SUPERSAMPLE;
                        for (int sx = 0; sx < SUPERSAMPLE; sx++) coverage += pixels[offset + sx] >>> 24;
                    }
                    // White RGB even in padding prevents dark fringes under linear filtering.
                    int alpha = (coverage + samples / 2) / samples;
                    page.pixels[(v + row) * page.side + u + col] = (alpha << 24) | 0x00FFFFFF;
                }
            }
            page.dirty = true;
            Glyph glyph = new Glyph(page, u, v, w, h, left, top);
            glyphs.put(code, glyph);
            return glyph;
        }

        private void upload() { for (Page page : pages) page.upload(); }
    }

    private static final class Page {
        private final TextureManager manager;
        private final Identifier id;
        private final int side;
        private final int[] pixels;
        private LinearTexture texture;
        private long uploadedGeneration = -1;
        private boolean dirty = true;
        private int cursorX, cursorY, rowHeight, reservedX, reservedY;

        private Page(TextureManager manager, int side) {
            this.manager = manager;
            this.side = side;
            id = Identifier.fromNamespaceAndPath("bloodhelper", "customfont/page_" + NEXT_PAGE.getAndIncrement());
            pixels = new int[side * side];
            Arrays.fill(pixels, 0x00FFFFFF);
        }

        private boolean reserve(int w, int h) {
            if (w > side || h > side) return false;
            int x = cursorX, y = cursorY, row = rowHeight;
            if (x + w > side) { x = 0; y += row; row = 0; }
            if (y + h > side) return false;
            reservedX = x;
            reservedY = y;
            cursorX = x + w;
            cursorY = y;
            rowHeight = Math.max(row, h);
            return true;
        }

        private void upload() {
            long generation = UPLOAD_GENERATION.get();
            boolean live = texture != null && texture.live() && manager.getTexture(id) == texture;
            if (live && !dirty && uploadedGeneration == generation) return;
            NativeImage image = live ? texture.getPixels() : new NativeImage(side, side, false);
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) image.setPixel(x, y, pixels[y * side + x]);
            }
            if (live) texture.upload();
            else {
                LinearTexture replacement;
                try {
                    replacement = new LinearTexture(id.toString(), image);
                } catch (RuntimeException | Error e) {
                    image.close();
                    throw e;
                }
                try {
                    manager.register(id, replacement);
                } catch (RuntimeException | Error e) {
                    replacement.close();
                    throw e;
                }
                texture = replacement;
            }
            dirty = false;
            uploadedGeneration = generation;
        }
    }

    /** Sampling belongs to this texture alone; no global/vanilla state changes. */
    private static final class LinearTexture extends DynamicTexture {
        private LinearTexture(String label, NativeImage image) {
            super(() -> label, image);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }

        private boolean live() {
            return getPixels() != null && texture != null && !texture.isClosed()
                    && textureView != null && !textureView.isClosed();
        }
    }
}
