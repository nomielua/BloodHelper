package ru.bloodhelper.client.hud;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventorySnapshotTest {
    @Test void countsMainStacksAndOffhandOnceIncludingSelectedHotbarStack() {
        var counts = InventorySnapshot.countOwned(List.of(
                new InventorySnapshot.CountedItem("minecraft:ender_pearl", 16),
                new InventorySnapshot.CountedItem("minecraft:ender_pearl", 8),
                new InventorySnapshot.CountedItem("minecraft:golden_apple", 12)),
                new InventorySnapshot.CountedItem("minecraft:ender_pearl", 3));
        assertEquals(Map.of("minecraft:ender_pearl", 27, "minecraft:golden_apple", 12), counts);
    }

    @Test void emptyHandsDoNotCreateSyntheticCountsAndTotalsCannotOverflow() {
        assertEquals(Map.of(), InventorySnapshot.countOwned(List.of(), null));
        assertEquals(Map.of("example:item", Integer.MAX_VALUE), InventorySnapshot.countOwned(List.of(
                new InventorySnapshot.CountedItem("example:item", Integer.MAX_VALUE),
                new InventorySnapshot.CountedItem("example:item", 64),
                new InventorySnapshot.CountedItem("minecraft:air", 0)), null));
    }

    @Test void brokenAndPartiallyDamagedArmorUseExactLowThreshold() {
        var exact = InventorySnapshot.durability(true, false, 100, 80);
        assertEquals(20, exact.remaining());
        assertEquals(20, exact.percent());
        assertTrue(exact.low(20));
        assertFalse(exact.low(19));
        assertEquals("20%", exact.display("PERCENT"));
        assertEquals("20", exact.display("POINTS"));
        var barelyRemaining = InventorySnapshot.durability(true, false, 529, 528);
        assertEquals(1, barelyRemaining.percent());
        assertEquals(0, InventorySnapshot.durability(true, false, 529, 900).remaining());
        assertEquals(529, InventorySnapshot.durability(true, false, 529, -100).remaining());
    }

    @Test void unbreakableAndNonDamageableItemsNeverWarnOrPretendToHaveDurability() {
        var unbreakable = InventorySnapshot.durability(true, true, 100, 99);
        assertFalse(unbreakable.damageable());
        assertFalse(unbreakable.low(90));
        assertEquals(-1, unbreakable.percent());
        assertEquals("∞", unbreakable.display("POINTS"));
        var cosmetic = InventorySnapshot.durability(false, false, 0, 0);
        assertFalse(cosmetic.low(90));
        assertEquals("—", cosmetic.display("PERCENT"));
    }
}
