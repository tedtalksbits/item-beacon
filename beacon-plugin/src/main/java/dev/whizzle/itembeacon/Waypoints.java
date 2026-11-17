package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.Protocol.Place;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * A player's waypoints: the containers of their last pick. They clear when the player opens that container, after
 * the lifetime (10 minutes by default), or by hand. With the mod, the mod draws them and the server only says which
 * one was opened; without it, each nearby container gets a glowing outline (a glowing block display only that player
 * sees; glowing shows through walls).
 */
public final class Waypoints {
    /** Outlines only for containers this close (in loaded chunks), and at most this many. */
    static final int OUTLINE_DISTANCE = 160, MAX_OUTLINES = 64;

    record Active(String id, long expires, BlockDisplay outline) {}

    private final Plugin plugin;
    private final long lifetimeMs;
    /** Told when a waypoint was cleared by opening its container (the bridge tells the mod). */
    private final BiConsumer<Player, String> cleared;
    private final Map<UUID, Map<String, Active>> active = new HashMap<>();
    java.util.function.LongSupplier clock = System::currentTimeMillis;

    public Waypoints(Plugin plugin, long lifetimeMs, BiConsumer<Player, String> cleared) {
        this.plugin = plugin;
        this.lifetimeMs = lifetimeMs;
        this.cleared = cleared;
    }

    public long lifetimeMs() { return lifetimeMs; }

    public Set<String> of(Player p) { Map<String, Active> m = active.get(p.getUniqueId()); return m == null ? Set.of() : Set.copyOf(m.keySet()); }

    public int outlines(Player p) {
        Map<String, Active> m = active.get(p.getUniqueId());
        return m == null ? 0 : (int) m.values().stream().filter(a -> a.outline() != null).count();
    }

    /** Replaces the player's waypoints with {@code places}; {@code outlines}: draw glowing outlines (no mod). */
    public void show(Player p, List<Place> places, boolean outlines) {
        clear(p);
        Map<String, Active> m = new LinkedHashMap<>();
        long expires = clock.getAsLong() + lifetimeMs;
        int drawn = 0;
        for (Place pl : places) {
            if (!pl.sameDimension()) continue;   // no waypoint into another dimension: the list says where
            BlockDisplay outline = null;
            if (outlines && drawn < MAX_OUTLINES) { outline = outline(p, pl); if (outline != null) drawn++; }
            m.put(pl.id(), new Active(pl.id(), expires, outline));
        }
        if (!m.isEmpty()) active.put(p.getUniqueId(), m);
    }

    /** The player opened container {@code id}: its waypoint goes. */
    public void opened(Player p, String id) {
        Map<String, Active> m = active.get(p.getUniqueId());
        Active a = m == null ? null : m.remove(id);
        if (a == null) return;
        remove(a);
        if (m.isEmpty()) active.remove(p.getUniqueId());
        cleared.accept(p, id);
    }

    public void clear(Player p) {
        Map<String, Active> m = active.remove(p.getUniqueId());
        if (m != null) for (Active a : m.values()) remove(a);
    }

    public void clearAll() {
        for (Map<String, Active> m : active.values()) for (Active a : m.values()) remove(a);
        active.clear();
    }

    /** Called every second: expired waypoints go, and those of players who left. */
    public void tick() {
        long now = clock.getAsLong();
        for (Iterator<Map.Entry<UUID, Map<String, Active>>> it = active.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            boolean online = Bukkit.getPlayer(e.getKey()) != null;
            for (Iterator<Active> a = e.getValue().values().iterator(); a.hasNext(); ) {
                Active w = a.next();
                if (!online || w.expires() <= now) { remove(w); a.remove(); }
            }
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    private static void remove(Active a) { if (a.outline() != null && a.outline().isValid()) a.outline().remove(); }

    private BlockDisplay outline(Player p, Place pl) {
        World w = p.getWorld();
        if (!w.getKey().asString().equals(pl.dimension()) && !pl.id().equals(Index.ENDER)) return null;
        if (!w.isChunkLoaded(pl.x() >> 4, pl.z() >> 4)) return null;
        Location at = new Location(w, pl.x(), pl.y(), pl.z());
        if (at.distanceSquared(p.getLocation()) > (double) OUTLINE_DISTANCE * OUTLINE_DISTANCE) return null;
        BlockData data;
        float scale;
        if (pl.id().startsWith("e:")) {
            Entity en = Bukkit.getEntity(Objects.requireNonNull(Index.entity(pl.id())));
            if (en == null) return null;
            at = en.getLocation().toBlockLocation();
            data = Material.GLASS.createBlockData();
            scale = 0.6f;
        } else {
            Block b = at.getBlock();
            data = b.getBlockData();
            scale = 1.002f;
        }
        float offset = (1 - scale) / 2;
        BlockData shown = data;
        BlockDisplay d = w.spawn(at, BlockDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setBlock(shown);
            e.setGlowing(true);
            e.setGlowColorOverride(Color.AQUA);
            e.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            e.setTransformation(new Transformation(new Vector3f(offset, offset, offset), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
        });
        p.showEntity(plugin, d);
        return d;
    }
}
