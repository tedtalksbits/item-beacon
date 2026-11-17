package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.BlockSet;
import dev.whizzle.itembeacon.model.Protocol;
import dev.whizzle.itembeacon.model.Protocol.ChunkPos;
import dev.whizzle.itembeacon.model.Protocol.Place;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.*;

/**
 * Server half of the client-mod protocol (docs/PROTOCOL.md). Two jobs:
 * <ul>
 * <li>Beacon positions, for the mod's model swap (Glowing Glass's shape): once a player's mod has said hello, every
 * chunk sent to that player is followed by the chunk's beacons; the mod can ask about chunks it had before; a chunk
 * whose beacons change is pushed again.</li>
 * <li>Searching: opening a beacon sends the summary (in pages); a {@code pick} gets the places holding that item
 * (and sets the player's waypoints); opening a waypoint's container sends {@code opened}.</li>
 * </ul>
 */
public final class ClientBridge implements PluginMessageListener, Listener {
    public static final List<String> INCOMING = Protocol.SERVERBOUND.stream().map(Protocol::channel).toList();
    public static final List<String> OUTGOING = Protocol.CLIENTBOUND.stream().map(Protocol::channel).toList();
    static final String HELLO = Protocol.channel(Protocol.HELLO), QUERY = Protocol.channel(Protocol.QUERY), PICK = Protocol.channel(Protocol.PICK),
        WELCOME = Protocol.channel(Protocol.WELCOME), CHUNK = Protocol.channel(Protocol.CHUNK), SUMMARY = Protocol.channel(Protocol.SUMMARY),
        PLACES = Protocol.channel(Protocol.PLACES), OPENED = Protocol.channel(Protocol.OPENED);
    static final int MESSAGES_PER_SECOND = 40;
    static final int ANSWERS_PER_TICK = 64;
    static final int MAX_QUEUED = 8192;

    public interface Outbox { void send(Player player, String channel, byte[] data); }

    /** The world as the bridge sees it; tests substitute it. */
    public interface View {
        boolean sent(Player player, ChunkPos chunk);
        /** The chunk's beacons, or null if the chunk isn't loaded. */
        BlockSet beacons(World world, ChunkPos chunk);
    }

    static final class Watch {
        final UUID world;
        final Set<Long> watched = new HashSet<>();
        final LinkedHashSet<Long> queued = new LinkedHashSet<>();
        long second; int messages;
        Watch(UUID world) { this.world = world; }
    }

    private final Outbox outbox;
    private final View view;
    private final Search search;
    private final Waypoints waypoints;
    private final Map<UUID, Watch> watches = new HashMap<>();
    /** Players whose mod speaks our protocol version. */
    private final Set<UUID> modded = new HashSet<>();
    java.util.function.LongSupplier clock = () -> System.currentTimeMillis() / 1000;

    public ClientBridge(Outbox outbox, View view, Search search, Waypoints waypoints) {
        this.outbox = outbox;
        this.view = view;
        this.search = search;
        this.waypoints = waypoints;
    }

    public static View real(Beacons beacons) {
        return new View() {
            @Override public boolean sent(Player player, ChunkPos chunk) { return player.isChunkSent(chunk.key()); }
            @Override public BlockSet beacons(World world, ChunkPos chunk) { return beacons.chunk(world, chunk.x(), chunk.z()); }
        };
    }

