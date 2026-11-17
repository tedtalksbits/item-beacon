package dev.whizzle.itembeacon.model;

import java.util.List;
import java.util.UUID;

/**
 * One container a player owns, as last read: where it is, what kind it is and what was in it. {@code id} is
 * {@code b:<world uuid>:<x>:<y>:<z>} for a block, {@code e:<entity uuid>} for an entity (a minecart, a chest boat,
 * an item frame), whose position is where it was last seen. {@code dimension} is the world's key
 * ({@code minecraft:the_nether}). {@code lastSeen} is when it was read (epoch milliseconds).
 */
public record Entry(String id, UUID world, String dimension, int x, int y, int z, String kind, List<Stack> stacks, long lastSeen) {
    public Entry {
        stacks = List.copyOf(stacks);
    }

    public static String blockId(UUID world, int x, int y, int z) { return "b:" + world + ":" + x + ":" + y + ":" + z; }
    public static String entityId(UUID entity) { return "e:" + entity; }

    public boolean isEntity() { return id.startsWith("e:"); }

    /** How many of {@code item} this container holds, nested ones included. */
    public int count(ItemKey item) {
        int n = 0;
        for (Stack s : stacks) if (s.item().equals(item)) n += s.count();
        return n;
    }

    public long chunkKey() { return ((long) (z >> 4) << 32) | ((x >> 4) & 0xFFFFFFFFL); }   // Bukkit's Chunk.getChunkKey
}
