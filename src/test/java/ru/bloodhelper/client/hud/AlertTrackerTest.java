package ru.bloodhelper.client.hud;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlertTrackerTest {
    @Test void suppressesInitialLowStateAndOnlyRepeatsAfterReplenishment() {
        var alerts = new AlertTracker();
        assertFalse(alerts.observe("totem", 2, 3));
        assertFalse(alerts.observe("totem", 1, 3));
        assertFalse(alerts.observe("totem", 2, 3));
        assertFalse(alerts.observe("totem", 8, 3));
        assertFalse(alerts.observe("totem", 4, 3));
        assertTrue(alerts.observe("totem", 3, 3));
        assertFalse(alerts.observe("totem", 3, 3));
        assertFalse(alerts.observe("totem", 0, 3));
        assertFalse(alerts.observe("totem", 6, 3));
        assertTrue(alerts.observe("totem", 1, 3));
    }

    @Test void detectsEffectsAfterRefreshAndZeroItemThreshold() {
        var alerts = new AlertTracker();
        assertFalse(alerts.observe("effect", 201, 200));
        assertTrue(alerts.observe("effect", 200, 200));
        assertFalse(alerts.observe("effect", 199, 200));
        assertFalse(alerts.observe("effect", 1200, 200));
        assertTrue(alerts.observe("effect", 195, 200));
        assertFalse(alerts.observe("item", 1, 0));
        assertTrue(alerts.observe("item", 0, 0));
    }

    @Test void thresholdChangesAndReappearingKeysDoNotCreateWarnings() {
        var alerts = new AlertTracker();
        assertFalse(alerts.observe("armor", 40, 20));
        assertFalse(alerts.observe("armor", 39, 50));
        assertFalse(alerts.observe("armor", 90, 50));
        assertTrue(alerts.observe("armor", 49, 50));
        alerts.retainObservations(Set.of());
        assertFalse(alerts.observe("armor", 10, 50));
        assertFalse(alerts.observe("armor", Double.NaN, 50));
        assertFalse(alerts.observe("armor", 9, 50));
    }

    @Test void queueDeduplicatesWithoutExtendingDurationAndExpiresOnBoundary() {
        var alerts = new AlertTracker();
        assertTrue(alerts.push("armor", "Armor", "20%", "shield", 1000, 4000, 3));
        assertFalse(alerts.push("armor", "Armor", "18%", "shield", 2000, 4000, 3));
        assertEquals(1, alerts.snapshot(2000).size());
        var card = alerts.snapshot(2000).getFirst();
        assertEquals("18%", card.body());
        assertEquals(1000, card.createdAt());
        assertEquals(5000, card.expiresAt());
        assertEquals(.75f, card.remaining(2000));
        assertEquals(1, alerts.snapshot(4999).size());
        assertTrue(alerts.snapshot(5000).isEmpty());
        assertTrue(alerts.push("armor", "Armor", "10%", "shield", 5000, 4000, 3));
    }

    @Test void keepsNewestCardsWithinTheBoundAndAppliesReducedLimit() {
        var alerts = new AlertTracker();
        for (int i = 0; i < 8; i++) alerts.push("key" + i, "", "", "info", 0, 4000, 3);
        assertEquals(java.util.List.of("key5", "key6", "key7"), alerts.snapshot(0).stream().map(AlertTracker.Notice::key).toList());
        alerts.expire(100, 1);
        assertEquals("key7", alerts.snapshot(100).getFirst().key());
        assertEquals(1, alerts.snapshot(100).size());
        assertThrows(UnsupportedOperationException.class, () -> alerts.snapshot(100).clear());
        alerts.expire(100, 0);
        assertTrue(alerts.snapshot(100).isEmpty());
    }

    @Test void clearingDiscardsBothCardsAndObservationBaseline() {
        var alerts = new AlertTracker();
        alerts.observe("item", 5, 3);
        alerts.push("test", "", "", "info", 0, 4000, 3);
        alerts.clear();
        assertTrue(alerts.snapshot(1).isEmpty());
        assertFalse(alerts.observe("item", 2, 3));
    }
}
