package ru.bloodhelper.client.gui;

import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import ru.bloodhelper.client.BloodHelperClient;
import ru.bloodhelper.client.config.ClientConfig;
import ru.bloodhelper.client.hud.HudRenderer;
import ru.bloodhelper.client.hud.HudWidgets;
import ru.bloodhelper.client.hud.HudStyle;

import static ru.bloodhelper.client.hud.HudStyle.tr;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private String dragging;
    private String selected;
    private double grabX, grabY;
    private HudRenderer.Bounds toolbar;
    private HudRenderer.Bounds done;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("BLOODHELPER • HUD"));
        this.parent = parent;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0x600C090A);
        for (int x = width / 2 % 40; x < width; x += 40) g.fill(x, 0, x + 1, height, 0x07FFFFFF);
        for (int y = height / 2 % 40; y < height; y += 40) g.fill(0, y, width, y + 1, 0x07FFFFFF);
        if (dragging != null) drawGuides(g);

        HudRenderer.renderPreview(g);
        String hovered = widgetAt(mouseX, mouseY);
        int accent = BloodHelperClient.config().accent;
        for (var entry : HudRenderer.bounds().entrySet()) {
            var box = entry.getValue();
            boolean active = entry.getKey().equals(selected);
            boolean hot = entry.getKey().equals(hovered);
            int color = active ? UiDraw.alpha(accent, 190) : hot ? 0xC9CDD0DA : 0x565F636F;
            int x = box.x() - 2, y = box.y() - 2, w = box.width() + 4, h = box.height() + 4;
            if (active || hot) g.renderOutline(x, y, w, h, color);
            corners(g, x, y, w, h, color);
        }

        // Leave the entire canvas available while a widget follows the pointer.
        if (dragging == null) drawToolbar(g, mouseX, mouseY);
        else { toolbar = null; done = null; }
    }

    private void drawGuides(GuiGraphics g) {
        var box = HudRenderer.bounds().get(dragging);
        int accent = BloodHelperClient.config().accent;
        boolean centeredX = box != null && Math.abs(box.x() + box.width() / 2.0 - width / 2.0) < 1;
        boolean centeredY = box != null && Math.abs(box.y() + box.height() / 2.0 - height / 2.0) < 1;
        g.fill(width / 2, 0, width / 2 + 1, height, centeredX ? UiDraw.alpha(accent, 115) : 0x1BFFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, centeredY ? UiDraw.alpha(accent, 115) : 0x1BFFFFFF);
    }

    private void drawToolbar(GuiGraphics g, int mouseX, int mouseY) {
        int panelWidth = Math.min(300, Math.max(1, Math.min(width - 20, width / 2)));
        int panelHeight = 48;
        toolbar = freeToolbar(panelWidth, panelHeight);
        int x = toolbar.x(), y = toolbar.y();
        UiDraw.border(g, x, y, panelWidth, panelHeight, 4, 0xF5100D0E, 0xFF403336);
        int buttonWidth = Math.min(57, Math.max(36, panelWidth / 5));
        done = new HudRenderer.Bounds(x + panelWidth - buttonWidth - 9, y + 11, buttonWidth, 26);
        boolean overDone = done.contains(mouseX, mouseY);
        UiDraw.border(g, done.x(), done.y(), done.width(), done.height(), 5,
                overDone ? 0xFF422226 : 0xFF261A1D,
                overDone ? UiDraw.alpha(BloodHelperClient.config().accent, 160) : 0xFF4A3438);
        String finish = "Done";
        HudStyle.strong(g, finish, done.x() + (done.width() - HudStyle.strongWidth(finish)) / 2,
                done.y() + 8, HudStyle.TEXT, false);

        int textWidth = Math.max(0, done.x() - x - 20);
        UiIcons.draw(g, "hud", x + 10, y + 6, 13, BloodHelperClient.config().accent);
        String title = selected == null ? "HUD Editor" : selectionLabel();
        HudStyle.strong(g, HudStyle.fitStrong(title, textWidth - 19), x + 29, y + 7, HudStyle.TEXT, false);
        HudStyle.label(g, HudStyle.fitLabel(tr("Перетащи блок · Колесо — масштаб", "Drag a widget · Scroll to scale"), textWidth),
                x + 10, y + 22, HudStyle.MUTED, false);
        HudStyle.label(g, HudStyle.fitLabel(tr("R — сбросить · Esc — сохранить", "R — reset · Esc — save"), textWidth),
                x + 10, y + 34, HudStyle.FAINT, false);
    }

    private String selectionLabel() {
        String name = HudWidgets.get(selected, BloodHelperClient.config()).title();
        return name + " · " + String.format(Locale.ROOT, "%.0f%%", position(selected).scale * 100);
    }

    private HudRenderer.Bounds freeToolbar(int w, int h) {
        int left = Math.min(10, Math.max(0, width - w));
        int right = Math.max(0, width - w - 10);
        int top = Math.min(10, Math.max(0, height - h));
        int bottom = Math.max(0, height - h - 10);
        int center = Math.max(0, (width - w) / 2);
        var choices = new HudRenderer.Bounds[] {
                new HudRenderer.Bounds(center, bottom, w, h),
                new HudRenderer.Bounds(center, top, w, h),
                new HudRenderer.Bounds(left, bottom, w, h),
                new HudRenderer.Bounds(right, bottom, w, h),
                new HudRenderer.Bounds(left, top, w, h),
                new HudRenderer.Bounds(right, top, w, h),
                new HudRenderer.Bounds(center, Math.max(0, (height - h) / 2), w, h)
        };
        HudRenderer.Bounds best = choices[0];
        long score = Long.MAX_VALUE;
        for (var candidate : choices) {
            long overlap = 0;
            for (var box : HudRenderer.bounds().values()) {
                int overlapWidth = Math.max(0, Math.min(candidate.x() + w, box.x() + box.width() + 6) - Math.max(candidate.x(), box.x() - 6));
                int overlapHeight = Math.max(0, Math.min(candidate.y() + h, box.y() + box.height() + 6) - Math.max(candidate.y(), box.y() - 6));
                overlap += (long) overlapWidth * overlapHeight;
            }
            if (overlap < score) { score = overlap; best = candidate; }
            if (score == 0) break;
        }
        return best;
    }

    private static void corners(GuiGraphics g, int x, int y, int w, int h, int color) {
        int length = Math.min(5, Math.min(w, h) / 2);
        for (int dx : new int[] {0, w - 1}) {
            int armX = dx == 0 ? x : x + w - length;
            g.fill(armX, y, armX + length, y + 1, color);
            g.fill(armX, y + h - 1, armX + length, y + h, color);
            g.fill(x + dx, y, x + dx + 1, y + length, color);
            g.fill(x + dx, y + h - length, x + dx + 1, y + h, color);
        }
    }

    private String widgetAt(double x, double y) {
        // Match painting order if the user places widgets on top of one another.
        var entries = new ArrayList<>(HudRenderer.bounds().entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            if (entry.getValue().contains(x, y)) return entry.getKey();
        }
        return null;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            if (done != null && done.contains(event.x(), event.y())) { onClose(); return true; }
            if (toolbar != null && toolbar.contains(event.x(), event.y())) return true;
            selected = widgetAt(event.x(), event.y());
            if (selected != null) {
                dragging = selected;
                var box = HudRenderer.bounds().get(selected);
                grabX = event.x() - box.x();
                grabY = event.y() - box.y();
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging == null || event.button() != 0) return super.mouseDragged(event, dx, dy);
        var box = HudRenderer.bounds().get(dragging);
        if (box == null) return false;
        var p = position(dragging);
        var config = BloodHelperClient.config();
        var widget = HudWidgets.get(dragging, config);
        String align = widget.alignment();
        boolean bottom = widget.bottom();
        double x = Math.clamp(event.x() - grabX, 0, Math.max(0, width - box.width()));
        double y = Math.clamp(event.y() - grabY, 0, Math.max(0, height - box.height()));
        if (box.width() <= width && Math.abs(x + box.width() / 2.0 - width / 2.0) < 4) x = (width - box.width()) / 2.0;
        if (box.height() <= height && Math.abs(y + box.height() / 2.0 - height / 2.0) < 4) y = (height - box.height()) / 2.0;
        p.x = (x + (align.equals("CENTER") ? box.width() / 2.0 : align.equals("RIGHT") ? box.width() : 0)) / Math.max(1, width);
        p.y = bottom ? 1 - (y + box.height()) / Math.max(1, height) : y / Math.max(1, height);
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null && event.button() == 0) {
            dragging = null;
            BloodHelperClient.saveConfig();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (dragging != null || toolbar != null && toolbar.contains(x, y)) return false;
        String target = widgetAt(x, y);
        if (target == null || vertical == 0) return false;
        selected = target;
        var p = position(target);
        p.scale = Math.clamp(p.scale + vertical * .05, .5, 2);
        BloodHelperClient.saveConfig();
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_R) {
            var config = BloodHelperClient.config();
            HudWidgets.reset(config);
            dragging = null;
            selected = null;
            BloodHelperClient.saveConfig();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() {
        dragging = null;
        BloodHelperClient.saveConfig();
        minecraft.setScreen(parent);
    }

    private ClientConfig.HudPosition position(String key) {
        var config = BloodHelperClient.config();
        return HudWidgets.get(key, config).position();
    }
}
