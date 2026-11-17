package dev.whizzle.itembeacon;

import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.util.BoundingBox;

import java.util.List;

/**
 * The recipe (a column: compass on top, a lodestone in the middle, an eye of ender below; shaped, so it fits any
 * column of the grid) and the guards that keep an Item Beacon from being used as a plain lodestone: no other crafting
 * recipe takes it (grids and crafters), and sulfur cubes don't keep it.
 */
public final class Recipes implements Listener {
    public static final NamespacedKey KEY = new NamespacedKey("itembeacon", "item_beacon");

    public static ShapedRecipe recipe() {
        ShapedRecipe r = new ShapedRecipe(KEY, BeaconItem.create(1));
        r.shape("C", "L", "E");
        r.setIngredient('C', Material.COMPASS);
        // A plain lodestone only (an Item Beacon is a lodestone too).
        r.setIngredient('L', RecipeChoice.predicateChoice(i -> i != null && i.getType() == Material.LODESTONE && !BeaconItem.is(i), new ItemStack(Material.LODESTONE)));
        r.setIngredient('E', Material.ENDER_EYE);
        r.setCategory(CraftingBookCategory.MISC);
        return r;
    }

    public static void register() {
        Bukkit.removeRecipe(KEY);
        Bukkit.addRecipe(recipe());
    }

    public static void unregister() { Bukkit.removeRecipe(KEY); }

    public static void discover(Player p) { p.discoverRecipes(List.of(KEY)); }

    static boolean anyBeacon(ItemStack[] items) {
        for (ItemStack i : items) if (BeaconItem.is(i)) return true;
        return false;
    }

    static boolean ours(Recipe r) { return r instanceof Keyed k && k.getKey().getNamespace().equals("itembeacon"); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent e) {
        if (e.getInventory().getResult() != null && !ours(e.getRecipe()) && anyBeacon(e.getInventory().getMatrix())) e.getInventory().setResult(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        if (!ours(e.getRecipe()) && e.getBlock().getState(false) instanceof Crafter crafter && anyBeacon(crafter.getInventory().getContents())) e.setCancelled(true);
    }

    // ---- sulfur cubes (they'd show a lodestone inside) ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFeedCube(PlayerInteractEntityEvent e) {
        if (e.getRightClicked().getType() == EntityType.SULFUR_CUBE && BeaconItem.is(e.getPlayer().getInventory().getItem(e.getHand()))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCubeSwallowed(EntityEquipmentChangedEvent e) {
        if (e.getEntity().getType() != EntityType.SULFUR_CUBE) return;
        EntityEquipmentChangedEvent.EquipmentChange body = e.getEquipmentChanges().get(EquipmentSlot.BODY);
        if (body != null && BeaconItem.is(body.newItem())) spitOut(e.getEntity());
    }

    static void spitOut(LivingEntity cube) {
        EntityEquipment eq = cube.getEquipment();
        if (eq == null) return;
        ItemStack inside = eq.getItem(EquipmentSlot.BODY);
        if (!BeaconItem.is(inside)) return;
        eq.setItem(EquipmentSlot.BODY, null);
        Item dropped = cube.getWorld().dropItem(cube.getLocation(), inside);
        dropped.setCanMobPickup(false);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispenseIntoCube(BlockDispenseEvent e) {
        if (!BeaconItem.is(e.getItem()) || !(e.getBlock().getBlockData() instanceof Directional d)) return;
        Block front = e.getBlock().getRelative(d.getFacing());
        for (Entity n : front.getWorld().getNearbyEntities(BoundingBox.of(front)))
            if (n.getType() == EntityType.SULFUR_CUBE) { e.setCancelled(true); return; }
    }
}
