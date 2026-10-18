package ru.bloodhelper.client.hud;

import java.util.Arrays;

/** A bounded, monotonic frame interval history; suspended rendering never becomes a spike. */
final class FrameHistory {
    private final double[] values;
    private int cursor, size;
    private long previous = -1;
    FrameHistory(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        values = new double[capacity];
    }
    void frame(long now, boolean active) {
        if (!active) { reset(); return; }
        if (previous >= 0) {
            long elapsed = now - previous;
            if (elapsed <= 0 || elapsed > 1_000_000_000L) { reset(); }
            else { values[cursor] = elapsed / 1_000_000.0; cursor = (cursor + 1) % values.length; size = Math.min(size + 1, values.length); }
        }
        previous = now;
    }
    void reset() { cursor = size = 0; previous = -1; Arrays.fill(values, 0); }
    double[] samples() {
        double[] result = new double[size];
        for (int i = 0; i < size; i++) result[i] = values[(cursor - size + i + values.length) % values.length];
        return result;
    }
    double mean() {
        if (size == 0) return Double.NaN;
        double sum = 0;
        for (int i = 0; i < size; i++) sum += values[(cursor - 1 - i + values.length) % values.length];
        return sum / size;
    }
}
