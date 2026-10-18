package ru.bloodhelper.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A single-line editor drawn entirely with {@link CustomFont}. Bounds and mouse
 * events use the same local coordinates as the parent's GuiGraphics pose.
 * The parent draws the surrounding box and manages focus between fields.
 */
public final class GuiTextField {
    private static final long BLINK_NANOS = 500_000_000L;
    private static final float CARET_WIDTH = 1.0f;

    private int x, y, width, height;
    private String value = "";
    private String hint;
    private int maxLength = 128;
    // UTF-16 indices, always at codepoint boundaries; anchor stays put with Shift.
    private int cursor, anchor;
    private boolean focused, dragging, draggingWords;
    private int dragWordStart, dragWordEnd;
    private double dragMouseX;
    private long dragUpdatedAt;
    private long blinkStartedAt = System.nanoTime();
    private float textSize = 14.0f;
    private int textColor = 0xFFF5F3F2;
    private int hintColor = 0xFF90868A;
    private float scrollX;
    private Predicate<String> filter = text -> true;
    private Consumer<String> responder = text -> {};

    // Measuring lazily also keeps construction and keyboard editing independent
    // of font/texture initialization. Only codepoint boundary entries are used.
    private boolean metricsDirty = true;
    private CustomFont measuredFont;
    private float[] advances = {0.0f};

    public GuiTextField(int x, int y, int width, int height, String hint) {
        this.x = x;
        this.y = y;
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        setHint(hint);
    }

    public String getValue() { return value; }

    public void setValue(String value) {
        String next = cleanText(Objects.requireNonNull(value, "value"), maxLength);
        if (filter.test(next)) changeValue(next, next.length(), next.length());
    }

    /** Limits UTF-16 length, without retaining half of a supplementary character. */
    public void setMaxLength(int maxLength) {
        this.maxLength = Math.max(0, maxLength);
        if (value.length() > this.maxLength) {
            int end = boundaryAtOrBefore(value, this.maxLength);
            // A new hard limit must apply even if the old filter rejects a prefix.
            changeValue(value.substring(0, end), Math.min(cursor, end), Math.min(anchor, end));
        }
    }

    public void setFilter(Predicate<String> filter) {
        this.filter = Objects.requireNonNull(filter, "filter");
    }

    public void setResponder(Consumer<String> responder) {
        this.responder = Objects.requireNonNull(responder, "responder");
    }

