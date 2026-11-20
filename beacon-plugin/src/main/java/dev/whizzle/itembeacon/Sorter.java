package dev.whizzle.itembeacon;

import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;

/**
 * Inventory sorting (mod only): the client sends the new order of a storage container's slots, or of the player's
 * main 27 slots; this lays the items out in that order with partial stacks merged. Anyone who has the container open
 * can sort it. Hotbar, armour and off-hand are never touched.
 */
public final class Sorter {
    private Sorter() {}

    /** Storage containers that can be sorted (chests of every kind, minecarts and boats included, are CHEST). */
    public static final Set<InventoryType> SORTABLE = EnumSet.of(InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX,
        InventoryType.ENDER_CHEST, InventoryType.HOPPER, InventoryType.DISPENSER, InventoryType.DROPPER);
    public static final int MAIN_FIRST = 9, MAIN_LAST = 35;

    /** Applies a sort request; returns a problem, or null when sorted. */
    public static String sort(Player p, boolean player, List<Integer> order) {
        if (player) {
            List<Integer> want = new ArrayList<>();
            for (int i = MAIN_FIRST; i <= MAIN_LAST; i++) want.add(i);
            if (!isPermutation(order, want)) return "not a permutation of the main slots";
            PlayerInventory inv = p.getInventory();
            ItemStack[] items = new ItemStack[order.size()];
            for (int i = 0; i < items.length; i++) items[i] = inv.getItem(order.get(i));
            ItemStack[] out = merge(items);
            for (int i = 0; i < out.length; i++) inv.setItem(MAIN_FIRST + i, out[i]);
            return null;
        }
        InventoryView view = p.getOpenInventory();
        Inventory top = view.getTopInventory();
        if (!SORTABLE.contains(top.getType()) || !realContainer(p, top)) return "not a storage container: " + top.getType();
        List<Integer> want = new ArrayList<>();
        for (int i = 0; i < top.getSize(); i++) want.add(i);
        if (!isPermutation(order, want)) return "not a permutation of the container's " + top.getSize() + " slots";
        ItemStack[] items = new ItemStack[order.size()];
        for (int i = 0; i < items.length; i++) items[i] = top.getItem(order.get(i));
        top.setContents(merge(items));
        return null;
    }

    /**
     * A real container, not a plugin's menu that looks like a chest (this plugin's own fallback menu, shops...) or an
     * animal's inventory (a donkey's saddle slot): a block, a double chest, a minecart or boat, or the player's ender
     * chest.
     */
    static boolean realContainer(Player p, Inventory top) {
        if (top.getType() == InventoryType.ENDER_CHEST) return top.equals(p.getEnderChest());
        InventoryHolder h = top.getHolder(false);
        return h instanceof DoubleChest || h instanceof BlockInventoryHolder || (h instanceof Entity e && !(e instanceof LivingEntity));
    }

    public static boolean isPermutation(List<Integer> order, List<Integer> want) {
        return order.size() == want.size() && new HashSet<>(order).equals(new HashSet<>(want)) && new HashSet<>(order).size() == order.size();
    }

    /**
     * The items in this order with partial stacks merged into the earliest matching stack, empty slots last. The
     * result has the same length; merging only ever frees slots, so everything fits.
     */
    public static ItemStack[] merge(ItemStack[] items) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : items) {
            if (s == null || s.getType().isAir() || s.getAmount() < 1) continue;
            ItemStack left = s.clone();
            for (ItemStack o : out) {
                if (left.getAmount() == 0) break;
                if (!o.isSimilar(left)) continue;
                int room = o.getMaxStackSize() - o.getAmount();
                if (room <= 0) continue;
                int move = Math.min(room, left.getAmount());
                o.setAmount(o.getAmount() + move);
                left.setAmount(left.getAmount() - move);
            }
            if (left.getAmount() > 0) out.add(left);
        }
        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < out.size(); i++) result[i] = out.get(i);
        return result;
    }
}
