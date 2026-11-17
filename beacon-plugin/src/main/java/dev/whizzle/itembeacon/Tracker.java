package dev.whizzle.itembeacon;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Keeps the index up to date: ownership (placing a container makes it yours; opening an unowned one makes it yours;
 * nothing else ever changes an owner), contents (read on close, when a chunk loads or unloads, and by the beacon),
 * and containers that are gone (broken by anything, blown up, burnt, replaced: forgotten). Also tells the waypoints
 * when a player opens a container ({@code opened}).
 */
public final class Tracker implements Listener {
    private final Plugin plugin;
    private final Index index;
    private final BiConsumer<Player, String> opened;
    private final Set<Block> toVerify = new LinkedHashSet<>();

    public Tracker(Plugin plugin, Index index, BiConsumer<Player, String> opened) {
        this.plugin = plugin;
        this.index = index;
        this.opened = opened;
    }

    /** Called every tick: rereads blocks that may have stopped being containers last tick. */
    public void tick() {
        if (toVerify.isEmpty()) return;
        List<Block> blocks = List.copyOf(toVerify);
        toVerify.clear();
        for (Block b : blocks) {
            if (b.getType() == Material.ENDER_CHEST) continue;
            index.removeEnderChest(b);
            if (!index.holders(Containers.id(b)).isEmpty() || Containers.isContainer(b)) index.refreshWithOtherHalf(b);
        }
    }

    void verifyLater(Block b) { toVerify.add(b); }

    // ---- placing ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        if (b.getType() == Material.ENDER_CHEST) { index.addEnderChest(b); return; }
        if (!Containers.isContainer(b)) { verifyLater(b); return; }   // replaced a container? (can't, but cheap)
        Containers.setOwner(b, e.getPlayer().getUniqueId());   // a new block: placing it makes it yours
        index.refreshWithOtherHalf(b);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent e) {
        if (e.getPlayer() == null || !Containers.isContainer(e.getEntity())) return;
        Containers.setOwner(e.getEntity(), e.getPlayer().getUniqueId());
        index.refresh(e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (e.getPlayer() == null || !(e.getEntity() instanceof ItemFrame f)) return;
        Containers.setOwner(f, e.getPlayer().getUniqueId());
        index.refresh(f);
    }

    // ---- opening, closing ----

    /** The containers behind an inventory: one block, both halves of a double chest, or an entity. */
    static List<Object> containers(Inventory inv) {
        InventoryHolder h = inv.getHolder(false);
        if (h instanceof DoubleChest dc) {
            List<Object> out = new ArrayList<>(2);
            if (dc.getLeftSide(false) instanceof Chest c) out.add(c.getBlock());
            if (dc.getRightSide(false) instanceof Chest c) out.add(c.getBlock());
            return out;
        }
        if (h instanceof BlockInventoryHolder b && Containers.isContainer(b.getBlock())) return List.of(b.getBlock());
        if (h instanceof Entity en && Containers.isContainer(en)) return List.of(en);
        return List.of();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) { opened.accept(p, Index.ENDER); return; }
        for (Object c : containers(e.getInventory())) {
            if (c instanceof Block b) {
                Containers.claim(b, p.getUniqueId());
                index.refreshWithOtherHalf(b);
                opened.accept(p, Containers.id(b));
            } else if (c instanceof Entity en) {
                Containers.claim(en, p.getUniqueId());
                index.refresh(en);
                opened.accept(p, Containers.id(en));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) { index.refreshEnder(p); return; }
        for (Object c : containers(e.getInventory())) {
            if (c instanceof Block b) index.refreshWithOtherHalf(b);
            else if (c instanceof Entity en) index.refresh(en);
        }
    }

    /** Item frames: putting an item in or turning it claims an unowned frame (like opening a chest). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameUse(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof ItemFrame f)) return;
        frameTouched(e.getPlayer(), f);
    }

    /** Item frames: punching the item out claims an unowned frame. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFrameHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof ItemFrame f && e.getDamager() instanceof Player p) frameTouched(p, f);
    }

    private void frameTouched(Player p, ItemFrame f) {
        Containers.claim(f, p.getUniqueId());
        opened.accept(p, Containers.id(f));
        Bukkit.getScheduler().runTask(plugin, () -> index.refresh(f));   // after the item went in or out
    }

    // ---- gone ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { verifyLater(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDestroy(BlockDestroyEvent e) { verifyLater(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) { verifyLater(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) { verifyLater(e.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { for (Block b : e.blockList()) verifyLater(b); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { for (Block b : e.blockList()) verifyLater(b); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) { for (Block b : e.getBlocks()) verifyLater(b); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) { for (Block b : e.getBlocks()) verifyLater(b); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent e) {
        switch (e.getCause()) {
            case UNLOAD, PLAYER_QUIT -> { return; }
            default -> { }
        }
        String id = Containers.id(e.getEntity());
        if (!index.holders(id).isEmpty()) index.forget(id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) { index.forget(Containers.id(e.getEntity())); }

    // ---- chunks ----

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent e) { index.rereadChunk(e.getChunk()); }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkUnload(ChunkUnloadEvent e) { index.rereadChunk(e.getChunk()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent e) { for (Entity en : e.getEntities()) if (Containers.owner(en) != null) index.refresh(en); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent e) { for (Entity en : e.getEntities()) if (!index.holders(Containers.id(en)).isEmpty()) index.refresh(en); }
}
