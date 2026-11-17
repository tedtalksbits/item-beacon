package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.Entry;
import dev.whizzle.itembeacon.model.ItemKey;
import dev.whizzle.itembeacon.model.Stack;
import io.papermc.paper.block.TileStateInventoryHolder;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * What counts as a container, who owns it and what's in it.
 * <ul>
 * <li>Blocks: every block entity that holds items ({@link TileStateInventoryHolder}): chests (trapped, copper),
 * barrels, shulker boxes, hoppers, droppers, dispensers, furnaces, smokers, blast furnaces, brewing stands,
 * crafters, shelves, chiseled bookshelves, decorated pots, lecterns, jukeboxes. Ender chests are separate (their
 * contents belong to the player, not the block).</li>
 * <li>Entities: chest and hopper minecarts, chest boats and rafts (any entity holding an inventory that isn't alive),
 * item frames and glow item frames.</li>
 * </ul>
 * The owner is a UUID in the block entity's or entity's persistent data ({@code itembeacon:owner}). A double chest
 * belongs to either half's owner: both owners see both halves.
 */
public final class Containers {
    private Containers() {}

    public static final NamespacedKey OWNER = new NamespacedKey("itembeacon", "owner");
    /** How deep to look into items inside items (a bundle in a bundle in a shulker box...). */
    static final int MAX_DEPTH = 4;

    // ---- what is a container ----

    public static boolean isContainer(BlockState state) { return state instanceof TileStateInventoryHolder; }

    public static boolean isContainer(Block b) { return isContainer(b.getState(false)); }

    public static boolean isContainer(Entity e) {
        if (e instanceof ItemFrame) return true;
        return e instanceof InventoryHolder && !(e instanceof org.bukkit.entity.LivingEntity);
    }

    // ---- owners ----

    static UUID owner(PersistentDataHolder holder) {
        String s = holder.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    public static UUID owner(Block b) { return b.getState(false) instanceof TileStateInventoryHolder t ? owner(t) : null; }

    public static UUID owner(Entity e) { return owner((PersistentDataHolder) e); }

    /** Sets (or with null, clears) the owner, whatever it was. Only first-time claims and the admin command call this. */
    public static boolean setOwner(Block b, UUID owner) {
        if (!(b.getState(false) instanceof TileStateInventoryHolder t)) return false;
        set(t.getPersistentDataContainer(), owner);
        return true;
    }

    public static void setOwner(Entity e, UUID owner) { set(e.getPersistentDataContainer(), owner); }

    private static void set(PersistentDataContainer pdc, UUID owner) {
        if (owner == null) pdc.remove(OWNER);
        else pdc.set(OWNER, PersistentDataType.STRING, owner.toString());
    }

    /** Makes {@code b} the player's if nobody owns it yet. Returns whether it did. */
    public static boolean claim(Block b, UUID player) {
        if (!(b.getState(false) instanceof TileStateInventoryHolder t) || owner(t) != null) return false;
        set(t.getPersistentDataContainer(), player);
        return true;
    }

    public static boolean claim(Entity e, UUID player) {
        if (!isContainer(e) || owner(e) != null) return false;
        setOwner(e, player);
        return true;
    }

    /** The other half of a double chest, or null. */
    public static Block otherHalf(Block b) {
        if (!(b.getState(false) instanceof Chest chest)) return null;
        if (!(chest.getInventory().getHolder(false) instanceof DoubleChest dc)) return null;
        for (InventoryHolder side : new InventoryHolder[] {dc.getLeftSide(false), dc.getRightSide(false)})
            if (side instanceof Chest c && !c.getBlock().equals(b)) return c.getBlock();
        return null;
    }

    /** Who sees this block's contents: its owner and, for a double chest, the other half's owner. */
    public static Set<UUID> owners(Block b) {
        Set<UUID> out = new LinkedHashSet<>();
        UUID own = owner(b);
        if (own != null) out.add(own);
        Block other = otherHalf(b);
        if (other != null) {
            UUID o = owner(other);
            if (o != null) out.add(o);
        }
        return out;
    }

    // ---- contents ----

    /** This block's own items (a double chest half: only its half). */
    public static ItemStack[] items(BlockState state) {
        if (state instanceof Chest c) return c.getBlockInventory().getContents();
        if (state instanceof TileStateInventoryHolder h) return h.getInventory().getContents();
        return new ItemStack[0];
    }

    public static ItemStack[] items(Entity e) {
        if (e instanceof ItemFrame f) return new ItemStack[] {f.getItem()};
        if (e instanceof InventoryHolder h) return h.getInventory().getContents();
        return new ItemStack[0];
    }

    /** Every item, items inside shulker boxes and bundles too, grouped by exact item and where it sits. */
    public static List<Stack> stacks(ItemStack[] items) {
        Map<List<Object>, int[]> counts = new LinkedHashMap<>();
        Map<List<Object>, ItemKey> keys = new HashMap<>();
        for (ItemStack i : items) add(i, "", 0, counts, keys);
        List<Stack> out = new ArrayList<>(counts.size());
        for (var e : counts.entrySet()) out.add(new Stack(keys.get(e.getKey()), e.getValue()[0], (String) e.getKey().get(1)));
        return out;
    }

    private static void add(ItemStack item, String inside, int depth, Map<List<Object>, int[]> counts, Map<List<Object>, ItemKey> keys) {
        if (item == null || item.getType().isAir() || item.getAmount() < 1) return;
        ItemKey key = key(item);
        List<Object> k = List.of(key, inside);
        keys.putIfAbsent(k, key);
        counts.computeIfAbsent(k, x -> new int[1])[0] += item.getAmount();
        if (depth >= MAX_DEPTH) return;
        String here = name(item.getType()) + (inside.isEmpty() ? "" : " in " + inside);
        ItemContainerContents box = item.getData(DataComponentTypes.CONTAINER);
        if (box != null) for (ItemStack i : box.contents()) for (int n = 0; n < item.getAmount(); n++) add(i, here, depth + 1, counts, keys);
        BundleContents bundle = item.getData(DataComponentTypes.BUNDLE_CONTENTS);
        if (bundle != null) for (ItemStack i : bundle.contents()) for (int n = 0; n < item.getAmount(); n++) add(i, here, depth + 1, counts, keys);
    }

    public static ItemKey key(ItemStack item) { return new ItemKey(item.asOne().serializeAsBytes()); }

    /** "Red Shulker Box" for {@code RED_SHULKER_BOX}. */
    public static String name(Material m) {
        StringBuilder sb = new StringBuilder();
        for (String w : m.getKey().getKey().split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    // ---- entries ----

    public static String id(Block b) { return Entry.blockId(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }

    public static String id(Entity e) { return Entry.entityId(e.getUniqueId()); }

    public static Entry entry(Block b, BlockState state, long now) {
        return new Entry(id(b), b.getWorld().getUID(), b.getWorld().getKey().asString(), b.getX(), b.getY(), b.getZ(),
            b.getType().getKey().getKey(), stacks(items(state)), now);
    }

    public static Entry entry(Entity e, long now) {
        var l = e.getLocation();
        return new Entry(id(e), e.getWorld().getUID(), e.getWorld().getKey().asString(), l.getBlockX(), l.getBlockY(), l.getBlockZ(),
            e.getType().getKey().getKey(), stacks(items(e)), now);
    }
}
