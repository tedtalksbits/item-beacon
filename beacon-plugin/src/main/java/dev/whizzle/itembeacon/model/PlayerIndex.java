package dev.whizzle.itembeacon.model;

import java.util.*;
import java.util.function.Predicate;

/**
 * Everything one player owns: their containers ({@link Entry}, by id) with each one's last-read contents. Saved as
 * one small file per player. Item bytes are written once per file (a dictionary), so a thousand chests of cobblestone
 * cost one cobblestone.
 */
public final class PlayerIndex {
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    public Entry get(String id) { return entries.get(id); }
    public Entry put(Entry e) { return entries.put(e.id(), e); }
    public Entry remove(String id) { return entries.remove(id); }
    public boolean contains(String id) { return entries.containsKey(id); }
    public Collection<Entry> entries() { return Collections.unmodifiableCollection(entries.values()); }
    public int size() { return entries.size(); }
    public boolean isEmpty() { return entries.isEmpty(); }

    /** Total of one exact item across the containers, and in how many. */
    public record Total(ItemKey item, long count, int containers) {}

    /** Every item the player owns (in the containers {@code include} accepts), with totals, most first. */
    public List<Total> totals(Predicate<Entry> include) {
        Map<ItemKey, long[]> sums = new LinkedHashMap<>();
        for (Entry e : entries.values()) {
            if (!include.test(e)) continue;
            Set<ItemKey> seen = new HashSet<>();
            for (Stack s : e.stacks()) {
                long[] t = sums.computeIfAbsent(s.item(), k -> new long[2]);
                t[0] += s.count();
                if (seen.add(s.item())) t[1]++;
            }
        }
        List<Total> out = new ArrayList<>(sums.size());
        for (var t : sums.entrySet()) out.add(new Total(t.getKey(), t.getValue()[0], (int) t.getValue()[1]));
        out.sort(Comparator.comparingLong(Total::count).reversed());
        return out;
    }

    /** The containers holding {@code item}. */
    public List<Entry> holding(ItemKey item) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries.values()) if (e.count(item) > 0) out.add(e);
        return out;
    }

    // ---- saved form ----
    // VarInt format (1); VarInt k, k x item bytes (VarInt length + bytes); VarInt n, n x entry:
    // UTF id, Int32 x2 world uuid (most, least as VarLong), UTF dimension, Int32 x, y, z, UTF kind, VarLong lastSeen,
    // VarInt s, s x (VarInt item index, VarInt count, UTF inside).

    static final int MAX_STRING = 1024;

    public byte[] save() {
        Map<ItemKey, Integer> dict = new LinkedHashMap<>();
        for (Entry e : entries.values()) for (Stack s : e.stacks()) dict.putIfAbsent(s.item(), dict.size());
        Wire.Writer out = new Wire.Writer().varInt(1).varInt(dict.size());
        for (ItemKey k : dict.keySet()) k.write(out);
        out.varInt(entries.size());
        for (Entry e : entries.values()) {
            out.utf(e.id()).varLong(e.world().getMostSignificantBits()).varLong(e.world().getLeastSignificantBits())
                .utf(e.dimension()).int32(e.x()).int32(e.y()).int32(e.z()).utf(e.kind()).varLong(e.lastSeen()).varInt(e.stacks().size());
            for (Stack s : e.stacks()) out.varInt(dict.get(s.item())).varInt(s.count()).utf(s.inside());
        }
        return out.bytes();
    }

    public static PlayerIndex load(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        int format = in.varInt();
        if (format != 1) throw new IllegalArgumentException("Unknown format " + format);
        int k = in.varInt();
        if (k < 0 || k > 1_000_000) throw new IllegalArgumentException("Bad item count " + k);
        List<ItemKey> dict = new ArrayList<>(k);
        for (int i = 0; i < k; i++) dict.add(ItemKey.read(in));
        int n = in.varInt();
        if (n < 0 || n > 1_000_000) throw new IllegalArgumentException("Bad entry count " + n);
        PlayerIndex index = new PlayerIndex();
        for (int i = 0; i < n; i++) {
            String id = in.utf(MAX_STRING);
            UUID world = new UUID(in.varLong(), in.varLong());
            String dimension = in.utf(MAX_STRING);
            int x = in.int32(), y = in.int32(), z = in.int32();
            String kind = in.utf(MAX_STRING);
            long lastSeen = in.varLong();
            int s = in.varInt();
            if (s < 0 || s > 100_000) throw new IllegalArgumentException("Bad stack count " + s);
            List<Stack> stacks = new ArrayList<>(s);
            for (int j = 0; j < s; j++) {
                int item = in.varInt();
                if (item < 0 || item >= dict.size()) throw new IllegalArgumentException("Bad item index " + item);
                stacks.add(new Stack(dict.get(item), in.varInt(), in.utf(MAX_STRING)));
            }
            index.put(new Entry(id, world, dimension, x, y, z, kind, stacks, lastSeen));
        }
        in.end();
        return index;
    }

    @Override public boolean equals(Object o) { return o instanceof PlayerIndex p && p.entries.equals(entries); }
    @Override public int hashCode() { return entries.hashCode(); }
}
