package dev.whizzle.itembeacon.selftest;

import dev.whizzle.itembeacon.*;
import dev.whizzle.itembeacon.model.*;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Supplier;

/**
 * Development-only: checks Item Beacon on a real Paper server, without a player: the recipe (a crafter crafts it),
 * the beacon block (records through breakNaturally, TNT, pistons, a chunk unload), ownership and contents for every
 * container kind (blocks and entities), nested shulker boxes and bundles, double chests, claiming, containers that
 * are gone, unloaded chunks (last seen), other dimensions, the radius, ender chests, a summary split into pages, saving
 * and loading the index, and the demo. Writes report.txt ending "SELFTEST PASSED"; with -Ditembeacon.selftest.exit=true
 * it stops the server when done. Anything needing a real client (opening, the menu, outlines): docs/TEST_PLAN.md.
 */
public final class SelfTestPlugin extends JavaPlugin {
    record Step(String name, Runnable action, int ticks, Supplier<String> check) {}

    static final UUID A = new UUID(0xA, 1), B = new UUID(0xB, 2);
    private final List<Step> steps = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private World w;
    private ItemBeaconPlugin ib;
    private Beacons beacons;
    private Index index;
    private static final int Y = -60;
    private int failures;

    @Override public void onEnable() { getServer().getScheduler().runTaskLater(this, this::start, 40); }

    Block at(int x, int z) { return w.getBlockAt(x, Y, z); }

    static String all(String... problems) { for (String p : problems) if (p != null) return p; return null; }

    static ItemStack item(Material m, int n) { return new ItemStack(m, n); }

    static ItemKey key(Material m) { return Containers.key(new ItemStack(m)); }

    /** A container of {@code type} at (x, z) owned by {@code owner}, holding {@code items}, read into the index. */
    Block container(int x, int z, Material type, UUID owner, ItemStack... items) {
        Block b = at(x, z);
        b.setType(type);
        if (b.getState(false) instanceof Container c) c.getInventory().addItem(items);
        if (owner != null) Containers.setOwner(b, owner);
        index.refresh(b);
        return b;
    }

    Entry entry(UUID owner, Block b) { return index.of(owner).get(Containers.id(b)); }

    String has(UUID owner, Block b, Material m, int count) {
        Entry e = entry(owner, b);
        if (e == null) return b.getType() + " at " + b.getX() + " " + b.getZ() + " isn't in " + name(owner) + "'s list";
        int n = e.count(key(m));
        return n == count ? null : b.getType() + " at " + b.getX() + " " + b.getZ() + " holds " + n + " " + m + " in the list, expected " + count;
    }

    static String name(UUID u) { return u.equals(A) ? "A" : u.equals(B) ? "B" : u.toString(); }

    String noEntry(UUID owner, String id) { return index.of(owner).contains(id) ? id + " is still in " + name(owner) + "'s list" : null; }

    long total(UUID owner, Material m) {
        for (var t : index.of(owner).totals(e -> true)) if (t.item().equals(key(m))) return t.count();
        return 0;
    }

    Block beaconAt(int x, int z) {
        Block b = at(x, z);
        b.setType(Material.LODESTONE);
        beacons.add(b);
        return b;
    }

    List<Item> itemsNear(Block b, double r) { return new ArrayList<>(w.getNearbyEntitiesByType(Item.class, b.getLocation().add(0.5, 0.5, 0.5), r)); }

    String droppedOneBeacon(Block b) {
        List<Item> items = itemsNear(b, 3);
        int beaconsDropped = 0, lodestones = 0;
        for (Item i : items) {
            if (BeaconItem.is(i.getItemStack())) beaconsDropped += i.getItemStack().getAmount();
            else if (i.getItemStack().getType() == Material.LODESTONE) lodestones += i.getItemStack().getAmount();
        }
        for (Item i : items) i.remove();
        return beaconsDropped == 1 && lodestones == 0 ? null : "dropped " + beaconsDropped + " beacon(s) and " + lodestones + " plain lodestone(s)";
    }

