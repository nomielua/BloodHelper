package ru.bloodhelper.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/** Small, texture-independent drawing primitives shared by the menu and HUD editor. */
public final class UiDraw {
    private static final java.util.Map<Integer, int[][]> CORNERS = new java.util.HashMap<>();
    private UiDraw() {}

    public static int alpha(int rgb, int opacity) {
        return (Math.clamp(opacity, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int blend(int first, int second, double amount) {
        double t = Math.clamp(amount, 0, 1);
        int r = (int) (((first >> 16) & 255) * (1 - t) + ((second >> 16) & 255) * t);
        int g = (int) (((first >> 8) & 255) * (1 - t) + ((second >> 8) & 255) * t);
        int b = (int) ((first & 255) * (1 - t) + (second & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    public static void rounded(GuiGraphics graphics, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(Math.max(0, radius), Math.min(w, h) / 2);
        if (r == 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        graphics.fill(x, y + r, x + w, y + h - r, color);
        int[][] mask = CORNERS.computeIfAbsent(r, UiDraw::cornerCoverage);
        for (int row = 0; row < r; row++) {
            int solid = 0;
            while (solid < r && mask[row][solid] < 255) solid++;
            graphics.fill(x + solid, y + row, x + w - solid, y + row + 1, color);
            graphics.fill(x + solid, y + h - row - 1, x + w - solid, y + h - row, color);
            for (int col = 0; col < solid; col++) {
                int coverage = mask[row][col];
                if (coverage == 0) continue;
                int edge = alpha(color, ((color >>> 24) * coverage + 127) / 255);
                graphics.fill(x + col, y + row, x + col + 1, y + row + 1, edge);
                graphics.fill(x + w - col - 1, y + row, x + w - col, y + row + 1, edge);
                graphics.fill(x + col, y + h - row - 1, x + col + 1, y + h - row, edge);
                graphics.fill(x + w - col - 1, y + h - row - 1, x + w - col, y + h - row, edge);
            }
        }
    }

    private static int[][] cornerCoverage(int radius) {
        int[][] result = new int[radius][radius];
        for (int y = 0; y < radius; y++) for (int x = 0; x < radius; x++) {
            int inside = 0;
            for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                double dx = radius - x - (sx + .5) / 4;
                double dy = radius - y - (sy + .5) / 4;
                if (dx * dx + dy * dy <= radius * radius) inside++;
            }
            result[y][x] = (inside * 255 + 8) / 16;
        }
        return result;
    }

    public static void border(GuiGraphics graphics, int x, int y, int w, int h, int radius, int fill, int border) {
        rounded(graphics, x, y, w, h, radius, border);
        rounded(graphics, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
    }

    /** One-pixel outline without painting underneath a translucent interior. */
    public static void outline(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.clamp(radius, 1, Math.max(1, Math.min(w, h) / 2));
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
        for (int yy = 0; yy < r; yy++) for (int xx = 0; xx < r; xx++) {
            int count = 0;
            for (int sy = 0; sy < 4; sy++) for (int sx = 0; sx < 4; sx++) {
                double dx = r - xx - (sx + .5) / 4, dy = r - yy - (sy + .5) / 4;
                double d = dx * dx + dy * dy;
                if (d <= r * r && d >= (r - 1.) * (r - 1.)) count++;
            }
            if (count == 0) continue;
            int edge = alpha(color, (color >>> 24) * count / 16);
            g.fill(x + xx, y + yy, x + xx + 1, y + yy + 1, edge);
            g.fill(x + w - xx - 1, y + yy, x + w - xx, y + yy + 1, edge);
            g.fill(x + xx, y + h - yy - 1, x + xx + 1, y + h - yy, edge);
            g.fill(x + w - xx - 1, y + h - yy - 1, x + w - xx, y + h - yy, edge);
        }
    }

}
