package dev.whizzle.itembeacon.model;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Positions inside one chunk (the Item Beacons in it). Packed as {@code (y << 8) | (z << 4) | x} with x and z 0-15
 * and y the world height (may be negative), as Glowing Glass does. Saved in the chunk's persistent data.
 */
public final class BlockSet {
    /** More than a chunk can hold; guards against corrupt data. */
    public static final int MAX_ENTRIES = 16 * 16 * 4064;
    private final TreeSet<Integer> positions = new TreeSet<>();

    public static int pack(int localX, int y, int localZ) { return (y << 8) | ((localZ & 15) << 4) | (localX & 15); }
    public static int x(int packed) { return packed & 15; }
    public static int z(int packed) { return (packed >> 4) & 15; }
    public static int y(int packed) { return packed >> 8; }

    public boolean contains(int packed) { return positions.contains(packed); }
    public boolean add(int packed) { return positions.add(packed); }
    public boolean remove(int packed) { return positions.remove(packed); }
    public boolean isEmpty() { return positions.isEmpty(); }
    public int size() { return positions.size(); }
    public Set<Integer> positions() { return Collections.unmodifiableSet(positions); }

    /** Saved form: VarInt format (1), VarInt n, n x Int32 packed position. */
    public byte[] save() {
        Wire.Writer out = new Wire.Writer().varInt(1).varInt(positions.size());
        for (int p : positions) out.int32(p);
        return out.bytes();
    }

    public static BlockSet load(byte[] data) {
        Wire.Reader in = new Wire.Reader(data);
        int format = in.varInt();
        if (format != 1) throw new IllegalArgumentException("Unknown format " + format);
        int n = in.varInt();
        if (n < 0 || n > MAX_ENTRIES) throw new IllegalArgumentException("Bad entry count " + n);
        BlockSet s = new BlockSet();
        for (int i = 0; i < n; i++) s.positions.add(in.int32());
        in.end();
        return s;
    }

    @Override public boolean equals(Object o) { return o instanceof BlockSet b && b.positions.equals(positions); }
    @Override public int hashCode() { return positions.hashCode(); }
    @Override public String toString() { return positions.toString(); }
}
