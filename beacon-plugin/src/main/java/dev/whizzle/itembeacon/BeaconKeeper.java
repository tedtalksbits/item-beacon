package dev.whizzle.itembeacon;

import io.papermc.paper.event.player.PlayerPickBlockEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

/**
 * Keeps an Item Beacon an Item Beacon through every way a block appears, moves or turns back into an item (Glowing
 * Glass's {@code Keeper}, for a lodestone): placing, a player breaking it, explosions (taken out of the block list and
 * broken here), pistons (records move with the blocks), other drops matched by position, middle-click. And the use:
 * right-clicking a beacon opens it (never binds a compass, which a lodestone would).
 */
public final class BeaconKeeper implements Listener {
    private final Plugin plugin;
    private final Beacons beacons;
    private final BiConsumer<Player, Block> open;
    private final Map<Event, List<Block>> exploding = new IdentityHashMap<>();
    private final Set<Location> droppedThisTick = new HashSet<>();

    public BeaconKeeper(Plugin plugin, Beacons beacons, BiConsumer<Player, Block> open) {
        this.plugin = plugin;
        this.beacons = beacons;
        this.open = open;
    }

    public void tick() {
        exploding.clear();
        droppedThisTick.clear();
    }

    void verifyLater(Block b) { Bukkit.getScheduler().runTask(plugin, () -> beacons.verify(b)); }

    static Location centre(Block b) { return b.getLocation().add(0.5, 0.5, 0.5); }

    // ---- using ----

    /**
     * Right-click: opens the beacon (also with an item in hand, unless sneaking, like a chest). A compass never binds
     * to it, sneaking or not. Sneaking with a block places the block as usual.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!beacons.is(b)) return;
        ItemStack held = e.getItem();
        boolean compass = held != null && (held.getType() == Material.COMPASS);
        if (e.getPlayer().isSneaking() && held != null && !held.getType().isAir() && !compass) return;
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        if (e.getHand() != EquipmentSlot.HAND) return;   // once per click, not again for the off hand
        if (!e.getPlayer().isSneaking()) open.accept(e.getPlayer(), b);
    }

    // ---- placing ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (BeaconItem.is(e.getItemInHand()) && b.getType() == BeaconItem.CARRIER) beacons.add(b);
        else if (beacons.recorded(b)) beacons.remove(b);
    }

    // ---- breaking ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (beacons.recorded(e.getBlock())) verifyLater(e.getBlock()); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBreakDrops(BlockDropItemEvent e) {
        Block b = e.getBlock();
        if (!beacons.recorded(b) || e.getBlockState().getType() != BeaconItem.CARRIER) return;
        beacons.remove(b);
        if (e.getItems().isEmpty()) return;
        Item first = e.getItems().getFirst();
        first.setItemStack(BeaconItem.create(1));
        e.getItems().retainAll(List.of(first));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) { if (beacons.recorded(e.getBlock())) verifyLater(e.getBlock()); }

    /** Drops that spawn while the block is still there ({@code breakNaturally}, withers, {@code setblock destroy}). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent e) {
        ItemStack stack = e.getEntity().getItemStack();
        if (stack.getType() != BeaconItem.CARRIER || BeaconItem.is(stack)) return;
        Block at = e.getLocation().getBlock();
        if (!beacons.is(at) || at.getType() == Material.MOVING_PISTON) return;
        if (droppedThisTick.add(at.getLocation())) {
            e.getEntity().setItemStack(BeaconItem.create(1));
            verifyLater(at);
        } else {
            e.setCancelled(true);
        }
    }

    // ---- explosions ----

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplodeTake(EntityExplodeEvent e) { take(e, e.blockList()); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplodeTake(BlockExplodeEvent e) { take(e, e.blockList()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { blowUp(e, e.getYield()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { blowUp(e, e.getYield()); }

    private void take(Event e, List<Block> blocks) {
        List<Block> found = null;
        for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
            Block b = it.next();
            if (!beacons.is(b) || b.getType() == Material.MOVING_PISTON) continue;
            it.remove();
            (found == null ? found = new ArrayList<>() : found).add(b);
        }
        if (found != null) exploding.put(e, found);
    }

    private void blowUp(Event e, float yield) {
        List<Block> found = exploding.remove(e);
        if (found == null) return;
        beacons.batch(() -> {
            for (Block b : found) {
                if (!beacons.is(b)) continue;
                beacons.remove(b);
                b.setType(Material.AIR);
                if (ThreadLocalRandom.current().nextFloat() < yield) b.getWorld().dropItemNaturally(centre(b), BeaconItem.create(1));
            }
        });
    }

    // ---- pistons ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) { move(e.getBlock(), e.getBlocks(), true); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) { move(e.getBlock(), e.getBlocks(), false); }

    void move(Block piston, List<Block> blocks, boolean extend) {
        if (!(piston.getBlockData() instanceof Directional d)) return;
        BlockFace dir = extend ? d.getFacing() : d.getFacing().getOppositeFace();
        List<Block> moving = new ArrayList<>();
        for (Block b : blocks) if (beacons.is(b)) moving.add(b);
        if (moving.isEmpty()) return;
        beacons.batch(() -> {
            for (Block b : moving) beacons.remove(b);
            for (Block b : moving) beacons.add(b.getRelative(dir));
        });
    }

    // ---- middle-click ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPick(PlayerPickBlockEvent e) {
        if (!beacons.is(e.getBlock())) return;
        Player p = e.getPlayer();
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 36; i++) if (BeaconItem.is(inv.getItem(i))) { slot = i; break; }
        if (slot >= 0) {
            e.setSourceSlot(slot);
            e.setTargetSlot(slot < 9 ? slot : hotbarSlotFor(inv));
            return;
        }
        e.setCancelled(true);
        if (p.getGameMode() != GameMode.CREATIVE) return;
        int target = hotbarSlotFor(inv);
        inv.setItem(target, BeaconItem.create(1));
        inv.setHeldItemSlot(target);
    }

    static int hotbarSlotFor(PlayerInventory inv) {
        int selected = inv.getHeldItemSlot();
        if (empty(inv.getItem(selected))) return selected;
        for (int i = 0; i < 9; i++) if (empty(inv.getItem(i))) return i;
        return selected;
    }

    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }
}