    public void setHint(String hint) {
        this.hint = cleanText(hint == null ? "" : hint, Integer.MAX_VALUE);
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public void setX(int x) { this.x = x; }
    public void setY(int y) { this.y = y; }
    public void setWidth(int width) { this.width = Math.max(0, width); }
    public void setHeight(int height) { this.height = Math.max(0, height); }

    public void setTextSize(float textSize) {
        if (!Float.isFinite(textSize) || textSize <= 0.0f) {
            throw new IllegalArgumentException("Text size must be finite and positive");
        }
        if (this.textSize != textSize) {
            this.textSize = textSize;
            metricsDirty = true;
        }
    }

    public void setTextColor(int argb) { textColor = argb; }
    public void setHintColor(int argb) { hintColor = argb; }

    public void setFocused(boolean focused) {
        if (this.focused != focused) resetBlink();
        this.focused = focused;
        if (!focused) stopDragging();
    }

    public boolean isFocused() { return focused; }
    public boolean canConsumeInput() { return focused; }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (width <= 0 || height <= 0) return;
        CustomFont font = CustomFont.regular();
        measure(font);
        keepCursorVisible();
        long now = System.nanoTime();
        if (focused && dragging) updateDrag(now);

        float lineHeight = font.lineHeight(textSize);
        float textY = y + (height - lineHeight) * 0.5f;
        float textX = x - scrollX;
        // GuiGraphics transforms this local rectangle and intersects its scissor
        // stack. Pop exactly our own clip, including if font drawing throws.
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            if (focused && cursor != anchor) {
                fill(graphics, textX + advances[Math.min(cursor, anchor)], textY,
                        textX + advances[Math.max(cursor, anchor)], textY + lineHeight,
                        0x805C638C);
            }
            if (value.isEmpty()) {
                font.draw(graphics, hint, x, textY, textSize, hintColor);
            } else {
                font.draw(graphics, value, textX, textY, textSize, textColor);
            }
            if (focused && (now - blinkStartedAt) / BLINK_NANOS % 2 == 0) {
                float caretX = textX + advances[cursor];
                fill(graphics, caretX, textY, caretX + CARET_WIDTH, textY + lineHeight, textColor);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (!contains(event.x(), event.y())) {
            setFocused(false);
            return false;
        }
        setFocused(true);
        measure(CustomFont.regular());
        keepCursorVisible();
        dragging = true;
        dragMouseX = event.x();
        dragUpdatedAt = System.nanoTime();
        draggingWords = doubled && !event.hasShiftDown() && !value.isEmpty();
        if (draggingWords) {
            int character = characterAt(event.x());
            dragWordStart = runStart(character);
            dragWordEnd = runEnd(character);
            anchor = dragWordStart;
            cursor = dragWordEnd;
            resetBlink();
        } else {
            moveCursor(cursorAt(event.x()), event.hasShiftDown());
        }
        keepCursorVisible();
        return true;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!focused || !dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        measure(CustomFont.regular());
        keepCursorVisible();
        dragMouseX = event.x();
        updateDrag(System.nanoTime());
        return true;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        stopDragging();
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!canConsumeInput()) return false;
        if (event.isSelectAll()) {
            stopDragging();
            anchor = 0;
            cursor = value.length();
            resetBlink();
            return true;
        }
        if (event.isCopy()) {
            copySelection();
            return true;
        }
        if (event.isCut()) {
            if (cursor != anchor) {
                copySelection();
                replaceRange(Math.min(cursor, anchor), Math.max(cursor, anchor), "");
            }
            return true;
        }
        if (event.isPaste()) {
            insertText(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }

        boolean selecting = event.hasShiftDown();
        boolean byWord = event.hasControlDownWithQuirk();
        switch (event.key()) {
            case GLFW.GLFW_KEY_LEFT -> {
                stopDragging();
                int next = !selecting && !byWord && cursor != anchor ? Math.min(cursor, anchor)
                        : byWord ? previousWord(cursor) : previousCodepoint(cursor);
                moveCursor(next, selecting);
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                stopDragging();
                int next = !selecting && !byWord && cursor != anchor ? Math.max(cursor, anchor)
                        : byWord ? nextWord(cursor) : nextCodepoint(cursor);
                moveCursor(next, selecting);
            }
            case GLFW.GLFW_KEY_HOME -> {
                stopDragging();
                moveCursor(0, selecting);
            }
            case GLFW.GLFW_KEY_END -> {
                stopDragging();
                moveCursor(value.length(), selecting);
            }
            case GLFW.GLFW_KEY_BACKSPACE -> delete(true, byWord);
            case GLFW.GLFW_KEY_DELETE -> delete(false, byWord);
            default -> { return false; }
        }
        return true;
    }

    public boolean charTyped(CharacterEvent event) {
        if (!canConsumeInput() || !allowedCodepoint(event.codepoint())) return false;
        int modifiers = event.modifiers();
        // AltGr may report Ctrl+Alt while producing ordinary printable text.
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0
                || (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (modifiers & GLFW.GLFW_MOD_ALT) == 0) {
            return false;
        }
        insertText(event.codepointAsString());
        return true;
    }

    private void insertText(String text) {
        int start = Math.min(cursor, anchor);
        int end = Math.max(cursor, anchor);
        int available = maxLength - (value.length() - (end - start));
        String insertion = cleanText(text, available);
        // An empty/invalid clipboard or an unfit codepoint must not erase a selection.
        if (!insertion.isEmpty()) replaceRange(start, end, insertion);
    }

    private void delete(boolean backwards, boolean byWord) {
        stopDragging();
        if (cursor != anchor) {
            replaceRange(Math.min(cursor, anchor), Math.max(cursor, anchor), "");
        } else {
            int other = backwards ? (byWord ? previousWord(cursor) : previousCodepoint(cursor))
                    : (byWord ? nextWord(cursor) : nextCodepoint(cursor));
            replaceRange(Math.min(cursor, other), Math.max(cursor, other), "");
        }
    }

    private void replaceRange(int start, int end, String insertion) {
        String next = value.substring(0, start) + insertion + value.substring(end);
        if (filter.test(next)) {
            int nextCursor = start + insertion.length();
            changeValue(next, nextCursor, nextCursor);
        }
    }

    private void changeValue(String next, int nextCursor, int nextAnchor) {
        boolean changed = !value.equals(next);
        value = next;
        cursor = nextCursor;
        anchor = nextAnchor;
        metricsDirty |= changed;
        stopDragging();
        resetBlink();
        // Update all editor state before allowing a responder to inspect it.
        if (changed) responder.accept(value);
    }

    private void copySelection() {
        if (cursor != anchor) {
            Minecraft.getInstance().keyboardHandler.setClipboard(
                    value.substring(Math.min(cursor, anchor), Math.max(cursor, anchor)));
        }
    }

    private void moveCursor(int position, boolean selecting) {
        cursor = position;
        if (!selecting) anchor = position;
        resetBlink();
    }

    private int previousCodepoint(int position) {
        return position == 0 ? 0 : value.offsetByCodePoints(position, -1);
    }

    private int nextCodepoint(int position) {
        return position == value.length() ? position : value.offsetByCodePoints(position, 1);
    }

    private int previousWord(int position) {
        while (position > 0 && wordKind(value.codePointBefore(position)) == 0) {
            position = previousCodepoint(position);
        }
        return position == 0 ? 0 : runStart(previousCodepoint(position));
    }

    private int nextWord(int position) {
        if (position < value.length()) position = runEnd(position);
        while (position < value.length() && wordKind(value.codePointAt(position)) == 0) {
            position = nextCodepoint(position);
        }
        return position;
    }

    private int runStart(int position) {
        int kind = wordKind(value.codePointAt(position));
        while (position > 0 && wordKind(value.codePointBefore(position)) == kind) {
            position = previousCodepoint(position);
        }
        return position;
    }

    private int runEnd(int position) {
        int kind = wordKind(value.codePointAt(position));
        do {
            position = nextCodepoint(position);
        } while (position < value.length() && wordKind(value.codePointAt(position)) == kind);
        return position;
    }

    private static int wordKind(int codepoint) {
        if (Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)) return 0;
        int type = Character.getType(codepoint);
        return Character.isLetterOrDigit(codepoint) || codepoint == '_'
                || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK ? 1 : 2;
    }

