package ru.bloodhelper.client.hud;

/** Shared geometry for painting, editor bounds and music controls. */
public final class HudLayout {
    public record Placement(int x, int y, int width, int height, double scale) {}
    private HudLayout() {}

    public static double scale(int screenWidth, int screenHeight, int width, int height, double requested) {
        double safe = Double.isFinite(requested) ? Math.clamp(requested, .5, 2) : 1;
        // Minecraft GUI Scale changes the logical viewport. Keep the default eight-widget
        // layout proportional on small viewports, while retaining each user's scale multiplier.
        safe *= Math.min(1, Math.min(Math.max(1, screenWidth) / 640.0, Math.max(1, screenHeight) / 400.0));
        return Math.min(safe, Math.min(Math.max(1, screenWidth - 4) / (double)Math.max(1, width),
                Math.max(1, screenHeight - 4) / (double)Math.max(1, height)));
    }

    public static Placement place(int screenWidth, int screenHeight, int width, int height,
                                  double requestedScale, double anchorX, double anchorY,
                                  String alignment, boolean bottom) {
        double scale = scale(screenWidth, screenHeight, width, height, requestedScale);
        int scaledWidth = (int)Math.ceil(width * scale), scaledHeight = (int)Math.ceil(height * scale);
        int x = (int)(screenWidth * anchorX) - ("CENTER".equals(alignment) ? scaledWidth / 2
                : "RIGHT".equals(alignment) ? scaledWidth : 0);
        int y = bottom ? (int)(screenHeight * (1 - anchorY)) - scaledHeight : (int)(screenHeight * anchorY);
        x = Math.clamp(x, 0, Math.max(0, screenWidth - scaledWidth));
        y = Math.clamp(y, 0, Math.max(0, screenHeight - scaledHeight));
        return new Placement(x, y, scaledWidth, scaledHeight, scale);
    }
}
