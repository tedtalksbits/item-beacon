package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.BlockSet;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Which lodestones are Item Beacons: a record in each chunk's persistent data ({@code itembeacon:beacons}), loaded
 * chunks kept in memory and every change written straight back (Glowing Glass's {@code Records}, for one kind of
 * block). A record counts only while its block is a lodestone (or being moved by a piston); stale ones are dropped by
 * {@link #verify} or when the chunk next loads.
 */
public final class Beacons implements Listener {
    public static final NamespacedKey KEY = new NamespacedKey("itembeacon", "beacons");

    public record ChunkId(UUID world, long key) {
        static ChunkId of(Chunk c) { return new ChunkId(c.getWorld().getUID(), c.getChunkKey()); }
        public int x() { return (int) key; }
        public int z() { return (int) (key >> 32); }
    }

    private final Map<ChunkId, BlockSet> chunks = new HashMap<>();
    private final Logger log;
    private final List<Consumer<ChunkId>> listeners = new ArrayList<>();
    private Set<ChunkId> batched;

    public Beacons(Logger log) { this.log = log; }

    public void onChange(Consumer<ChunkId> listener) { listeners.add(listener); }

    private void changed(ChunkId id) { for (Consumer<ChunkId> l : List.copyOf(listeners)) l.accept(id); }

    static boolean holds(Block b) {
        Material t = b.getType();
        return t == BeaconItem.CARRIER || t == Material.MOVING_PISTON;
    }

    static int pack(Block b) { return BlockSet.pack(b.getX() & 15, b.getY(), b.getZ() & 15); }

    /** Whether {@code b} is an Item Beacon (a stale record reads as no). */
    public boolean is(Block b) {
        BlockSet s = set(b);
        return s != null && s.contains(pack(b)) && holds(b);
    }

    /** Whether there's a record at {@code b} whatever the block is now. */
    public boolean recorded(Block b) {
        BlockSet s = set(b);
        return s != null && s.contains(pack(b));
    }

    /** The beacons of a loaded chunk, or null if it isn't loaded. */
    public BlockSet chunk(World w, int cx, int cz) {
        if (!w.isChunkLoaded(cx, cz)) return null;
        return chunks.computeIfAbsent(new ChunkId(w.getUID(), Chunk.getChunkKey(cx, cz)), id -> read(w.getChunkAt(cx, cz)));
    }

    private BlockSet set(Block b) { return chunk(b.getWorld(), b.getX() >> 4, b.getZ() >> 4); }

    public int count() { int n = 0; for (BlockSet s : chunks.values()) n += s.size(); return n; }

    /** Runs {@code changes}, announcing each changed chunk once at the end (pistons, explosions). */
    public void batch(Runnable changes) {
        if (batched != null) { changes.run(); return; }
        batched = new LinkedHashSet<>();
        try { changes.run(); }
        finally {
            Set<ChunkId> done = batched;
            batched = null;
            for (ChunkId id : done) changed(id);
        }
    }

    public void add(Block b) {
        BlockSet s = set(b);
        if (s != null && s.add(pack(b))) write(b);
    }

    /** Forgets the record at {@code b}; returns whether there was one. */
    public boolean remove(Block b) {
        BlockSet s = set(b);
        if (s == null || !s.remove(pack(b))) return false;
        write(b);
        return true;
    }

    /** Drops the record at {@code b} if the block is no longer a lodestone. */
    public void verify(Block b) { if (recorded(b) && !holds(b)) remove(b); }

    private void write(Block b) {
        Chunk chunk = b.getChunk();
        ChunkId id = ChunkId.of(chunk);
        save(chunk, chunks.get(id));
        if (batched != null) batched.add(id);
        else changed(id);
    }

    private BlockSet read(Chunk chunk) {
        byte[] data = chunk.getPersistentDataContainer().get(KEY, PersistentDataType.BYTE_ARRAY);
        if (data == null) return new BlockSet();
        BlockSet s;
        try { s = BlockSet.load(data); }
        catch (IllegalArgumentException e) {
            log.warning("Unreadable Item Beacon record in chunk " + chunk.getX() + "," + chunk.getZ() + " of " + chunk.getWorld().getName() + " (" + e.getMessage() + "); ignored.");
            return new BlockSet();
        }
        int stale = 0;
        for (int p : List.copyOf(s.positions())) {
            if (!holds(chunk.getBlock(BlockSet.x(p), BlockSet.y(p), BlockSet.z(p)))) { s.remove(p); stale++; }
        }
        if (stale > 0) {
            log.info("Forgot " + stale + " Item Beacon record(s) in chunk " + chunk.getX() + "," + chunk.getZ() + " of " + chunk.getWorld().getName() + ": those blocks were replaced.");
            save(chunk, s);
        }
        return s;
    }

    private static void save(Chunk chunk, BlockSet s) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (s == null || s.isEmpty()) pdc.remove(KEY);
        else pdc.set(KEY, PersistentDataType.BYTE_ARRAY, s.save());
    }

    public void loadAll() {
        for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) chunks.computeIfAbsent(ChunkId.of(c), id -> read(c));
    }

    public void saveAll() {
        for (World w : Bukkit.getWorlds()) for (Chunk c : w.getLoadedChunks()) {
            BlockSet s = chunks.get(ChunkId.of(c));
            if (s != null) save(c, s);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(ChunkLoadEvent e) { chunks.computeIfAbsent(ChunkId.of(e.getChunk()), id -> read(e.getChunk())); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        BlockSet s = chunks.remove(ChunkId.of(e.getChunk()));
        if (s != null) save(e.getChunk(), s);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent e) {
        UUID w = e.getWorld().getUID();
        chunks.keySet().removeIf(id -> id.world().equals(w));
    }
}
