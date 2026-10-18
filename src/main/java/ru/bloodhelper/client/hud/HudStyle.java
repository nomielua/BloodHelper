package ru.bloodhelper.client.hud;


import net.minecraft.client.gui.GuiGraphics;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.gui.UiDraw;
import ru.bloodhelper.client.gui.CustomFont;

/** Shared HUD typography and surfaces. All measurements use the same scale as drawing. */
public final class HudStyle {
    public static final int TEXT = 0xFFF1F1F3;
    public static final int MUTED = 0xFFB6B7BF;
    public static final int FAINT = 0xFF898B96;
    public static final int TRACK = 0xFF303139;
    private static final float TEXT_SCALE = 9f;
    private static final float LABEL_SCALE = 8f;

    private HudStyle() {}

    public static void text(GuiGraphics g, String value, int x, int y, int color, boolean shadow) {
        draw(g, CustomFont.regular(), value, x, y, TEXT_SCALE, color, shadow);
    }
    public static void strong(GuiGraphics g, String value, int x, int y, int color, boolean shadow) {
        draw(g, CustomFont.medium(), value, x, y, TEXT_SCALE, color, shadow);
    }
    public static void label(GuiGraphics g, String value, int x, int y, int color, boolean shadow) {
        draw(g, CustomFont.regular(), value, x, y, LABEL_SCALE, color, shadow);
    }
    public static int width(String value) { return measured(CustomFont.regular(), value, TEXT_SCALE); }
    public static int strongWidth(String value) { return measured(CustomFont.medium(), value, TEXT_SCALE); }
    public static int labelWidth(String value) { return measured(CustomFont.regular(), value, LABEL_SCALE); }
    public static String fit(String value, int maxWidth) { return fitted(CustomFont.regular(), value, maxWidth, TEXT_SCALE); }
    public static String fitStrong(String value, int maxWidth) { return fitted(CustomFont.medium(), value, maxWidth, TEXT_SCALE); }
    public static String fitLabel(String value, int maxWidth) { return fitted(CustomFont.regular(), value, maxWidth, LABEL_SCALE); }

    public static void panel(GuiGraphics g, int width, int height, ClientConfig config) {
        surface(g, 0, 0, width, height, 6, config);
    }
    /** Delta-style separate header and rows, tinted by the user's theme. */
    public static void surface(GuiGraphics g, int x, int y, int width, int height, int radius, ClientConfig config) {
        if (!config.background) return;
        int opacity = Math.clamp(config.backgroundAlpha, 0, 255);
        if (opacity == 0) return;
        int fill = UiDraw.blend(config.backgroundColor, config.accent, .035);
        UiDraw.rounded(g, x, y, width, height, radius, UiDraw.alpha(fill, opacity));
    }
    public static void separator(GuiGraphics g, int x, int y, int height) {
        g.fill(x, y + height / 4, x + 1, y + height - height / 4, 0x554F4F56);
    }
    public static void progress(GuiGraphics g, int x, int y, int width, float progress, int color) {
        if (width <= 0) return;
        UiDraw.rounded(g, x, y, width, 2, 1, TRACK);
        float amount = Float.isFinite(progress) ? Math.clamp(progress, 0, 1) : 0;
        int filled = Math.round(width * amount);
        if (filled > 0) UiDraw.rounded(g, x, y, filled, 2, 1, color);
    }
    public static String tr(String ru, String en) {
        return "ru".equals(BloodHelperClient.config().language) ? ru : en;
    }
    private static void draw(GuiGraphics g, CustomFont font, String value, int x, int y, float size,
                             int color, boolean shadow) {
        if (value == null || value.isEmpty()) return;
        if (shadow && BloodHelperClient.config().textShadow)
            font.draw(g, value, x + .5f, y + .5f, size, UiDraw.alpha(0, (color >>> 24) / 2));
        font.draw(g, value, x, y, size, color);
    }
    private static int measured(CustomFont font, String value, float size) {
        return value == null ? 0 : (int)Math.ceil(font.width(value, size));
    }
    private static String fitted(CustomFont font, String value, int maxWidth, float size) {
        return font.ellipsize(value, size, maxWidth);
    }
}
