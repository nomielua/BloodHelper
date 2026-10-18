package ru.bloodhelper.client.hud;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FrameHistoryTest {
    @Test void ringIsBoundedOrderedAndMeasuredInMilliseconds() {
        var history = new FrameHistory(3);
        history.frame(0, true);
        history.frame(10_000_000, true);
        history.frame(30_000_000, true);
        history.frame(60_000_000, true);
        history.frame(100_000_000, true);
        assertArrayEquals(new double[]{20, 30, 40}, history.samples());
        assertEquals(30, history.mean());
        var copy = history.samples(); copy[0] = 999;
        assertEquals(20, history.samples()[0]);
    }
    @Test void menusLongGapsAndClockReversalDoNotProduceFrameSpikes() {
        var history = new FrameHistory(90);
        history.frame(0, true); history.frame(8_000_000, true);
        history.frame(10_000_000, false);
        assertEquals(0, history.samples().length);
        assertTrue(Double.isNaN(history.mean()));
        history.frame(5_000_000_000L, true);
        assertEquals(0, history.samples().length);
        history.frame(5_010_000_000L, true);
        assertEquals(10, history.mean());
        history.frame(8_000_000_000L, true);
        assertEquals(0, history.samples().length);
        history.frame(1, true);
        assertEquals(0, history.samples().length);
    }
}