    private void measure(CustomFont font) {
        if (!metricsDirty && measuredFont == font) return;
        advances = font.caretAdvances(value, textSize);
        measuredFont = font;
        metricsDirty = false;
    }

    private float viewportWidth() { return Math.max(0.0f, width - CARET_WIDTH); }
    private float maxScroll() { return Math.max(0.0f, advances[value.length()] - viewportWidth()); }

    private void keepCursorVisible() {
        float caretX = advances[cursor];
        if (caretX < scrollX) scrollX = caretX;
        if (caretX > scrollX + viewportWidth()) scrollX = caretX - viewportWidth();
        scrollX = Math.clamp(scrollX, 0.0f, maxScroll());
    }

    private int cursorAt(double mouseX) {
        double target = mouseX - x + scrollX;
        for (int position = 0; position < value.length();) {
            int next = nextCodepoint(position);
            if (target < (advances[position] + advances[next]) * 0.5f) return position;
            position = next;
        }
        return value.length();
    }

    private int characterAt(double mouseX) {
        double target = mouseX - x + scrollX;
        for (int position = 0; position < value.length();) {
            int next = nextCodepoint(position);
            if (target < advances[next]) return position;
            position = next;
        }
        return previousCodepoint(value.length());
    }

    private void updateDrag(long now) {
        double seconds = Math.clamp((now - dragUpdatedAt) / 1_000_000_000.0, 0.0, 0.05);
        dragUpdatedAt = now;
        double outside = dragMouseX < x ? dragMouseX - x
                : dragMouseX >= x + width ? dragMouseX - (x + width) + 1.0 : 0.0;
        if (outside != 0.0) {
            double speed = Math.copySign(textSize * 6.0 + Math.min(Math.abs(outside), 120.0) * 8.0, outside);
            scrollX = (float) Math.clamp(scrollX + speed * seconds, 0.0, maxScroll());
        }
        double pointerX = Math.clamp(dragMouseX, x, x + viewportWidth());
        int next = cursorAt(pointerX);
        int nextAnchor = anchor;
        if (draggingWords && !value.isEmpty()) {
            int character = characterAt(pointerX);
            if (next < dragWordStart) {
                nextAnchor = dragWordEnd;
                next = runStart(character);
            } else if (next > dragWordEnd) {
                nextAnchor = dragWordStart;
                next = runEnd(character);
            } else {
                nextAnchor = dragWordStart;
                next = dragWordEnd;
            }
        }
        if (cursor != next || anchor != nextAnchor) {
            cursor = next;
            anchor = nextAnchor;
            resetBlink();
        }
        keepCursorVisible();
    }

    private boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void stopDragging() {
        dragging = false;
        draggingWords = false;
    }

    private void resetBlink() { blinkStartedAt = System.nanoTime(); }

    private static boolean allowedCodepoint(int codepoint) {
        if (!Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)
                || codepoint >= Character.MIN_SURROGATE && codepoint <= Character.MAX_SURROGATE) return false;
        int type = Character.getType(codepoint);
        return type != Character.LINE_SEPARATOR && type != Character.PARAGRAPH_SEPARATOR;
    }

    private static String cleanText(String text, int limit) {
        StringBuilder result = new StringBuilder(Math.min(text.length(), Math.max(0, limit)));
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            int length = Character.charCount(codepoint);
            offset += length;
            if (!allowedCodepoint(codepoint)) continue;
            if (length > limit - result.length()) break;
            result.appendCodePoint(codepoint);
        }
        return result.toString();
    }

    private static int boundaryAtOrBefore(String text, int position) {
        if (position > 0 && position < text.length() && Character.isHighSurrogate(text.charAt(position - 1))
                && Character.isLowSurrogate(text.charAt(position))) return position - 1;
        return position;
    }

    private static void fill(GuiGraphics graphics, float left, float top, float right, float bottom, int argb) {
        if (right <= left || bottom <= top) return;
        // Preserve fractional font advances instead of rounding the caret to a glyph.
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(left, top);
            graphics.pose().scale(right - left, bottom - top);
            graphics.fill(0, 0, 1, 1, argb);
        } finally {
            graphics.pose().popMatrix();
        }
    }
}
