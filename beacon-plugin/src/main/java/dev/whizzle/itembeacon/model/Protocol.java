package dev.whizzle.itembeacon.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The client-mod protocol (docs/PROTOCOL.md): channel names and every message's bytes, shared by the plugin and the
 * mod so both encode and decode with the same code. Bump {@link #VERSION} when any message's bytes change.
 */
public final class Protocol {
    private Protocol() {}

    public static final int VERSION = 1;
    public static final String NAMESPACE = "itembeacon";
    public static final String HELLO = "hello", WELCOME = "welcome", QUERY = "query", CHUNK = "chunk",
        SUMMARY = "summary", PICK = "pick", PLACES = "places", SHOW = "show", CLEAR = "clear", OPENED = "opened",
        SORT = "sort";
    public static final List<String> SERVERBOUND = List.of(HELLO, QUERY, PICK, SHOW, CLEAR, SORT);
    public static final List<String> CLIENTBOUND = List.of(WELCOME, CHUNK, SUMMARY, PLACES, OPENED);
    /** Most chunks in one query. */
    public static final int MAX_QUERY = 64;
    /**
     * Most bytes of item entries in one summary page. Minecraft refuses a clientbound custom payload over 1 MiB; a big
     * storage room can exceed that, so the summary is split into pages well under it.
     */
    public static final int PAGE_BYTES = 256 * 1024;
    /** Most places in one answer (the nearest ones); far under the payload limit. */
    public static final int MAX_PLACES = 4000;
    static final int MAX_STRING = 1024;

    public static String channel(String name) { return NAMESPACE + ":" + name; }

    // ---- hello (C->S): VarInt protocol ----
    public static byte[] hello() { return new Wire.Writer().varInt(VERSION).bytes(); }
    public static int readHello(byte[] data) { Wire.Reader in = new Wire.Reader(data); int v = in.varInt(); in.end(); return v; }

    // ---- welcome (S->C): VarInt protocol, VarInt radius (blocks), VarInt waypoint lifetime (seconds) ----
    public record Welcome(int version, int radius, int waypointSeconds) {}
    public static byte[] welcome(int radius, int waypointSeconds) { return new Wire.Writer().varInt(VERSION).varInt(radius).varInt(waypointSeconds).bytes(); }
    public static Welcome readWelcome(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        Welcome w = new Welcome(in.varInt(), in.varInt(), in.varInt());
        in.end();
        return w;
    }

    public record ChunkPos(int x, int z) {
        public long key() { return ((long) z << 32) | (x & 0xFFFFFFFFL); }   // Bukkit's Chunk.getChunkKey
        public static ChunkPos of(long key) { return new ChunkPos((int) key, (int) (key >> 32)); }
    }

    // ---- query (C->S): VarInt n (1-64), n x (Int32 chunk x, Int32 chunk z). Which of these chunks' lodestones are beacons? ----
    public static byte[] query(List<ChunkPos> chunks) {
        if (chunks.isEmpty() || chunks.size() > MAX_QUERY) throw new IllegalArgumentException("1-" + MAX_QUERY + " chunks per query");
        Wire.Writer out = new Wire.Writer().varInt(chunks.size());
        for (ChunkPos c : chunks) out.int32(c.x()).int32(c.z());
        return out.bytes();
    }
    public static List<ChunkPos> readQuery(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        int n = in.varInt();
        if (n < 1 || n > MAX_QUERY) throw new IllegalArgumentException("Bad query size " + n);
        List<ChunkPos> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(new ChunkPos(in.int32(), in.int32()));
        in.end();
        return out;
    }

    // ---- chunk (S->C): Int32 chunk x, Int32 chunk z, VarInt n, n x Int32 packed position (BlockSet.pack) ----
    // The chunk's Item Beacons (for the mod's model swap). Sent right behind each chunk, answered for queries, and
    // pushed whole whenever a beacon in it is placed, broken or moved.
    public record Chunk(ChunkPos pos, BlockSet beacons) {}
    public static byte[] chunk(ChunkPos pos, BlockSet beacons) {
        Wire.Writer out = new Wire.Writer().int32(pos.x()).int32(pos.z()).varInt(beacons.size());
        for (int p : beacons.positions()) out.int32(p);
        return out.bytes();
    }
    public static Chunk readChunk(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        ChunkPos pos = new ChunkPos(in.int32(), in.int32());
        int n = in.varInt();
        if (n < 0 || n > BlockSet.MAX_ENTRIES) throw new IllegalArgumentException("Bad entry count " + n);
        BlockSet set = new BlockSet();
        for (int i = 0; i < n; i++) set.add(in.int32());
        in.end();
        return new Chunk(pos, set);
    }

    // ---- summary (S->C), sent when the player opens a beacon, in one or more pages ----
    // VarInt session, VarInt page (0-based), VarInt pages, UTF beacon dimension, Int32 beacon x, y, z,
    // VarInt n, n x (VarInt item id, item bytes (VarInt length + bytes), VarLong total, VarInt containers,
    // Bool only last seen (no container holding it was read just now)).

    /** One item in the summary. {@code id} is the item's number in this session (what {@code pick} sends back). */
    public record SummaryItem(int id, ItemKey item, long total, int containers, boolean onlyLastSeen) {}
    public record Beacon(String dimension, int x, int y, int z) {}
    public record SummaryPage(int session, int page, int pages, Beacon beacon, List<SummaryItem> items) {}

    /** The summary as pages, each under {@link #PAGE_BYTES} of items (at least one page, even with no items). */
    public static List<byte[]> summary(int session, Beacon beacon, List<SummaryItem> items) {
        List<List<SummaryItem>> pages = new ArrayList<>();
        List<SummaryItem> current = new ArrayList<>();
        int bytes = 0;
        for (SummaryItem i : items) {
            int size = i.item().length() + 24;
            if (!current.isEmpty() && bytes + size > PAGE_BYTES) { pages.add(current); current = new ArrayList<>(); bytes = 0; }
            current.add(i);
            bytes += size;
        }
        pages.add(current);
        List<byte[]> out = new ArrayList<>(pages.size());
        for (int p = 0; p < pages.size(); p++) {
            Wire.Writer w = new Wire.Writer().varInt(session).varInt(p).varInt(pages.size())
                .utf(beacon.dimension()).int32(beacon.x()).int32(beacon.y()).int32(beacon.z()).varInt(pages.get(p).size());
            for (SummaryItem i : pages.get(p)) {
                w.varInt(i.id());
                i.item().write(w);
                w.varLong(i.total()).varInt(i.containers()).bool(i.onlyLastSeen());
            }
            out.add(w.bytes());
        }
        return out;
    }

    public static SummaryPage readSummary(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        int session = in.varInt(), page = in.varInt(), pages = in.varInt();
        Beacon beacon = new Beacon(in.utf(MAX_STRING), in.int32(), in.int32(), in.int32());
        int n = in.varInt();
        if (n < 0 || n > 1_000_000) throw new IllegalArgumentException("Bad item count " + n);
        List<SummaryItem> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) items.add(new SummaryItem(in.varInt(), ItemKey.read(in), in.varLong(), in.varInt(), in.bool()));
        in.end();
        if (page < 0 || page >= pages) throw new IllegalArgumentException("Bad page " + page + " of " + pages);
        return new SummaryPage(session, page, pages, beacon, items);
    }

    // ---- pick (C->S): VarInt session, VarInt item id ----
    public record Pick(int session, int item) {}
    public static byte[] pick(int session, int item) { return new Wire.Writer().varInt(session).varInt(item).bytes(); }
    public static Pick readPick(byte[] data) { Wire.Reader in = new Wire.Reader(data); Pick p = new Pick(in.varInt(), in.varInt()); in.end(); return p; }

    // ---- places (S->C): the containers holding the picked item ----
    // VarInt session, VarInt item id, VarInt n, n x (UTF id, UTF kind, UTF dimension, Bool same dimension as the
    // beacon, Int32 x, y, z, VarInt count, Bool live, VarLong last seen (epoch ms), UTF inside).

    /**
     * A container holding the picked item. {@code id} is the container's id ({@link Entry#id}), or {@code ender} for
     * the player's ender chest (the position is then the nearest ender chest in the beacon's dimension, and
     * {@code sameDimension} is false if there is none). {@code live}: read just now, else {@code lastSeen}.
     * {@code inside}: where the items sit, "; "-separated ("" = loose in the container, "Shulker Box", ...).
     */
    public record Place(String id, String kind, String dimension, boolean sameDimension, int x, int y, int z, int count,
                        boolean live, long lastSeen, String inside) {}
    public record Places(int session, int item, List<Place> places) {}

    public static byte[] places(int session, int item, List<Place> places) {
        if (places.size() > MAX_PLACES) throw new IllegalArgumentException("At most " + MAX_PLACES + " places");
        Wire.Writer w = new Wire.Writer().varInt(session).varInt(item).varInt(places.size());
        for (Place p : places)
            w.utf(p.id()).utf(p.kind()).utf(p.dimension()).bool(p.sameDimension()).int32(p.x()).int32(p.y()).int32(p.z())
                .varInt(p.count()).bool(p.live()).varLong(p.lastSeen()).utf(p.inside());
        return w.bytes();
    }

    public static Places readPlaces(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        int session = in.varInt(), item = in.varInt(), n = in.varInt();
        if (n < 0 || n > MAX_PLACES) throw new IllegalArgumentException("Bad place count " + n);
        List<Place> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            out.add(new Place(in.utf(MAX_STRING), in.utf(MAX_STRING), in.utf(MAX_STRING), in.bool(), in.int32(), in.int32(), in.int32(),
                in.varInt(), in.bool(), in.varLong(), in.utf(MAX_STRING * 8)));
        in.end();
        return new Places(session, item, out);
    }

    // ---- show (C->S): VarInt session, VarInt item id, Bool nearest only, Bool outlines ----
    // Sets the player's waypoints to the places of that item (all, or the nearest only), replacing any before. With
    // outlines, the server also draws its glowing outlines (for a mod that doesn't draw waypoints itself yet).
    public record Show(int session, int item, boolean nearestOnly, boolean outlines) {}
    public static byte[] show(int session, int item, boolean nearestOnly, boolean outlines) {
        return new Wire.Writer().varInt(session).varInt(item).bool(nearestOnly).bool(outlines).bytes();
    }
    public static Show readShow(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        Show s = new Show(in.varInt(), in.varInt(), in.bool(), in.bool());
        in.end();
        return s;
    }

    // ---- clear (C->S): empty. Clears the player's waypoints (by hand). ----
    public static byte[] clear() { return new byte[0]; }
    public static void readClear(byte[] data) { new Wire.Reader(data).end(); }

    // ---- sort (C->S): Bool player inventory, VarInt n, n x VarInt slot ----
    // Rearranges the open container (player false: the top inventory, slots 0..n-1, n = its size) or the player's
    // main inventory (player true: slots 9..35, n = 27). The slots are the new order: the items now in those slots,
    // taken in that order, are merged into full stacks and laid out from the first slot, empty slots last. The order
    // is the client's (it knows the creative menu order and the names as the player reads them).
    public static final int MAX_SORT = 54;
    public record Sort(boolean player, List<Integer> order) {}
    public static byte[] sort(boolean player, List<Integer> order) {
        if (order.isEmpty() || order.size() > MAX_SORT) throw new IllegalArgumentException("1-" + MAX_SORT + " slots");
        Wire.Writer w = new Wire.Writer().bool(player).varInt(order.size());
        for (int i : order) w.varInt(i);
        return w.bytes();
    }
    public static Sort readSort(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        boolean player = in.bool();
        int n = in.varInt();
        if (n < 1 || n > MAX_SORT) throw new IllegalArgumentException("Bad sort size " + n);
        List<Integer> order = new ArrayList<>(n);
        for (int i = 0; i < n; i++) order.add(in.varInt());
        in.end();
        return new Sort(player, order);
    }

    // ---- opened (S->C): UTF container id. The player opened a container they had a waypoint to: clear it. ----
    public static byte[] opened(String id) { return new Wire.Writer().utf(id).bytes(); }
    public static String readOpened(byte[] data) { Wire.Reader in = new Wire.Reader(data); String s = in.utf(MAX_STRING); in.end(); return s; }
}
