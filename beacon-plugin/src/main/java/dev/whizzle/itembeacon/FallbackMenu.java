package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.ItemKey;
import dev.whizzle.itembeacon.model.Protocol.Place;
import dev.whizzle.itembeacon.model.Protocol.SummaryItem;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.*;

/**
 * The beacon without the mod: a chest-style menu of everything the player owns (most first, 45 per page, totals in
 * the tooltip). Clicking an item closes the menu, outlines the containers holding it (glowing, through walls, only
 * for that player) and lists them in chat. The bottom row: previous page, clear waypoints, next page.
 */
public final class FallbackMenu implements Listener {
    static final int PER_PAGE = 45, PREVIOUS = 45, CLEAR = 49, NEXT = 53;
    /** Chat lines per pick (the nearest). */
    static final int CHAT_LINES = 8;

    static final class Menu implements InventoryHolder {
        final Search.Session session;
        final List<SummaryItem> items;
        final int page;
        Inventory inventory;
        Menu(Search.Session session, List<SummaryItem> items, int page) { this.session = session; this.items = items; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
        int pages() { return Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE); }
    }

    private final Search search;
    private final Waypoints waypoints;

    public FallbackMenu(Search search, Waypoints waypoints) {
        this.search = search;
        this.waypoints = waypoints;
    }

    public void open(Player p, Search.Session s) { open(p, new Menu(s, search.summary(s), 0)); }

