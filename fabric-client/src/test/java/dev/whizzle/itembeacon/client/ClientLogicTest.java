package dev.whizzle.itembeacon.client;

import dev.whizzle.itembeacon.model.BlockSet;
import dev.whizzle.itembeacon.model.ItemKey;
import dev.whizzle.itembeacon.model.Protocol;
import dev.whizzle.itembeacon.model.Protocol.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The mod's pure-Java parts: the beacon cache, summary pages, text, waypoints. */
class ClientLogicTest {
    @Test void beaconCacheMarksAndReportsChangedSections() {
        BeaconCache c = new BeaconCache();
        BlockSet s = new BlockSet();
        s.add(BlockSet.pack(3, 70, 5));
        assertEquals(Set.of(4), c.update(new ChunkPos(-1, 2), s));
        assertTrue(c.at(-16 + 3, 70, 32 + 5));
        assertFalse(c.at(-16 + 3, 71, 32 + 5));
        assertEquals(Set.of(4), c.update(new ChunkPos(-1, 2), new BlockSet()));
        assertFalse(c.at(-13, 70, 37));
        assertEquals(0, c.size());
    }

    static Protocol.SummaryItem item(int id) { return new Protocol.SummaryItem(id, new ItemKey(new byte[300 + id % 50]), id, 1, false); }

    @Test void summaryPagesAssembleInAnyOrderAndANewSessionStartsOver() {
        List<Protocol.SummaryItem> items = new ArrayList<>();
        for (int i = 0; i < 2500; i++) items.add(item(i));
        var beacon = new Protocol.Beacon("minecraft:overworld", 1, 2, 3);
        List<byte[]> pages = Protocol.summary(9, beacon, items);
        assertTrue(pages.size() > 1);
        SummaryAssembler a = new SummaryAssembler();
        assertNull(a.accept(Protocol.readSummary(Protocol.summary(8, beacon, items).get(0))));   // an older session, unfinished
        for (int i = pages.size() - 1; i > 0; i--) assertNull(a.accept(Protocol.readSummary(pages.get(i))));
        SummaryAssembler.Summary s = a.accept(Protocol.readSummary(pages.get(0)));
        assertNotNull(s);
        assertEquals(9, s.session());
        assertEquals(items, s.items());
        assertEquals(beacon, s.beacon());
    }

    @Test void emptySummaryCompletesAtOnce() {
        var s = new SummaryAssembler().accept(Protocol.readSummary(Protocol.summary(1, new Protocol.Beacon("d", 0, 0, 0), List.of()).get(0)));
        assertNotNull(s);
        assertTrue(s.items().isEmpty());
    }

    @Test void countsFitUnderAnIcon() {
        assertEquals("412", Format.count(412));
        assertEquals("1.5k", Format.count(1500));
        assertEquals("2k", Format.count(2000));
        assertEquals("12k", Format.count(12_345));
        assertEquals("1.2M", Format.count(1_234_567));
        assertEquals("15M", Format.count(15_000_000));
        for (long n : new long[] {999, 9_999, 99_999, 999_999, 9_999_999, 99_999_999}) assertTrue(Format.count(n).length() <= 4, Format.count(n));
    }

    @Test void text() {
        assertEquals("Chest minecart", Format.kind("chest_minecart"));
        assertEquals("in the Nether", Format.dimension("minecraft:the_nether"));
        assertEquals(List.of("Red Shulker Box"), Format.insides("; Red Shulker Box"));
        assertEquals(List.of(), Format.insides(""));
        assertEquals("5 min ago", Format.ago(5 * 60_000 + 10));
        assertEquals("2 h ago", Format.ago(2 * 3_600_000));
        assertEquals("12 m", Format.distance(12.4));
        assertEquals("1.5 km", Format.distance(1500));
    }

    static SortPlan.Slot full(int index, int type, String name, int group, int count) { return new SortPlan.Slot(index, false, type, name, group, count); }
    static SortPlan.Slot none(int index) { return new SortPlan.Slot(index, true, 0, "", -1, 0); }

    @Test void sortOrders() {
        // slot: (index, creative order, name, group, count)
        var cobble = full(0, 5, "Cobblestone", 0, 10);
        var apple = full(1, 900, "Apple", 1, 3);
        var empty = none(2);
        var cobble2 = full(3, 5, "Cobblestone", 0, 64);
        var named = full(4, 5, "Base stone", 2, 64);    // renamed cobblestone: same type, other group
        var diamond = full(5, 300, "Diamond", 3, 1);
        var unknown = full(6, Integer.MAX_VALUE, "Mod thing", 4, 1);
        var slots = List.of(cobble, apple, empty, cobble2, named, diamond, unknown);
        assertEquals(List.of(4, 3, 0, 5, 1, 6, 2), SortPlan.order(slots, SortPlan.Mode.TYPE));
        assertEquals(List.of(1, 4, 3, 0, 5, 6, 2), SortPlan.order(slots, SortPlan.Mode.NAME));
        assertEquals(List.of(3, 0, 4, 1, 5, 6, 2), SortPlan.order(slots, SortPlan.Mode.COUNT));   // 74 cobble, 64 named, 3 apples, then type
        for (var mode : SortPlan.Mode.values()) assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6), Set.copyOf(SortPlan.order(slots, mode)));
    }

    @Test void sortKeepsEmptySlotsLastAndTiesInPlace() {
        var a = full(7, 1, "A", 0, 5);
        var b = full(2, 1, "A", 0, 5);
        assertEquals(List.of(2, 7, 0, 9), SortPlan.order(List.of(none(0), a, b, none(9)), SortPlan.Mode.TYPE));
    }

    /** Mirrors the plugin's BeaconKeeper.onUse: only sneaking with an item (not a compass) places it. */
    @Test void useRuleMatchesThePlugin() {
        assertTrue(UseRule.beaconUse(false, false, false));   // a block in hand: open, don't place
        assertTrue(UseRule.beaconUse(false, true, false));
        assertTrue(UseRule.beaconUse(true, false, true));     // sneaking with a compass: never bind
        assertTrue(UseRule.beaconUse(true, true, false));
        assertFalse(UseRule.beaconUse(true, false, false));   // sneaking with a block: place it
    }

    @Test void waypointsClearOnOpenTimeOrHand() {
        long[] now = {1000};
        Waypoints.clock = () -> now[0];
        var here = new Protocol.Place("b:1", "chest", "minecraft:overworld", true, 0, 0, 0, 1, true, 0, "");
        var there = new Protocol.Place("b:2", "chest", "minecraft:the_nether", false, 0, 0, 0, 1, true, 0, "");
        var other = new Protocol.Place("b:3", "barrel", "minecraft:overworld", true, 0, 0, 0, 1, true, 0, "");
        Waypoints.show(List.of(here, there, other), 60_000);
        assertEquals(Set.of("b:1", "b:3"), Waypoints.ids());   // no waypoint into another dimension
        Waypoints.opened("b:1");
        assertEquals(Set.of("b:3"), Waypoints.ids());
        now[0] += 60_000;
        Waypoints.tick();
        assertTrue(Waypoints.ids().isEmpty());
        Waypoints.show(List.of(here), 60_000);
        Waypoints.clear();
        assertTrue(Waypoints.ids().isEmpty());
    }
}