    public boolean modded(Player p) { return modded.contains(p.getUniqueId()); }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        Watch w = watch(player);
        long now = clock.getAsLong();
        if (w.second != now) { w.second = now; w.messages = 0; }
        if (++w.messages > MESSAGES_PER_SECOND) return;
        try {
            if (channel.equals(HELLO)) {
                int version = Protocol.readHello(message);
                w.watched.clear();
                w.queued.clear();
                if (version == Protocol.VERSION) modded.add(player.getUniqueId()); else modded.remove(player.getUniqueId());
                outbox.send(player, WELCOME, Protocol.welcome(search.radius(), (int) (waypoints.lifetimeMs() / 1000)));
            } else if (channel.equals(QUERY)) {
                for (ChunkPos c : Protocol.readQuery(message)) if (w.queued.size() < MAX_QUEUED) w.queued.add(c.key());
            } else if (channel.equals(PICK)) {
                pick(player, Protocol.readPick(message));
            }
        } catch (IllegalArgumentException ignored) {
            // A malformed message from a modified client: ignore it.
        }
    }

    // ---- searching ----

    /** Sends the session's summary, in pages. */
    public void summary(Player p, Search.Session s) {
        var b = s.beacon();
        Protocol.Beacon beacon = new Protocol.Beacon(b.getWorld().getKey().asString(), b.getX(), b.getY(), b.getZ());
        for (byte[] page : Protocol.summary(s.id(), beacon, search.summary(s))) outbox.send(p, SUMMARY, page);
    }

    void pick(Player p, Protocol.Pick pick) {
        Search.Session s = search.session(p.getUniqueId());
        if (s == null || s.id() != pick.session() || !search.usable(p, s)) return;   // not at the beacon any more
        List<Place> places = search.places(s, pick.item());
        waypoints.show(p, places, false);
        outbox.send(p, PLACES, Protocol.places(s.id(), pick.item(), places));
    }

    /** A waypoint's container was opened: the mod clears it. */
    public void opened(Player p, String id) { if (modded(p)) outbox.send(p, OPENED, Protocol.opened(id)); }

    // ---- beacon positions ----

    private Watch watch(Player p) {
        Watch w = watches.get(p.getUniqueId());
        if (w == null || !w.world.equals(p.getWorld().getUID())) watches.put(p.getUniqueId(), w = new Watch(p.getWorld().getUID()));
        return w;
    }

    public void changed(Beacons.ChunkId id) {
        ChunkPos pos = ChunkPos.of(id.key());
        for (Map.Entry<UUID, Watch> e : watches.entrySet()) {
            Watch w = e.getValue();
            if (!w.world.equals(id.world()) || !w.watched.contains(id.key())) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null || !p.getWorld().getUID().equals(id.world())) continue;
            BlockSet set = view.beacons(p.getWorld(), pos);
            if (set != null) outbox.send(p, CHUNK, Protocol.chunk(pos, set));
        }
    }

    public void tick() {
        for (Iterator<Map.Entry<UUID, Watch>> it = watches.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Watch> e = it.next();
            Player p = Bukkit.getPlayer(e.getKey());
            Watch w = e.getValue();
            if (p == null || !w.world.equals(p.getWorld().getUID())) { it.remove(); continue; }
            answer(p, w);
        }
    }

    @EventHandler
    public void onChunkSent(PlayerChunkLoadEvent e) { sent(e.getPlayer(), new ChunkPos(e.getChunk().getX(), e.getChunk().getZ())); }

    void sent(Player p, ChunkPos pos) {
        Watch w = watches.get(p.getUniqueId());
        if (w == null || !modded(p) || !w.world.equals(p.getWorld().getUID())) return;
        BlockSet set = view.beacons(p.getWorld(), pos);
        if (set == null) return;
        w.queued.remove(pos.key());
        w.watched.add(pos.key());
        outbox.send(p, CHUNK, Protocol.chunk(pos, set));
    }

    void answer(Player p, Watch w) {
        int n = 0;
        for (Iterator<Long> it = w.queued.iterator(); it.hasNext() && n < ANSWERS_PER_TICK; ) {
            long key = it.next();
            it.remove();
            ChunkPos pos = ChunkPos.of(key);
            if (!view.sent(p, pos)) continue;
            BlockSet set = view.beacons(p.getWorld(), pos);
            if (set == null) continue;
            w.watched.add(key);
            outbox.send(p, CHUNK, Protocol.chunk(pos, set));
            n++;
        }
    }

    @EventHandler
    public void onChunkUnsent(PlayerChunkUnloadEvent e) {
        Watch w = watches.get(e.getPlayer().getUniqueId());
        if (w == null) return;
        long key = e.getChunk().getChunkKey();
        w.watched.remove(key);
        w.queued.remove(key);
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { watches.remove(e.getPlayer().getUniqueId()); modded.remove(e.getPlayer().getUniqueId()); }

    /** A new world: the beacon watch starts over (the mod stays modded). */
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) {
        Watch w = watches.remove(e.getPlayer().getUniqueId());
        if (w != null) watches.put(e.getPlayer().getUniqueId(), new Watch(e.getPlayer().getWorld().getUID()));
    }
}
