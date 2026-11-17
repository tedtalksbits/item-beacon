package dev.whizzle.itembeacon;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * {@code /itembeacon demo} (creative only): an Item Beacon three blocks ahead, and the player's containers around it:
 * a chest (with a shulker box and a bundle inside, holding more iron), a barrel, a furnace, a chest buried four blocks
 * down (fewer near the bottom of the world), and a chest minecart. Iron ingots are in all of them, so picking iron shows every kind of waypoint.
 */
public final class Demo {
    private Demo() {}

    public static String build(Player p, Beacons beacons, Index index) {
        return build(p.getLocation(), p.getFacing(), p.getUniqueId(), beacons, index);
    }

    /** Builds the demo for {@code owner}; returns a problem, or null when built. */
    public static String build(Location feet, BlockFace facing, UUID owner, Beacons beacons, Index index) {
        BlockFace right = switch (facing) {
            case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; default -> BlockFace.NORTH;
        };
        Block beacon = feet.getBlock().getRelative(facing, 3);
        Block chest = beacon.getRelative(right, 2), barrel = beacon.getRelative(right.getOppositeFace(), 2),
            furnace = beacon.getRelative(facing, 2), cartAt = beacon.getRelative(right, 2).getRelative(facing, 2);
        for (Block b : List.of(beacon, chest, barrel, furnace, cartAt))
            if (!b.getType().isAir()) return "Not enough room: stand on open ground with 6 free blocks ahead.";
        // Four blocks down, or less near the bottom of the world (a flat world has only three).
        int depth = Math.min(4, beacon.getY() - beacon.getWorld().getMinHeight() - 1);
        Block buried = beacon.getRelative(facing, 1).getRelative(BlockFace.DOWN, Math.max(depth, 0));
        boolean bury = depth >= 2;

        beacon.setType(BeaconItem.CARRIER);
        beacons.add(beacon);

        ItemStack box = new ItemStack(Material.RED_SHULKER_BOX);
        box.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(List.of(
            new ItemStack(Material.IRON_INGOT, 32), new ItemStack(Material.GOLD_INGOT, 16))));
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        bundle.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(List.of(
            new ItemStack(Material.IRON_INGOT, 5), new ItemStack(Material.APPLE, 3))));
        fill(chest, Material.CHEST, owner, index, new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.IRON_INGOT, 23),
            new ItemStack(Material.DIAMOND_PICKAXE), box, bundle);
        fill(barrel, Material.BARREL, owner, index, new ItemStack(Material.COBBLESTONE, 64), new ItemStack(Material.COBBLESTONE, 64),
            new ItemStack(Material.BREAD, 12), new ItemStack(Material.IRON_INGOT, 9));
        fill(furnace, Material.FURNACE, owner, index, new ItemStack(Material.RAW_IRON, 4), new ItemStack(Material.COAL, 8), new ItemStack(Material.IRON_INGOT, 7));
        if (bury) fill(buried, Material.CHEST, owner, index, new ItemStack(Material.IRON_INGOT, 40), new ItemStack(Material.DIAMOND, 5));

        StorageMinecart cart = cartAt.getWorld().spawn(cartAt.getLocation().add(0.5, 0, 0.5), StorageMinecart.class, c -> {
            c.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 10), new ItemStack(Material.OAK_LOG, 32));
            Containers.setOwner(c, owner);
        });
        index.refresh(cart);
        return null;
    }

    static void fill(Block b, Material type, UUID owner, Index index, ItemStack... items) {
        b.setType(type);
        if (b.getState(false) instanceof Container c) {
            if (type == Material.FURNACE) {
                c.getInventory().setItem(0, items[0]);
                c.getInventory().setItem(1, items[1]);
                c.getInventory().setItem(2, items[2]);
            } else c.getInventory().addItem(items);
        }
        Containers.setOwner(b, owner);
        index.refresh(b);
    }
}