    void open(Player p, Menu m) {
        Inventory inv = Bukkit.createInventory(m, 54, Component.text("Item Beacon" + (m.pages() > 1 ? " (" + (m.page + 1) + "/" + m.pages() + ")" : "")));
        m.inventory = inv;
        int from = m.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && from + i < m.items.size(); i++) inv.setItem(i, icon(m.items.get(from + i)));
        if (m.items.isEmpty()) inv.setItem(22, button(Material.PAPER, "Nothing here yet",
            "Containers you place, or open for the first time, are yours.", "Their contents show up here."));
        if (m.page > 0) inv.setItem(PREVIOUS, button(Material.ARROW, "Previous page"));
        if (m.page < m.pages() - 1) inv.setItem(NEXT, button(Material.ARROW, "Next page"));
        inv.setItem(CLEAR, button(Material.BARRIER, "Clear waypoints"));
        p.openInventory(inv);
    }

    static String number(long n) { return NumberFormat.getIntegerInstance(Locale.ROOT).format(n); }

    /** The item itself as the icon (it may not read on this server, then paper), totals in the tooltip. */
    static ItemStack icon(SummaryItem s) {
        ItemStack icon = item(s.item());
        icon.setAmount(1);
        List<Component> lore = new ArrayList<>();
        lore.add(grey("Total " + number(s.total()) + " in " + s.containers() + (s.containers() == 1 ? " container" : " containers")));
        if (s.onlyLastSeen()) lore.add(grey("Last seen (not read just now)"));
        lore.add(Component.text("Click for waypoints", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        icon.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return icon;
    }

    static ItemStack item(ItemKey key) {
        try { return ItemStack.deserializeBytes(key.bytes()); }
        catch (RuntimeException e) { return new ItemStack(Material.PAPER); }
    }

    static Component grey(String s) { return Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false); }

    static ItemStack button(Material m, String name, String... lines) {
        ItemStack b = new ItemStack(m);
        b.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        if (lines.length > 0) b.setData(DataComponentTypes.LORE, ItemLore.lore(Arrays.stream(lines).map(FallbackMenu::grey).toList()));
        return b;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getSlot();
        if (slot == PREVIOUS && m.page > 0) open(p, new Menu(m.session, m.items, m.page - 1));
        else if (slot == NEXT && m.page < m.pages() - 1) open(p, new Menu(m.session, m.items, m.page + 1));
        else if (slot == CLEAR) {
            waypoints.clear(p);
            p.sendMessage(Component.text("Waypoints cleared.", NamedTextColor.GRAY));
        } else if (slot < PER_PAGE) {
            int i = m.page * PER_PAGE + slot;
            if (i >= m.items.size()) return;
            p.closeInventory();
            pick(p, m.session, m.items.get(i));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
    }

    void pick(Player p, Search.Session s, SummaryItem item) {
        if (!search.usable(p, s)) {
            p.sendMessage(Component.text("Stand at the Item Beacon to search.", NamedTextColor.RED));
            return;
        }
        List<Place> places = search.places(s, item.id());
        waypoints.show(p, places, true);
        Component name = item(item.item()).effectiveName();
        p.sendMessage(Component.text("", NamedTextColor.AQUA).append(name)
            .append(Component.text(": " + number(item.total()) + " in " + places.size() + (places.size() == 1 ? " place" : " places")
                + ". Outlines for " + waypoints.lifetimeMs() / 60000 + " min, or until you open one.", NamedTextColor.AQUA)));
        int shown = 0;
        for (Place pl : places) {
            if (shown++ == CHAT_LINES) { p.sendMessage(grey("  ... and " + (places.size() - CHAT_LINES) + " more")); break; }
            p.sendMessage(Component.text("  " + line(p, pl), pl.live() ? NamedTextColor.WHITE : NamedTextColor.GRAY));
        }
    }

    /** "64 in Chest at 120 64 -40, 12 blocks away, 9 below you, in Shulker Box". */
    static String line(Player p, Place pl) {
        StringBuilder sb = new StringBuilder(number(pl.count()));
        if (pl.id().equals(Index.ENDER)) {
            sb.append(" in your Ender Chest");
            if (pl.sameDimension()) sb.append(" (nearest ender chest ").append(pl.x()).append(' ').append(pl.y()).append(' ').append(pl.z()).append(')');
        } else {
            sb.append(" in ").append(kind(pl.kind())).append(" at ").append(pl.x()).append(' ').append(pl.y()).append(' ').append(pl.z());
            if (!pl.sameDimension()) sb.append(", ").append(dimension(pl.dimension()));
            else {
                var l = p.getLocation();
                long d = Math.round(Math.sqrt(Math.pow(pl.x() + 0.5 - l.getX(), 2) + Math.pow(pl.y() + 0.5 - l.getY(), 2) + Math.pow(pl.z() + 0.5 - l.getZ(), 2)));
                sb.append(", ").append(d).append(d == 1 ? " block" : " blocks").append(" away");
                int dy = pl.y() - l.getBlockY();
                if (dy <= -3) sb.append(", ").append(-dy).append(" below you");
                else if (dy >= 3) sb.append(", ").append(dy).append(" above you");
            }
        }
        List<String> in = Arrays.stream(pl.inside().split("; ", -1)).filter(s -> !s.isEmpty()).toList();
        if (!in.isEmpty()) sb.append(", in ").append(String.join(" / ", in));
        if (!pl.live()) sb.append(" (last seen ").append(ago(System.currentTimeMillis() - pl.lastSeen())).append(')');
        return sb.toString();
    }

    static String kind(String kind) {
        StringBuilder sb = new StringBuilder();
        for (String w : kind.split("_")) {
            if (w.isEmpty()) continue;
            sb.append(sb.isEmpty() ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : " " + w);
        }
        return sb.toString();
    }

    /** "in the Nether" for {@code minecraft:the_nether}. */
    public static String dimension(String key) {
        return switch (key) {
            case "minecraft:overworld" -> "in the Overworld";
            case "minecraft:the_nether" -> "in the Nether";
            case "minecraft:the_end" -> "in the End";
            default -> "in " + key.substring(key.indexOf(':') + 1).replace('_', ' ');
        };
    }

    static String ago(long ms) {
        long min = Math.max(0, ms) / 60000;
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 48) return h + " h ago";
        return h / 24 + " days ago";
    }
}
