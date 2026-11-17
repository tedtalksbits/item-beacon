package dev.whizzle.itembeacon.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    static final Protocol.Beacon B = new Protocol.Beacon("minecraft:overworld", 10, -40, 99);

    @Test void smallSummaryIsOnePage() {
        var items = List.of(new Protocol.SummaryItem(0, new ItemKey(new byte[] {1}), 5_000_000_000L, 3, false),
            new Protocol.SummaryItem(1, new ItemKey(new byte[] {2, 2}), 7, 1, true));
        List<byte[]> pages = Protocol.summary(42, B, items);
        assertEquals(1, pages.size());
        var p = Protocol.readSummary(pages.get(0));
        assertEquals(42, p.session());
        assertEquals(1, p.pages());
        assertEquals(B, p.beacon());
        assertEquals(items, p.items());
    }

    @Test void emptySummaryIsStillOnePage() {
        List<byte[]> pages = Protocol.summary(1, B, List.of());
        assertEquals(1, pages.size());
        assertTrue(Protocol.readSummary(pages.get(0)).items().isEmpty());
    }

    @Test void bigSummarySplitsIntoPagesUnderTheLimitAndReassembles() {
        List<Protocol.SummaryItem> items = new ArrayList<>();
        for (int i = 0; i < 3000; i++) items.add(new Protocol.SummaryItem(i, new ItemKey(new byte[400 + i % 300]), i, 1, false));
        List<byte[]> pages = Protocol.summary(7, B, items);
        assertTrue(pages.size() >= 4, "pages: " + pages.size());
        List<Protocol.SummaryItem> back = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            assertTrue(pages.get(i).length < 1024 * 1024 - 1024, "page " + i + " is " + pages.get(i).length + " bytes");
            var p = Protocol.readSummary(pages.get(i));
            assertEquals(i, p.page());
            assertEquals(pages.size(), p.pages());
            back.addAll(p.items());
        }
        assertEquals(items, back);
    }

    @Test void placesRoundTrip() {
        var places = List.of(new Protocol.Place("b:x:1:2:3", "chest", "minecraft:overworld", true, 1, -2, 3, 64, true, 123456789L, "; Red Shulker Box"),
            new Protocol.Place("ender", "ender_chest", "minecraft:overworld", false, 0, 0, 0, 1, true, 0, ""));
        var read = Protocol.readPlaces(Protocol.places(3, 9, places));
        assertEquals(3, read.session());
        assertEquals(9, read.item());
        assertEquals(places, read.places());
    }

    @Test void smallMessagesRoundTrip() {
        assertEquals(Protocol.VERSION, Protocol.readHello(Protocol.hello()));
        assertEquals(new Protocol.Welcome(Protocol.VERSION, 64, 600), Protocol.readWelcome(Protocol.welcome(64, 600)));
        assertEquals(new Protocol.Pick(5, 77), Protocol.readPick(Protocol.pick(5, 77)));
        assertEquals("e:abc", Protocol.readOpened(Protocol.opened("e:abc")));
        assertEquals(new Protocol.Show(4, 2, true, false), Protocol.readShow(Protocol.show(4, 2, true, false)));
        Protocol.readClear(Protocol.clear());
        assertThrows(IllegalArgumentException.class, () -> Protocol.readClear(new byte[] {0}));
        var q = List.of(new Protocol.ChunkPos(-3, 4), new Protocol.ChunkPos(100, -100));
        assertEquals(q, Protocol.readQuery(Protocol.query(q)));
        BlockSet s = new BlockSet();
        s.add(BlockSet.pack(1, -64, 15));
        s.add(BlockSet.pack(15, 300, 0));
        var c = Protocol.readChunk(Protocol.chunk(new Protocol.ChunkPos(2, -2), s));
        assertEquals(s, c.beacons());
        assertEquals(new Protocol.ChunkPos(2, -2), c.pos());
        assertThrows(IllegalArgumentException.class, () -> Protocol.readPick(new byte[] {1}));
    }

    @Test void blockSetSaveLoadAndPacking() {
        BlockSet s = new BlockSet();
        int p = BlockSet.pack(7, -61, 12);
        s.add(p);
        assertEquals(7, BlockSet.x(p));
        assertEquals(-61, BlockSet.y(p));
        assertEquals(12, BlockSet.z(p));
        assertEquals(s, BlockSet.load(s.save()));
    }

    @Test void chunkKeyMatchesBukkit() {
        var c = new Protocol.ChunkPos(-5, 9);
        assertEquals(((long) 9 << 32) | (-5 & 0xFFFFFFFFL), c.key());
        assertEquals(c, Protocol.ChunkPos.of(c.key()));
    }
}
