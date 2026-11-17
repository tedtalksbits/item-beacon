package dev.whizzle.itembeacon;

import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Item Beacon: stand at a beacon, pick one of your items, get waypoints to every container of yours holding it. */
public class ItemBeaconPlugin extends JavaPlugin implements Listener, TabExecutor {
    private Beacons beacons;
    private Index index;
    private Search search;
    private Waypoints waypoints;
    private ClientBridge bridge;
    private FallbackMenu menu;
    private BeaconKeeper keeper;
    private Tracker tracker;
    private final List<BukkitTask> tasks = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int radius = Math.max(8, Math.min(512, getConfig().getInt("radius", 64)));
        long lifetime = Math.max(1, getConfig().getLong("waypoint-minutes", 10)) * 60_000L;
        beacons = new Beacons(getLogger());
        index = new Index(getDataFolder().toPath(), getLogger());
        search = new Search(index, beacons, radius);
        waypoints = new Waypoints(this, lifetime, (p, id) -> bridge.opened(p, id));
        bridge = new ClientBridge((p, channel, data) -> p.sendPluginMessage(this, channel, data), ClientBridge.real(beacons), search, waypoints);
        menu = new FallbackMenu(search, waypoints);
        keeper = new BeaconKeeper(this, beacons, this::open);
        tracker = new Tracker(this, index, waypoints::opened);
        beacons.onChange(bridge::changed);
        var pm = getServer().getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(beacons, this);
        pm.registerEvents(keeper, this);
        pm.registerEvents(tracker, this);
        pm.registerEvents(new Recipes(), this);
        pm.registerEvents(bridge, this);
        pm.registerEvents(menu, this);
        for (String c : ClientBridge.INCOMING) getServer().getMessenger().registerIncomingPluginChannel(this, c, bridge);
        for (String c : ClientBridge.OUTGOING) getServer().getMessenger().registerOutgoingPluginChannel(this, c);
        index.loadAll();
        beacons.loadAll();
        Recipes.register();
        for (Player p : Bukkit.getOnlinePlayers()) Recipes.discover(p);
        PluginCommand command = getCommand("itembeacon");
        if (command == null) throw new IllegalStateException("itembeacon command missing from plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        tasks.add(getServer().getScheduler().runTaskTimer(this, () -> { keeper.tick(); tracker.tick(); bridge.tick(); }, 1, 1));
        tasks.add(getServer().getScheduler().runTaskTimer(this, waypoints::tick, 20, 20));
        tasks.add(getServer().getScheduler().runTaskTimer(this, index::save, 600, 600));
        if (getConfig().getBoolean("particles", true)) {
            Particles particles = new Particles(beacons);
            tasks.add(getServer().getScheduler().runTaskTimer(this, () -> particles.tick(Bukkit.getWorlds()), 5, 5));
        }
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : tasks) t.cancel();
        if (waypoints != null) waypoints.clearAll();
        if (beacons != null) beacons.saveAll();
        if (index != null) index.save();
        Recipes.unregister();
    }

    public Beacons beacons() { return beacons; }
    public Index index() { return index; }
    public Search search() { return search; }
    public Waypoints waypoints() { return waypoints; }
    public ClientBridge bridge() { return bridge; }
    public Tracker tracker() { return tracker; }

    /** A player right-clicked a beacon: the mod's screen, or the chest menu without the mod. */
    public void open(Player p, Block beacon) {
        Search.Session s = search.open(p, beacon);
        if (bridge.modded(p)) bridge.summary(p, s);
        else menu.open(p, s);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { Recipes.discover(e.getPlayer()); }

    @EventHandler public void onQuit(PlayerQuitEvent e) { search.end(e.getPlayer().getUniqueId()); waypoints.clear(e.getPlayer()); }

    @EventHandler public void onReloaded(ServerResourcesReloadedEvent e) {
        if (Bukkit.getRecipe(Recipes.KEY) != null) return;
        Recipes.register();
        for (Player p : Bukkit.getOnlinePlayers()) Recipes.discover(p);
    }

    // ---- /itembeacon ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info" -> info(p);
            case "demo" -> demo(p);
            case "owner" -> owner(p, args);
            default -> {
                p.sendMessage(Component.text("Item Beacon", NamedTextColor.AQUA));
                p.sendMessage(grey("  Craft a compass, a lodestone and an eye of ender in a column (top to bottom): an Item Beacon."));
                p.sendMessage(grey("  Right-click it to see everything in your containers, pick an item, follow the waypoints."));
                p.sendMessage(grey("  Your containers: the ones you place, and any nobody owns once you open it."));
                p.sendMessage(grey("  /" + label + " info: who owns the container you look at."));
                p.sendMessage(grey("  /" + label + " demo: a beacon and some filled containers around you (creative only)."));
                if (p.hasPermission("itembeacon.admin")) p.sendMessage(grey("  /" + label + " owner <player|clear>: give the container you look at to someone, or to nobody."));
            }
        }
        return true;
    }

    static Component grey(String s) { return Component.text(s, NamedTextColor.GRAY); }

    private void info(Player p) {
        Block b = p.getTargetBlockExact(8);
        if (b == null) { p.sendMessage(Component.text("Look at a container or an Item Beacon (within 8 blocks).", NamedTextColor.RED)); return; }
        if (beacons.is(b)) { p.sendMessage(Component.text("That is an Item Beacon (players without the mod see a lodestone).", NamedTextColor.AQUA)); return; }
        if (!Containers.isContainer(b)) { p.sendMessage(Component.text("That isn't a container.", NamedTextColor.RED)); return; }
        Set<UUID> owners = Containers.owners(b);
        if (owners.isEmpty()) { p.sendMessage(Component.text("Nobody owns this yet: the first player to open it gets it.", NamedTextColor.AQUA)); return; }
        List<String> names = owners.stream().map(u -> Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse(u.toString())).toList();
        p.sendMessage(Component.text("Owned by " + String.join(" and ", names) + ".", NamedTextColor.AQUA));
    }

    private void owner(Player p, String[] args) {
        if (!p.hasPermission("itembeacon.admin")) { p.sendMessage(Component.text("You can't change owners.", NamedTextColor.RED)); return; }
        if (args.length < 2) { p.sendMessage(Component.text("/itembeacon owner <player|clear>", NamedTextColor.RED)); return; }
        Block b = p.getTargetBlockExact(8);
        if (b == null || !Containers.isContainer(b)) { p.sendMessage(Component.text("Look at a container (within 8 blocks).", NamedTextColor.RED)); return; }
        UUID to = null;
        if (!args[1].equalsIgnoreCase("clear")) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) { p.sendMessage(Component.text("No player called " + args[1] + " has played here.", NamedTextColor.RED)); return; }
            to = target.getUniqueId();
        }
        Containers.setOwner(b, to);
        index.refreshWithOtherHalf(b);
        p.sendMessage(Component.text(to == null ? "Nobody owns it now (the next player to open it will)." : "It's " + args[1] + "'s now.", NamedTextColor.GREEN));
    }

    private void demo(Player p) {
        if (p.getGameMode() != GameMode.CREATIVE) { p.sendMessage(Component.text("Demos only work in creative mode.", NamedTextColor.RED)); return; }
        String problem = Demo.build(p, beacons, index);
        if (problem != null) { p.sendMessage(Component.text(problem, NamedTextColor.RED)); return; }
        p.getInventory().addItem(BeaconItem.create(1));
        p.sendMessage(Component.text("An Item Beacon in front of you, with your containers around it (one buried below, one in a chest minecart). "
            + "Right-click the beacon. You also got a beacon item.", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> subs = new ArrayList<>(List.of("info", "demo"));
        if (sender.hasPermission("itembeacon.admin")) subs.add("owner");
        if (args.length == 1) return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("owner") && sender.hasPermission("itembeacon.admin")) {
            List<String> names = new ArrayList<>(List.of("clear"));
            for (Player o : Bukkit.getOnlinePlayers()) names.add(o.getName());
            return names.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
