package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.Entry;
import dev.whizzle.itembeacon.model.ItemKey;
import dev.whizzle.itembeacon.model.PlayerIndex;
import dev.whizzle.itembeacon.model.PlayerIndex.Total;
import dev.whizzle.itembeacon.model.Protocol.Place;
import dev.whizzle.itembeacon.model.Protocol.SummaryItem;
import dev.whizzle.itembeacon.model.Stack;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Searching at a beacon. Opening one rereads, live, every container of the player's within the radius around it (in
 * loaded chunks; all heights) and their ender chest; everything else they own is listed as last seen. The radius
 * only decides what's read live: all their containers are listed. A session lasts while the player stays at the
 * beacon; picking an item gives the containers holding it, nearest first, other dimensions last.
 */
public final class Search {
    /** How far from the beacon's centre the player may be to keep using it (a bit more than reach). */
    public static final double USE_DISTANCE = 8;

    public record Session(int id, UUID player, Block beacon, List<Total> items, Set<String> live) {
        public Location centre() { return beacon.getLocation().add(0.5, 0.5, 0.5); }
    }

    private final Index index;
    private final Beacons beacons;
    private final int radius;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private int nextId = 1;

    public Search(Index index, Beacons beacons, int radius) {
        this.index = index;
        this.beacons = beacons;
        this.radius = radius;
    }

    public int radius() { return radius; }

    public Session session(UUID player) { return sessions.get(player); }

    public void end(UUID player) { sessions.remove(player); }

    /** Opens the beacon for the player: rereads what's in range, then lists everything they own. */
    public Session open(Player p, Block beacon) {
        Set<String> live = readNear(p.getUniqueId(), beacon);
        if (index.refreshEnder(p) != null) live.add(Index.ENDER);
        PlayerIndex own = index.of(p.getUniqueId());
        Session s = new Session(nextId++, p.getUniqueId(), beacon, own.totals(e -> true), Set.copyOf(live));
        sessions.put(p.getUniqueId(), s);
        return s;
    }

    /** Rereads the player's containers within the radius of {@code beacon}; returns the ids read (still theirs). */
    public Set<String> readNear(UUID player, Block beacon) {
        Set<String> live = new HashSet<>();
        World w = beacon.getWorld();
        for (Entry e : List.copyOf(index.of(player).entries())) {
            if (e.id().equals(Index.ENDER) || !e.world().equals(w.getUID())) continue;
            if (e.isEntity()) {
                Entity en = Bukkit.getEntity(Objects.requireNonNull(Index.entity(e.id())));
                if (en == null || !en.getWorld().equals(w) || !inRange(beacon, en.getLocation().getBlockX(), en.getLocation().getBlockZ())) continue;
                Entry now = index.refresh(en);
                if (now != null && index.holders(e.id()).contains(player)) live.add(e.id());
            } else {
                if (!inRange(beacon, e.x(), e.z()) || !w.isChunkLoaded(e.x() >> 4, e.z() >> 4)) continue;
                Entry now = index.refresh(w.getBlockAt(e.x(), e.y(), e.z()));
                if (now != null && index.holders(e.id()).contains(player)) live.add(e.id());
            }
        }
        return live;
    }

    public boolean inRange(Block beacon, int x, int z) {
        long dx = x - beacon.getX(), dz = z - beacon.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    /** Whether the player can still use the session's beacon (same world, close, the beacon still there). */
    public boolean usable(Player p, Session s) {
        return s != null && p.getUniqueId().equals(s.player()) && p.getWorld().equals(s.beacon().getWorld())
            && p.getLocation().distanceSquared(s.centre()) <= USE_DISTANCE * USE_DISTANCE && beacons.is(s.beacon());
    }

    /** The session's items as the summary lists them; {@code onlyLastSeen}: no container read just now holds it. */
    public List<SummaryItem> summary(Session s) {
        PlayerIndex own = index.of(s.player());
        Set<ItemKey> seenLive = new HashSet<>();
        for (String id : s.live()) {
            Entry e = own.get(id);
            if (e != null) for (Stack st : e.stacks()) seenLive.add(st.item());
        }
        List<SummaryItem> out = new ArrayList<>(s.items().size());
        for (int i = 0; i < s.items().size(); i++) {
            Total t = s.items().get(i);
            out.add(new SummaryItem(i, t.item(), t.count(), t.containers(), !seenLive.contains(t.item())));
        }
        return out;
    }

    /** The containers holding item number {@code item} of the session, nearest first, other dimensions last. */
    public List<Place> places(Session s, int item) {
        if (item < 0 || item >= s.items().size()) return List.of();
        ItemKey key = s.items().get(item).item();
        Block beacon = s.beacon();
        UUID world = beacon.getWorld().getUID();
        List<Place> out = new ArrayList<>();
        for (Entry e : index.of(s.player()).holding(key)) {
            String inside = insides(e, key);
            if (e.id().equals(Index.ENDER)) {
                Block chest = index.nearestEnderChest(s.centre());
                out.add(new Place(Index.ENDER, e.kind(), beacon.getWorld().getKey().asString(), chest != null,
                    chest == null ? 0 : chest.getX(), chest == null ? 0 : chest.getY(), chest == null ? 0 : chest.getZ(),
                    e.count(key), s.live().contains(Index.ENDER), e.lastSeen(), inside));
                continue;
            }
            out.add(new Place(e.id(), e.kind(), e.dimension(), e.world().equals(world), e.x(), e.y(), e.z(), e.count(key),
                s.live().contains(e.id()), e.lastSeen(), inside));
        }
        out.sort(Comparator.comparing((Place p) -> !p.sameDimension()).thenComparingLong(p -> distanceSquared(beacon, p)));
        return out.size() > dev.whizzle.itembeacon.model.Protocol.MAX_PLACES ? out.subList(0, dev.whizzle.itembeacon.model.Protocol.MAX_PLACES) : out;
    }

    static long distanceSquared(Block b, Place p) {
        long dx = p.x() - b.getX(), dy = p.y() - b.getY(), dz = p.z() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    /** Where the item sits in the container: distinct "inside" labels joined by "; " ("" = loose in it). */
    static String insides(Entry e, ItemKey key) {
        LinkedHashSet<String> in = new LinkedHashSet<>();
        for (Stack st : e.stacks()) if (st.item().equals(key)) in.add(st.inside());
        return String.join("; ", in);
    }
}
