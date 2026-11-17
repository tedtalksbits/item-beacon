package dev.whizzle.itembeacon.client;

import dev.whizzle.itembeacon.model.BlockSet;
import dev.whizzle.itembeacon.model.Protocol.ChunkPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which lodestones the server said are Item Beacons, by chunk, for the current level. Read from chunk-meshing
 * threads ({@link #at}), written on the client thread: each chunk's set is replaced whole. Pure Java.
 */
public final class BeaconCache {
    private final Map<Long, Set<Integer>> chunks = new ConcurrentHashMap<>();

    public boolean at(int x, int y, int z) {
        Set<Integer> c = chunks.get(new ChunkPos(x >> 4, z >> 4).key());
        return c != null && c.contains(BlockSet.pack(x & 15, y, z & 15));
    }

    /** Replaces a chunk's beacons; returns the section y indices (block y >> 4) that need meshing again. */
    public Set<Integer> update(ChunkPos pos, BlockSet beacons) {
        Set<Integer> now = Set.copyOf(beacons.positions());
        Set<Integer> before = now.isEmpty() ? chunks.remove(pos.key()) : chunks.put(pos.key(), now);
        if (before == null) before = Set.of();
        Set<Integer> sections = new TreeSet<>();
        for (int p : now) if (!before.contains(p)) sections.add(BlockSet.y(p) >> 4);
        for (int p : before) if (!now.contains(p)) sections.add(BlockSet.y(p) >> 4);
        return sections;
    }

    public void forget(ChunkPos pos) { chunks.remove(pos.key()); }

    public void clear() { chunks.clear(); }

    public int size() { int n = 0; for (var c : chunks.values()) n += c.size(); return n; }
}
