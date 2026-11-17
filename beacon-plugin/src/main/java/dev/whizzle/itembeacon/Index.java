package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.Entry;
import dev.whizzle.itembeacon.model.PlayerIndex;
import dev.whizzle.itembeacon.model.Stack;
import dev.whizzle.itembeacon.model.Wire;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Every player's containers and their last-read contents ({@link PlayerIndex}, one file per player in
 * {@code plugins/ItemBeacon/players/}), plus the positions of every ender chest (any placer: ender chests open each
 * player's own inventory). Reading a container ({@link #refresh(Block)}) updates the entry of each of its owners and
 * forgets it for anyone who no longer owns it; a container that's gone (broken, blown up, replaced) is forgotten.
 * The player's ender chest contents are an entry with id {@value #ENDER}.
 */
public final class Index {
    public static final String ENDER = "ender";

    record Pos(UUID world, int x, int y, int z) {
        static Pos of(Block b) { return new Pos(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }
        long chunkKey() { return Chunk.getChunkKey(x >> 4, z >> 4); }
    }

    private final Path folder;
    private final Logger log;
    private final Map<UUID, PlayerIndex> players = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();
    /** Container id -> the players whose index holds it. */
    private final Map<String, Set<UUID>> holders = new HashMap<>();
    /** Block container ids by world and chunk, to reread a chunk's containers when it loads or unloads. */
    private final Map<UUID, Map<Long, Set<String>>> byChunk = new HashMap<>();
    private final Set<Pos> enderChests = new HashSet<>();
    private boolean endersDirty;
    /** The current time (tests fix it). */
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    public Index(Path folder, Logger log) {
        this.folder = folder;
        this.log = log;
    }

    // ---- reading ----

    /** The player's index (empty if they own nothing). Read only: change it through this class. */
    public PlayerIndex of(UUID player) { return players.getOrDefault(player, new PlayerIndex()); }

    public Set<UUID> holders(String id) { return holders.getOrDefault(id, Set.of()); }

    public int containers() { return holders.size(); }

    /** The ids of the block containers indexed in a chunk. */
    public Set<String> inChunk(UUID world, long chunkKey) {
        Map<Long, Set<String>> w = byChunk.get(world);
        Set<String> s = w == null ? null : w.get(chunkKey);
        return s == null ? Set.of() : Set.copyOf(s);
    }

    // ---- changing ----

    private PlayerIndex index(UUID player) { return players.computeIfAbsent(player, p -> new PlayerIndex()); }

    void put(UUID player, Entry e) {
        Entry before = index(player).put(e);
        holders.computeIfAbsent(e.id(), x -> new HashSet<>()).add(player);
        if (!e.isEntity() && !e.id().equals(ENDER)) {
            if (before != null && before.chunkKey() != e.chunkKey()) unchunk(before);
            byChunk.computeIfAbsent(e.world(), w -> new HashMap<>()).computeIfAbsent(e.chunkKey(), k -> new HashSet<>()).add(e.id());
        }
        dirty.add(player);
    }

    /** Forgets container {@code id} for {@code player}. */
    void remove(UUID player, String id) {
        PlayerIndex index = players.get(player);
        Entry e = index == null ? null : index.remove(id);
        if (e == null) return;
        dirty.add(player);
        Set<UUID> h = holders.get(id);
        if (h != null) { h.remove(player); if (h.isEmpty()) { holders.remove(id); unchunk(e); } }
    }

    private void unchunk(Entry e) {
        if (e.isEntity()) return;
        Map<Long, Set<String>> w = byChunk.get(e.world());
        Set<String> s = w == null ? null : w.get(e.chunkKey());
        if (s != null) { s.remove(e.id()); if (s.isEmpty()) w.remove(e.chunkKey()); }
    }

    /** Forgets container {@code id} for everyone (it's gone). */
    public void forget(String id) { for (UUID p : List.copyOf(holders(id))) remove(p, id); }

    /**
     * Reads a block container now: each owner's entry gets its contents; anyone who no longer owns it forgets it;
     * if it's no longer a container (or nobody owns it), everyone forgets it. Returns the entry (null if forgotten).
     */
    public Entry refresh(Block b) {
        String id = Containers.id(b);
        BlockState state = b.getState(false);
        Set<UUID> owners = Containers.isContainer(state) ? Containers.owners(b) : Set.of();
        for (UUID p : List.copyOf(holders(id))) if (!owners.contains(p)) remove(p, id);
        if (owners.isEmpty()) return null;
        Entry e = Containers.entry(b, state, clock.getAsLong());
        for (UUID p : owners) put(p, e);
        return e;
    }

    /** {@link #refresh(Block)} for both halves of a double chest (owners of one half see the other). */
    public void refreshWithOtherHalf(Block b) {
        refresh(b);
        Block other = Containers.otherHalf(b);
        if (other != null) refresh(other);
    }

    /** Reads an entity container now (a minecart, a chest boat, an item frame). */
    public Entry refresh(Entity e) {
        String id = Containers.id(e);
        UUID owner = Containers.isContainer(e) ? Containers.owner(e) : null;
        for (UUID p : List.copyOf(holders(id))) if (!p.equals(owner)) remove(p, id);
        if (owner == null) return null;
        Entry entry = Containers.entry(e, clock.getAsLong());
        put(owner, entry);
        return entry;
    }

    /** Reads the player's ender chest contents. */
    public Entry refreshEnder(Player p) {
        List<Stack> stacks = Containers.stacks(p.getEnderChest().getContents());
        Location l = p.getLocation();
        if (stacks.isEmpty()) { remove(p.getUniqueId(), ENDER); return null; }
        Entry e = new Entry(ENDER, p.getWorld().getUID(), p.getWorld().getKey().asString(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
            "ender_chest", stacks, clock.getAsLong());
        put(p.getUniqueId(), e);
        return e;
    }

    /**
     * A chunk loaded or is about to unload: rereads its indexed block containers (a container replaced while nobody
     * watched is forgotten here) and indexes any owned container that isn't yet.
     */
    public void rereadChunk(Chunk c) {
        Set<String> known = new HashSet<>(inChunk(c.getWorld().getUID(), c.getChunkKey()));
        for (BlockState s : c.getTileEntities(b -> true, false)) {
            if (s.getType() == Material.ENDER_CHEST) { addEnderChest(s.getBlock()); continue; }
            if (!Containers.isContainer(s)) continue;
            Block b = s.getBlock();
            String id = Containers.id(b);
            if (known.remove(id) || Containers.owner(b) != null || Containers.otherHalf(b) != null) refresh(b);
        }
        // Indexed but no block entity there any more.
        for (String id : known) {
            Block b = block(id);
            if (b == null) forget(id); else refresh(b);
        }
    }

    /** The block of a block id, or null if its world is gone or the id isn't a block id. */
    public static Block block(String id) {
        if (!id.startsWith("b:")) return null;
        String[] p = id.split(":");
        if (p.length != 5) return null;
        try {
            World w = Bukkit.getWorld(UUID.fromString(p[1]));
            return w == null ? null : w.getBlockAt(Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4]));
        } catch (IllegalArgumentException e) { return null; }
    }

    public static UUID entity(String id) {
        if (!id.startsWith("e:")) return null;
        try { return UUID.fromString(id.substring(2)); } catch (IllegalArgumentException e) { return null; }
    }

    // ---- ender chests ----

    public void addEnderChest(Block b) { if (enderChests.add(Pos.of(b))) endersDirty = true; }

    public void removeEnderChest(Block b) { if (enderChests.remove(Pos.of(b))) endersDirty = true; }

    public int enderChestCount() { return enderChests.size(); }

    /** The nearest ender chest to {@code from} in its world (one in an unloaded chunk counts as still there). */
    public Block nearestEnderChest(Location from) {
        UUID w = from.getWorld().getUID();
        Pos best = null;
        double bestD = Double.MAX_VALUE;
        for (Pos p : List.copyOf(enderChests)) {
            if (!p.world().equals(w)) continue;
            Block b = from.getWorld().getBlockAt(p.x(), p.y(), p.z());
            if (from.getWorld().isChunkLoaded(p.x() >> 4, p.z() >> 4) && b.getType() != Material.ENDER_CHEST) { removeEnderChest(b); continue; }
            double d = from.distanceSquared(b.getLocation().add(0.5, 0.5, 0.5));
            if (d < bestD) { bestD = d; best = p; }
        }
        return best == null ? null : from.getWorld().getBlockAt(best.x(), best.y(), best.z());
    }

    // ---- files ----

    public void loadAll() {
        Path dir = folder.resolve("players");
        if (Files.isDirectory(dir)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.bin")) {
                for (Path f : files) {
                    String name = f.getFileName().toString();
                    UUID player;
                    try { player = UUID.fromString(name.substring(0, name.length() - 4)); } catch (IllegalArgumentException e) { continue; }
                    try {
                        PlayerIndex index = PlayerIndex.load(read(f));
                        for (Entry e : index.entries()) put(player, e);
                    } catch (IOException | IllegalArgumentException e) {
                        log.warning("Couldn't read " + f + " (" + e.getMessage() + "); that player's list starts empty.");
                    }
                }
            } catch (IOException e) { log.warning("Couldn't list " + dir + ": " + e.getMessage()); }
        }
        dirty.clear();
        Path enders = folder.resolve("enderchests.bin");
        if (Files.exists(enders)) {
            try {
                Wire.Reader in = new Wire.Reader(read(enders));
                if (in.varInt() != 1) throw new IllegalArgumentException("unknown format");
                int n = in.varInt();
                for (int i = 0; i < n; i++) enderChests.add(new Pos(new UUID(in.varLong(), in.varLong()), in.int32(), in.int32(), in.int32()));
                in.end();
            } catch (IOException | IllegalArgumentException e) { log.warning("Couldn't read " + enders + " (" + e.getMessage() + ")."); }
        }
        endersDirty = false;
    }

    /** Writes the files that changed. */
    public void save() {
        for (UUID p : List.copyOf(dirty)) {
            Path f = folder.resolve("players").resolve(p + ".bin");
            try {
                PlayerIndex index = players.get(p);
                if (index == null || index.isEmpty()) { Files.deleteIfExists(f); players.remove(p); }
                else write(f, index.save());
                dirty.remove(p);
            } catch (IOException e) { log.warning("Couldn't save " + f + ": " + e.getMessage()); }
        }
        if (endersDirty) {
            Wire.Writer out = new Wire.Writer().varInt(1).varInt(enderChests.size());
            for (Pos p : enderChests) out.varLong(p.world().getMostSignificantBits()).varLong(p.world().getLeastSignificantBits()).int32(p.x()).int32(p.y()).int32(p.z());
            try { write(folder.resolve("enderchests.bin"), out.bytes()); endersDirty = false; }
            catch (IOException e) { log.warning("Couldn't save the ender chest list: " + e.getMessage()); }
        }
    }

    private static byte[] read(Path f) throws IOException {
        try (var in = new GZIPInputStream(Files.newInputStream(f))) { return in.readAllBytes(); }
    }

    private static void write(Path f, byte[] data) throws IOException {
        Files.createDirectories(f.getParent());
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try (var out = new GZIPOutputStream(Files.newOutputStream(tmp))) { out.write(data); }
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