    Search.Session session(UUID owner, Block beacon, Set<String> live) {
        return new Search.Session(1, owner, beacon, index.of(owner).totals(e -> true), live);
    }

    int itemNumber(Search.Session s, Material m) {
        for (int i = 0; i < s.items().size(); i++) if (s.items().get(i).item().equals(key(m))) return i;
        return -1;
    }

    void start() {
        w = getServer().getWorlds().getFirst();
        ib = (ItemBeaconPlugin) Objects.requireNonNull(getServer().getPluginManager().getPlugin("ItemBeacon"));
        beacons = ib.beacons();
        index = ib.index();
        for (int cx = -2; cx <= 20; cx++) for (int cz = -2; cz <= 2; cz++) w.setChunkForceLoaded(cx, cz, true);
        for (int x = -10; x <= 330; x++) for (int z = -10; z <= 10; z++) {
            w.getBlockAt(x, Y - 1, z).setType(Material.STONE);
            for (int y = Y; y < Y + 6; y++) w.getBlockAt(x, y, z).setType(Material.AIR);
        }

        // ---- the beacon item and block ----
        steps.add(new Step("recipe: a column of compass, lodestone, eye of ender -> Item Beacon; a beacon can't be the lodestone", () -> { }, 1, () -> {
            if (!(Bukkit.getRecipe(Recipes.KEY) instanceof ShapedRecipe r)) return "no recipe";
            // The server's copy renames the shape's letters: go by position.
            String[] shape = r.getShape();
            if (shape.length != 3 || shape[0].length() != 1) return "shape " + Arrays.toString(shape);
            RecipeChoice top = r.getChoiceMap().get(shape[0].charAt(0)), mid = r.getChoiceMap().get(shape[1].charAt(0)), bottom = r.getChoiceMap().get(shape[2].charAt(0));
            return all(BeaconItem.is(r.getResult()) ? null : "result " + r.getResult(),
                top.test(item(Material.COMPASS, 1)) ? null : "top isn't a compass", bottom.test(item(Material.ENDER_EYE, 1)) ? null : "bottom isn't an eye of ender",
                mid.test(item(Material.LODESTONE, 1)) ? null : "middle doesn't take a lodestone",
                mid.test(BeaconItem.create(1)) ? "a beacon counts as the lodestone" : null);
        }));
        int cr = 0;
        // The crafter puts its result into the chest in front of it (no flying item to find), and the check waits
        // long enough for a slow CI runner (CI once found nothing after 12 ticks within 2.5 blocks).
        steps.add(new Step("crafter: compass, lodestone, eye of ender in the middle column -> one Item Beacon, into the chest in front", () -> {
            Block out = at(cr + 1, 0);
            out.setType(Material.CHEST);
            Block c = at(cr, 0);
            c.setType(Material.CRAFTER);
            org.bukkit.block.data.type.Crafter data = (org.bukkit.block.data.type.Crafter) c.getBlockData();
            data.setOrientation(org.bukkit.block.Orientation.EAST_UP);
            c.setBlockData(data);
            Inventory inv = ((Crafter) c.getState(false)).getInventory();
            inv.setItem(1, item(Material.COMPASS, 1));
            inv.setItem(4, item(Material.LODESTONE, 1));
            inv.setItem(7, item(Material.ENDER_EYE, 1));
            at(cr, -1).setType(Material.REDSTONE_BLOCK);
        }, 40, () -> {
            Inventory chest = ((Chest) at(cr + 1, 0).getState(false)).getBlockInventory();
            List<String> got = new ArrayList<>();
            int beaconsMade = 0;
            for (ItemStack i : chest.getContents()) {
                if (i == null) continue;
                got.add(i.getType() + "x" + i.getAmount() + (BeaconItem.is(i) ? "(beacon)" : ""));
                if (BeaconItem.is(i)) beaconsMade += i.getAmount();
            }
            List<Item> loose = itemsNear(at(cr + 1, 0), 6);
            for (Item i : loose) got.add("loose " + i.getItemStack().getType());
            for (Item i : loose) i.remove();
            return beaconsMade == 1 && got.size() == 1 ? null : "crafter made " + got;
        }));
        int bb = 10;
        steps.add(new Step("beacon: breakNaturally drops one Item Beacon and forgets the record", () -> beaconAt(bb, 0).breakNaturally(new ItemStack(Material.DIAMOND_PICKAXE)), 3,
            () -> all(droppedOneBeacon(at(bb, 0)), beacons.recorded(at(bb, 0)) ? "record still there" : null)));
        steps.add(new Step("beacon: TNT drops one Item Beacon", () -> {
            beaconAt(bb, 4);
            w.spawn(at(bb, 4).getRelative(BlockFace.UP).getLocation().add(0.5, 0, 0.5), TNTPrimed.class, t -> t.setFuseTicks(1));
        }, 20, () -> all(at(bb, 4).getType() == Material.LODESTONE ? "the beacon survived the TNT" : null, droppedOneBeacon(at(bb, 4)),
            beacons.recorded(at(bb, 4)) ? "record still there" : null)));
        int pi = 20;
        steps.add(new Step("beacon: a piston can't move it (lodestones don't move); the record stays", () -> {
            Block piston = at(pi, 0);
            piston.setType(Material.PISTON);
            Directional d = (Directional) piston.getBlockData();
            d.setFacing(BlockFace.EAST);
            piston.setBlockData(d);
            beaconAt(pi + 1, 0);
            at(pi, -1).setType(Material.REDSTONE_BLOCK);
        }, 6, () -> all(Material.LODESTONE.createBlockData().getPistonMoveReaction() == PistonMoveReaction.BLOCK ? null
                : "INFO lodestones move now (" + Material.LODESTONE.createBlockData().getPistonMoveReaction() + "): the keeper moves records, test it",
            beacons.is(at(pi + 1, 0)) ? null : "the beacon isn't where it was (" + at(pi + 1, 0).getType() + ")")));
        int far = 600;
        steps.add(new Step("beacon and owned chest survive a chunk unload/load (records in chunk and block entity data)", () -> {
            beaconAt(far, 0);
            container(far + 2, 0, Material.CHEST, A, item(Material.IRON_INGOT, 3));
            Chunk c = at(far, 0).getChunk();
            c.setForceLoaded(false);
        }, 60, () -> {
            boolean unloaded = w.unloadChunk(far >> 4, 0, true);
            Block b = at(far, 0);
            w.getChunkAt(b).load();   // getBlockAt alone doesn't load it
            return all(unloaded ? null : "couldn't unload the chunk (test can't tell)", beacons.is(b) ? null : "beacon record lost",
                A.equals(Containers.owner(at(far + 2, 0))) ? null : "chest owner lost: " + Containers.owner(at(far + 2, 0)),
                has(A, at(far + 2, 0), Material.IRON_INGOT, 3));
        }));

        // ---- ownership and contents ----
        int o = 40;
        steps.add(new Step("contents: loose items, a shulker box and a bundle inside, counted and labelled", () -> {
            ItemStack box = item(Material.RED_SHULKER_BOX, 1);
            box.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(List.of(item(Material.IRON_INGOT, 32), item(Material.GOLD_INGOT, 16))));
            ItemStack bundle = item(Material.BUNDLE, 1);
            bundle.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(List.of(item(Material.IRON_INGOT, 5))));
            ItemStack boxWithBundle = item(Material.BLUE_SHULKER_BOX, 1);
            boxWithBundle.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(List.of(bundle.clone())));
            container(o, 0, Material.CHEST, A, item(Material.IRON_INGOT, 10), box, bundle, boxWithBundle);
        }, 1, () -> {
            Entry e = entry(A, at(o, 0));
            if (e == null) return "chest not listed";
            Set<String> insides = new TreeSet<>();
            for (dev.whizzle.itembeacon.model.Stack s : e.stacks()) if (s.item().equals(key(Material.IRON_INGOT))) insides.add(s.inside());
            return all(e.count(key(Material.IRON_INGOT)) == 10 + 32 + 5 + 5 ? null : "iron " + e.count(key(Material.IRON_INGOT)),
                e.count(key(Material.GOLD_INGOT)) == 16 ? null : "gold " + e.count(key(Material.GOLD_INGOT)),
                insides.equals(new TreeSet<>(List.of("", "Red Shulker Box", "Bundle", "Bundle in Blue Shulker Box"))) ? null : "insides " + insides);
        }));
        steps.add(new Step("claiming: an unowned barrel becomes the first opener's; a second opener doesn't change it", () -> {
            Block b = container(o + 2, 0, Material.BARREL, null, item(Material.BREAD, 4));
            Containers.claim(b, B);
            Containers.claim(b, A);
            index.refresh(b);
        }, 1, () -> all(B.equals(Containers.owner(at(o + 2, 0))) ? null : "owner " + Containers.owner(at(o + 2, 0)),
            has(B, at(o + 2, 0), Material.BREAD, 4), noEntry(A, Containers.id(at(o + 2, 0))))));
        steps.add(new Step("double chest: either half's owner sees both halves", () -> {
            Block left = container(o + 4, 0, Material.CHEST, A, item(Material.APPLE, 1));
            Block right = at(o + 5, 0);
            right.setType(Material.CHEST);
            org.bukkit.block.data.type.Chest l = (org.bukkit.block.data.type.Chest) left.getBlockData(), r = (org.bukkit.block.data.type.Chest) right.getBlockData();
            l.setFacing(BlockFace.NORTH); r.setFacing(BlockFace.NORTH);
            l.setType(org.bukkit.block.data.type.Chest.Type.LEFT); r.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
            left.setBlockData(l); right.setBlockData(r);
            ((Chest) right.getState(false)).getBlockInventory().addItem(item(Material.CARROT, 2));
            Containers.setOwner(right, B);
            index.refreshWithOtherHalf(left);
        }, 1, () -> all(Containers.otherHalf(at(o + 4, 0)) == null ? "the chests didn't join" : null,
            has(A, at(o + 5, 0), Material.CARROT, 2), has(B, at(o + 4, 0), Material.APPLE, 1), has(A, at(o + 4, 0), Material.APPLE, 1),
            has(A, at(o + 4, 0), Material.CARROT, 0))));

        int k = 60;
        Map<Material, Material> kinds = new LinkedHashMap<>();
        for (Material m : List.of(Material.CHEST, Material.TRAPPED_CHEST, Material.COPPER_CHEST, Material.BARREL, Material.WHITE_SHULKER_BOX, Material.HOPPER,
            Material.DROPPER, Material.DISPENSER, Material.FURNACE, Material.SMOKER, Material.BLAST_FURNACE, Material.CRAFTER, Material.DECORATED_POT)) kinds.put(m, Material.IRON_INGOT);
        kinds.put(Material.BREWING_STAND, Material.BLAZE_POWDER);
        kinds.put(Material.OAK_SHELF, Material.IRON_INGOT);
        kinds.put(Material.CHISELED_BOOKSHELF, Material.BOOK);
        kinds.put(Material.LECTERN, Material.WRITABLE_BOOK);
        kinds.put(Material.JUKEBOX, Material.MUSIC_DISC_CAT);
        steps.add(new Step("every container kind is listed with its item: " + kinds.keySet(), () -> {
            int x = k;
            for (var e : kinds.entrySet()) {
                Block b = at(x, 0);
                b.setType(e.getKey());
                Inventory inv = ((io.papermc.paper.block.TileStateInventoryHolder) b.getState(false)).getInventory();
                int slot = e.getKey() == Material.BREWING_STAND ? 4 : 0;
                inv.setItem(slot, item(e.getValue(), 1));
                Containers.setOwner(b, A);
                index.refresh(b);
                x += 2;
            }
        }, 1, () -> {
            int x = k;
            List<String> problems = new ArrayList<>();
            for (var e : kinds.entrySet()) {
                String p = has(A, at(x, 0), e.getValue(), 1);
                if (p != null) problems.add(p);
                x += 2;
            }
            return problems.isEmpty() ? null : String.join("; ", problems);
        }));
        List<Entity> ents = new ArrayList<>();
        int en = 110;
        steps.add(new Step("entity containers: chest and hopper minecarts, a chest boat, an item frame", () -> {
            ents.add(w.spawn(at(en, 0).getLocation().add(0.5, 0, 0.5), StorageMinecart.class, c -> c.getInventory().addItem(item(Material.IRON_INGOT, 2))));
            ents.add(w.spawn(at(en + 2, 0).getLocation().add(0.5, 0, 0.5), HopperMinecart.class, c -> c.getInventory().addItem(item(Material.IRON_INGOT, 3))));
            ents.add(w.spawn(at(en + 4, 0).getLocation().add(0.5, 0, 0.5), org.bukkit.entity.boat.OakChestBoat.class, c -> c.getInventory().addItem(item(Material.IRON_INGOT, 4))));
            at(en + 6, 1).setType(Material.STONE);
            ents.add(w.spawn(at(en + 6, 0).getLocation(), ItemFrame.class, f -> { f.setFacingDirection(BlockFace.NORTH, true); f.setItem(item(Material.IRON_INGOT, 1)); }));
            for (Entity e : ents) { Containers.setOwner(e, A); index.refresh(e); }
        }, 2, () -> {
            List<String> problems = new ArrayList<>();
            int[] want = {2, 3, 4, 1};
            for (int i = 0; i < ents.size(); i++) {
                Entry e = index.of(A).get(Containers.id(ents.get(i)));
                if (e == null) problems.add(ents.get(i).getType() + " not listed");
                else if (e.count(key(Material.IRON_INGOT)) != want[i]) problems.add(ents.get(i).getType() + " holds " + e.count(key(Material.IRON_INGOT)));
            }
            return problems.isEmpty() ? null : String.join("; ", problems);
        }));
        steps.add(new Step("gone: a removed minecart and item frame are forgotten", () -> { ents.get(0).remove(); ents.get(3).remove(); }, 2,
            () -> all(noEntry(A, Containers.id(ents.get(0))), noEntry(A, Containers.id(ents.get(3))),
                index.of(A).contains(Containers.id(ents.get(1))) ? null : "the hopper minecart was forgotten too")));
        int g = 130;
        steps.add(new Step("gone: TNT next to an owned chest; the chest is forgotten", () -> {
            container(g, 0, Material.CHEST, A, item(Material.IRON_INGOT, 1));
            w.spawn(at(g, 1).getLocation().add(0.5, 0, 0.5), TNTPrimed.class, t -> t.setFuseTicks(1));
        }, 20, () -> all(at(g, 0).getType() == Material.CHEST ? "the chest survived (test can't tell)" : null, noEntry(A, Containers.id(at(g, 0))))));
        steps.add(new Step("gone: a chest replaced without events (breakNaturally) is forgotten when read near a beacon", () -> {
            container(g + 4, 0, Material.CHEST, A, item(Material.IRON_INGOT, 1));
            at(g + 4, 0).breakNaturally();
        }, 2, () -> {
            boolean before = index.of(A).contains(Containers.id(at(g + 4, 0)));
            ib.search().readNear(A, at(g + 6, 0));
            return all(before ? null : "INFO: forgotten even before (fine)", noEntry(A, Containers.id(at(g + 4, 0))));
        }));

        // ---- searching ----
        int s = 160;
        steps.add(new Step("radius: containers inside are reread live, those outside keep their last-seen contents", () -> {
            beaconAt(s, 0);
            container(s + 3, 0, Material.CHEST, B, item(Material.EMERALD, 1));
            container(s + 100, 0, Material.CHEST, B, item(Material.EMERALD, 1));
            // Changed without any event: only a reread notices.
            ((Chest) at(s + 3, 0).getState(false)).getBlockInventory().addItem(item(Material.EMERALD, 9));
            ((Chest) at(s + 100, 0).getState(false)).getBlockInventory().addItem(item(Material.EMERALD, 9));
        }, 1, () -> {
            Set<String> live = ib.search().readNear(B, at(s, 0));
            return all(has(B, at(s + 3, 0), Material.EMERALD, 10), has(B, at(s + 100, 0), Material.EMERALD, 1),
                live.contains(Containers.id(at(s + 3, 0))) ? null : "inside not live", live.contains(Containers.id(at(s + 100, 0))) ? "outside read live" : null);
        }));
        steps.add(new Step("places: nearest first; live or last seen; other dimension last, labelled, no waypoint", () -> {
            World nether = Bukkit.getWorlds().stream().filter(x -> x.getEnvironment() == World.Environment.NETHER).findFirst().orElse(null);
            if (nether == null) return;
            Block n = nether.getBlockAt(0, 70, 0);
            n.setType(Material.CHEST);
            ((Chest) n.getState(false)).getBlockInventory().addItem(item(Material.EMERALD, 5));
            Containers.setOwner(n, B);
            index.refresh(n);
        }, 1, () -> {
            Block beacon = at(s, 0);
            Set<String> live = ib.search().readNear(B, beacon);
            Search.Session ses = session(B, beacon, live);
            List<Protocol.Place> places = ib.search().places(ses, itemNumber(ses, Material.EMERALD));
            if (places.size() != 3) return "places " + places;
            Protocol.Place first = places.get(0), second = places.get(1), third = places.get(2);
            return all(first.x() == s + 3 && first.live() ? null : "first " + first, second.x() == s + 100 && !second.live() ? null : "second " + second,
                !third.sameDimension() && third.dimension().equals("minecraft:the_nether") && third.count() == 5 ? null : "third " + third,
                FallbackMenu.dimension(third.dimension()).equals("in the Nether") ? null : "label " + FallbackMenu.dimension(third.dimension()));
        }));
        steps.add(new Step("ender chests: found when their chunk loads; the nearest is chosen", () -> {
            at(s + 10, 0).setType(Material.ENDER_CHEST);
            at(s + 40, 0).setType(Material.ENDER_CHEST);
            index.rereadChunk(at(s + 10, 0).getChunk());
            index.rereadChunk(at(s + 40, 0).getChunk());
        }, 1, () -> {
            Block near = index.nearestEnderChest(at(s, 0).getLocation());
            return near != null && near.getX() == s + 10 ? null : "nearest " + near + " of " + index.enderChestCount();
        }));
        steps.add(new Step("chunk unload refreshes a container's last-seen contents", () -> {
            container(far + 100, 0, Material.BARREL, A, item(Material.IRON_INGOT, 1));
            ((Barrel) at(far + 100, 0).getState(false)).getInventory().addItem(item(Material.IRON_INGOT, 6));   // no event
        }, 80, () -> {
            // Nobody is near, so the server may already have unloaded it by itself (our unload handler ran then).
            boolean already = has(A, at(far + 100, 0), Material.IRON_INGOT, 7) == null;
            boolean unloaded = already || w.unloadChunk((far + 100) >> 4, 0, true);
            return all(unloaded ? null : "couldn't unload (test can't tell)", has(A, at(far + 100, 0), Material.IRON_INGOT, 7),
                already ? "INFO the server had unloaded it by itself already" : null);
        }));
        int big = 200;
        steps.add(new Step("summary: thousands of distinct items split into pages under the 1 MiB payload limit", () -> {
            for (int c = 0; c < 120; c++) {
                Block b = at(big + (c % 60), c < 60 ? 6 : 8);
                b.setType(Material.BARREL);
                Inventory inv = ((Barrel) b.getState(false)).getInventory();
                for (int i = 0; i < 27; i++) {
                    ItemStack it = item(Material.PAPER, 1);
                    it.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Note " + c + "-" + i + " " + "x".repeat(120)));
                    inv.setItem(i, it);
                }
                Containers.setOwner(b, B);
                index.refresh(b);
            }
        }, 1, () -> {
            Search.Session ses = session(B, at(s, 0), Set.of());
            List<Protocol.SummaryItem> items = ib.search().summary(ses);
            List<byte[]> pages = Protocol.summary(1, new Protocol.Beacon("minecraft:overworld", 0, 0, 0), items);
            int n = 0, biggest = 0;
            for (byte[] p : pages) { n += Protocol.readSummary(p).items().size(); biggest = Math.max(biggest, p.length); }
            return all(items.size() >= 3240 ? null : "only " + items.size() + " items", pages.size() >= 2 ? null : "one page of " + biggest + " bytes",
                biggest < 1024 * 1024 ? null : "a page of " + biggest + " bytes", n == items.size() ? null : "pages hold " + n + " of " + items.size());
        }));
        steps.add(new Step("saving: the index written to disk reads back the same", () -> index.save(), 1, () -> {
            Index again = new Index(ib.getDataFolder().toPath(), getLogger());
            again.loadAll();
            return all(again.of(A).equals(index.of(A)) ? null : "A's list differs after loading", again.of(B).equals(index.of(B)) ? null : "B's list differs",
                again.enderChestCount() == index.enderChestCount() ? null : "ender chests " + again.enderChestCount());
        }));
        int d = 290;
        UUID demo = new UUID(0xD, 4);
        steps.add(new Step("demo: a beacon and five containers holding 126 iron (loose, in a shulker box, in a bundle, buried, a minecart)", () -> { }, 1, () -> {
            String problem = Demo.build(at(d, 0).getLocation(), BlockFace.EAST, demo, beacons, index);
            if (problem != null) return "refused: " + problem;
            Block beacon = at(d + 3, 0);
            Search.Session ses = session(demo, beacon, ib.search().readNear(demo, beacon));
            int iron = itemNumber(ses, Material.IRON_INGOT);
            List<Protocol.Place> places = iron < 0 ? List.of() : ib.search().places(ses, iron);
            return all(beacons.is(beacon) ? null : "no beacon", total(demo, Material.IRON_INGOT) == 126 ? null : "iron " + total(demo, Material.IRON_INGOT),
                places.size() == 5 ? null : "places " + places.size(), places.stream().allMatch(Protocol.Place::live) ? null : "not all live: " + places,
                Demo.build(at(d, 0).getLocation(), BlockFace.EAST, demo, beacons, index) != null ? null : "built a second demo over the first");
        }));
        steps.add(new Step("particles: the loaded beacons are listed and a particle tick runs (nobody near: nothing spawned)", () -> { }, 1, () -> {
            List<Block> loaded = beacons.loaded(w);
            new Particles(beacons).tick(List.of(w));
            return loaded.stream().anyMatch(b -> b.equals(at(d + 3, 0))) ? null : "the demo beacon isn't in " + loaded.size() + " loaded beacons";
        }));
        steps.add(new Step("sorting: the given order, partial stacks merged (a renamed stack apart), empties last, nothing lost", () -> { }, 1, () -> {
            ItemStack named = item(Material.COBBLESTONE, 5);
            named.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Base stone"));
            ItemStack sword = item(Material.DIAMOND_SWORD, 1);
            sword.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, 3);
            ItemStack[] in = {item(Material.COBBLESTONE, 10), item(Material.DIRT, 5), null, item(Material.COBBLESTONE, 60), sword, named, item(Material.COBBLESTONE, 30)};
            ItemStack[] out = Sorter.merge(in);
            List<String> got = new ArrayList<>();
            for (ItemStack i : out) got.add(i == null ? "-" : i.getType().getKey().getKey() + "x" + i.getAmount() + (i.hasData(DataComponentTypes.CUSTOM_NAME) ? "(named)" : ""));
            List<String> want = List.of("cobblestonex64", "dirtx5", "cobblestonex36", "diamond_swordx1", "cobblestonex5(named)", "-", "-");
            return all(got.equals(want) ? null : "got " + got, out[3] != null && out[3].getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS) == 3 ? null : "the sword lost its enchantment");
        }));
        steps.add(new Step("sorting: only real storage containers (not a plugin menu, not a donkey) and only whole permutations", () -> { }, 1, () -> {
            Block chest = container(o + 20, 0, Material.CHEST, A, item(Material.STONE, 1));
            Inventory menu = Bukkit.createInventory(null, 27);
            List<Integer> perm = new ArrayList<>();
            for (int i = 0; i < 27; i++) perm.add(26 - i);
            List<Integer> twice = new ArrayList<>(perm);
            twice.set(0, 0);
            return all(Sorter.SORTABLE.contains(((Chest) chest.getState(false)).getInventory().getType()) ? null : "a chest isn't sortable",
                ((Chest) chest.getState(false)).getInventory().getHolder(false) instanceof BlockInventoryHolder ? null : "a chest's holder isn't a block holder",
                menu.getHolder(false) == null ? null : "a plugin menu has a holder",
                Sorter.isPermutation(perm, perm.stream().sorted().toList()) ? null : "a reversed order was refused",
                Sorter.isPermutation(twice, perm.stream().sorted().toList()) ? "a slot listed twice was accepted" : null);
        }));
        steps.add(new Step("protocol: a chunk answer lists the chunk's beacons", () -> { }, 1, () -> {
            Block b = at(d + 3, 0);
            Chunk c = b.getChunk();
            BlockSet set = beacons.chunk(w, c.getX(), c.getZ());
            Protocol.Chunk read = Protocol.readChunk(Protocol.chunk(new Protocol.ChunkPos(c.getX(), c.getZ()), set));
            return read.beacons().contains(BlockSet.pack(b.getX() & 15, b.getY(), b.getZ() & 15)) ? null : "beacon missing from " + read.beacons();
        }));

        String only = System.getProperty("itembeacon.selftest.only", "");
        if (!only.isBlank()) {
            List<String> prefixes = List.of(only.split(","));
            steps.removeIf(st -> prefixes.stream().noneMatch(st.name()::startsWith));
            report.add("INFO only steps starting with " + prefixes + " (" + steps.size() + " steps)");
        }
        run(0);
    }

    void run(int i) {
        if (i >= steps.size()) { finish(); return; }
        Step s = steps.get(i);
        try { s.action().run(); }
        catch (RuntimeException ex) { record(s.name(), "action threw " + ex); run(i + 1); return; }
        getServer().getScheduler().runTaskLater(this, () -> {
            String problem;
            try { problem = s.check().get(); } catch (RuntimeException ex) { problem = "check threw " + ex; }
            record(s.name(), problem);
            run(i + 1);
        }, Math.max(1, s.ticks()));
    }

    void record(String name, String problem) {
        if (problem == null) report.add("PASS " + name);
        else if (problem.startsWith("INFO")) report.add("PASS " + name + " (" + problem + ")");
        else { failures++; report.add("FAIL " + name + ": " + problem); }
    }

    void finish() {
        report.add(failures == 0 ? "SELFTEST PASSED on " + getServer().getVersion() : "SELFTEST FAILED (" + failures + ")");
        try { getDataFolder().mkdirs(); Files.write(getDataFolder().toPath().resolve("report.txt"), report); }
        catch (IOException e) { getLogger().severe("Could not write report: " + e); }
        getLogger().info(String.join("\n", report));
        if (Boolean.getBoolean("itembeacon.selftest.exit")) getServer().shutdown();
    }
}
