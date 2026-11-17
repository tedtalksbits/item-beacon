package dev.whizzle.itembeacon.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerIndexTest {
    static final UUID W = UUID.randomUUID();
    static final ItemKey IRON = new ItemKey(new byte[] {1, 2, 3}), STONE = new ItemKey(new byte[] {4, 5}), BOX = new ItemKey(new byte[] {9});

    static Entry chest(int x, Stack... stacks) { return new Entry(Entry.blockId(W, x, 64, 0), W, "minecraft:overworld", x, 64, 0, "chest", List.of(stacks), 1000L + x); }

    @Test void totalsSumAcrossContainersAndCountEachContainerOnce() {
        PlayerIndex p = new PlayerIndex();
        p.put(chest(1, new Stack(IRON, 10, ""), new Stack(IRON, 5, "Shulker Box"), new Stack(STONE, 64, "")));
        p.put(chest(2, new Stack(IRON, 3, "")));
        var totals = p.totals(e -> true);
        assertEquals(STONE, totals.get(0).item());   // most first
        var iron = totals.stream().filter(t -> t.item().equals(IRON)).findFirst().orElseThrow();
        assertEquals(18, iron.count());
        assertEquals(2, iron.containers());
        assertEquals(2, p.holding(IRON).size());
        assertEquals(15, p.get(Entry.blockId(W, 1, 64, 0)).count(IRON));
        assertEquals(1, p.totals(e -> e.x() == 2).size());
    }

    @Test void saveLoadRoundTripSharesItemBytes() {
        PlayerIndex p = new PlayerIndex();
        ItemKey big = new ItemKey(new byte[2000]);
        for (int i = 0; i < 100; i++) p.put(chest(i, new Stack(big, i + 1, ""), new Stack(BOX, 1, "Bundle in Red Shulker Box")));
        p.put(new Entry(Entry.entityId(UUID.randomUUID()), W, "minecraft:the_nether", -5, 30, 7, "chest_minecart", List.of(new Stack(STONE, 2, "")), 5));
        byte[] saved = p.save();
        assertEquals(p, PlayerIndex.load(saved));
        assertTrue(saved.length < 2000 + 100 * 200, "item bytes written once: " + saved.length);
    }

    @Test void corruptDataIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> PlayerIndex.load(new byte[] {2}));
        byte[] good = new PlayerIndex().save();
        byte[] trailing = java.util.Arrays.copyOf(good, good.length + 1);
        assertThrows(IllegalArgumentException.class, () -> PlayerIndex.load(trailing));
    }

    @Test void entryChunkKeyMatchesBukkit() {
        Entry e = new Entry("x", W, "d", -17, 0, 33, "chest", List.of(), 0);
        Protocol.ChunkPos c = Protocol.ChunkPos.of(e.chunkKey());
        assertEquals(-2, c.x());
        assertEquals(2, c.z());
    }
}
