package dev.whizzle.itembeacon.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.whizzle.itembeacon.client.Format;
import dev.whizzle.itembeacon.client.ItemBeaconClient;
import dev.whizzle.itembeacon.client.SummaryAssembler;
import dev.whizzle.itembeacon.client.Waypoints;
import dev.whizzle.itembeacon.client.net.Net;
import dev.whizzle.itembeacon.model.Protocol;
import dev.whizzle.itembeacon.model.Protocol.Place;
import dev.whizzle.itembeacon.model.Protocol.SummaryItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * The beacon's screen (mockup {@code item-beacon-picker.png}). Left: everything the player owns, with totals, most
 * first or A-Z, narrowed by the search box and a filter (creative tab groups). Click an item: the right side lists
 * the containers holding it (count, distance, "below you", "last seen", other dimensions), and "Show waypoints" /
 * "Nearest only" set the waypoints (the server outlines them until slice 3 draws beams and markers).
 */
public final class PickerScreen extends Screen {
    static final int SLOT = 20, COLS = 9, ROW = 24;
    static final int PANEL = 0xFFC6C6C6, EDGE_LIGHT = 0xFFFFFFFF, EDGE_DARK = 0xFF555555, TEXT = 0xFF3F3F3F, GREY = 0xFF6B6B6B,
        SLOT_BG = 0xFF8B8B8B, SLOT_DARK = 0xFF373737, SLOT_HOVER = 0xFFA8A8A8, SLOT_STALE = 0xFF7A7A7A, PICKED = 0xFFFFE04A,
        RIGHT_BG = 0xFFAAAAAA, ROW_BG = 0xFFD2D2D2, ROW_STALE = 0xFFBDBDBD, NOTE = 0xFF2E6B2E;

    record Entry(SummaryItem item, ItemStack stack, String search, EnumSet<Items.Filter> filters) {}

    private final SummaryAssembler.Summary summary;
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> shown = List.of();
    private Items.Filter filter = Items.Filter.ALL;
    private boolean byName;
    private int gridScroll, listScroll;
    private Entry selected;
    /** The selected item's places; null while waiting for the server. */
    private List<Place> places;
    private String note = "";

    private int px, py, pw, ph, gridX, gridY, gridRows, rx, ry, rw, rh;
    private EditBox search;
    private final Map<Items.Filter, Button> chips = new EnumMap<>(Items.Filter.class);
    private Button sort, showAll, nearest, clear;

    public PickerScreen(SummaryAssembler.Summary summary) {
        super(Component.literal("Item Beacon"));
        this.summary = summary;
    }

    @Override
    protected void init() {
        if (all.isEmpty() && !summary.items().isEmpty()) {
            var level = minecraft.level;
            Map<Item, EnumSet<Items.Filter>> filters = Items.filters(level.enabledFeatures(), level.registryAccess());
            for (SummaryItem s : summary.items()) {
                ItemStack stack = Items.decode(s.item(), level.registryAccess());
                String search = (stack.getHoverName().getString() + " " + BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ')).toLowerCase(Locale.ROOT);
                EnumSet<Items.Filter> f = filters.getOrDefault(stack.getItem(), EnumSet.noneOf(Items.Filter.class));
                if (f.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) f = EnumSet.of(Items.Filter.BLOCKS);
                all.add(new Entry(s, stack, search, f));
            }
        }
        pw = Math.min(width - 16, 440);
        ph = Math.min(height - 16, 260);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        gridX = px + 8;
        gridY = py + 46;
        gridRows = Math.max(1, (ph - 46 - 30) / SLOT);
        rx = gridX + COLS * SLOT + 8;
        rw = px + pw - 8 - rx;
        ry = gridY - 2;
        rh = py + ph - 8 - ry;

        String before = search == null ? "" : search.getValue();
        search = new EditBox(font, gridX, py + 24, 118, 16, Component.literal("Search"));
        search.setHint(Component.literal("Search your items...").withStyle(ChatFormatting.GRAY));
        search.setMaxLength(50);
        search.setValue(before);
        search.setResponder(s -> { gridScroll = 0; refilter(); });
        addRenderableWidget(search);

        chips.clear();
        int x = gridX + 124;
        for (Items.Filter f : Items.Filter.values()) {
            int w = font.width(f.label) + 10;
            if (x + w > px + pw - 8) break;   // a narrow window: the last filters don't fit
            Button b = Button.builder(Component.literal(f.label), btn -> { filter = f; gridScroll = 0; refilter(); }).bounds(x, py + 24, w, 16).build();
            chips.put(f, addRenderableWidget(b));
            x += w + 3;
        }
        sort = addRenderableWidget(Button.builder(sortLabel(), b -> { byName = !byName; b.setMessage(sortLabel()); refilter(); })
            .bounds(px + pw - 8 - 82, py + 5, 82, 14).build());
        int bw = (rw - 12) / 2;
        showAll = addRenderableWidget(Button.builder(Component.literal("Show waypoints"), b -> show(false)).bounds(rx + 4, ry + rh - 42, bw, 18).build());
        nearest = addRenderableWidget(Button.builder(Component.literal("Nearest only"), b -> show(true)).bounds(rx + 8 + bw, ry + rh - 42, bw, 18).build());
        clear = addRenderableWidget(Button.builder(Component.literal("Clear waypoints"), b -> clearWaypoints()).bounds(rx + 4, ry + rh - 22, rw - 8, 18).build());
        setInitialFocus(search);
        refilter();
    }

    private Component sortLabel() { return Component.literal(byName ? "Sort: A-Z" : "Sort: most first"); }

    private void refilter() {
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (filter != Items.Filter.ALL && !e.filters().contains(filter)) continue;
            if (!q.isEmpty() && !e.search().contains(q)) continue;
            out.add(e);
        }
        if (byName) out.sort(Comparator.comparing(e -> e.stack().getHoverName().getString().toLowerCase(Locale.ROOT)));
        shown = out;
        for (var c : chips.entrySet()) c.getValue().active = c.getKey() != filter;
        updateButtons();
    }

    private List<Place> here() { return places == null ? List.of() : places.stream().filter(Place::sameDimension).toList(); }

    private void updateButtons() {
        int n = here().size();
        if (showAll != null) {
            showAll.active = selected != null && n > 0;
            showAll.setMessage(Component.literal(n > 0 ? "Show waypoints (" + n + ")" : "Show waypoints"));
            nearest.active = selected != null && n > 0;
            clear.active = !Waypoints.ids().isEmpty();
        }
    }

    // ---- server answers ----

    public void places(Protocol.Places p) {
        if (p.session() != summary.session() || selected == null || p.item() != selected.item().id()) return;
        places = p.places();
        listScroll = 0;
        updateButtons();
    }

    private void pick(Entry e) {
        selected = e;
        places = null;
        listScroll = 0;
        note = "";
        if (Net.canSend(Protocol.PICK)) Net.send(Protocol.PICK, Protocol.pick(summary.session(), e.item().id()));
        updateButtons();
    }

    private void show(boolean nearestOnly) {
        if (selected == null) return;
        List<Place> chosen = nearestOnly ? here().stream().limit(1).toList() : here();
        // Until slice 3 draws its own waypoints, the server outlines them (outlines = true).
        Net.send(Protocol.SHOW, Protocol.show(summary.session(), selected.item().id(), nearestOnly, true));
        Waypoints.show(chosen, ItemBeaconClient.waypointSeconds * 1000L);
        note = (chosen.size() == 1 ? "1 waypoint" : chosen.size() + " waypoints") + " for " + Math.max(1, ItemBeaconClient.waypointSeconds / 60) + " min.";
        updateButtons();
    }

    private void clearWaypoints() {
        Net.send(Protocol.CLEAR, Protocol.clear());
        Waypoints.clear();
        note = "Waypoints cleared.";
        updateButtons();
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractBackground(g, mouseX, mouseY, delta);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, EDGE_DARK);
        g.fill(px, py, px + pw, py + ph, EDGE_LIGHT);
        g.fill(px + 1, py + 1, px + pw, py + ph, EDGE_DARK);
        g.fill(px + 1, py + 1, px + pw - 1, py + ph - 1, PANEL);
        g.fill(rx - 1, ry - 1, rx + rw + 1, ry + rh + 1, EDGE_DARK);
        g.fill(rx, ry, rx + rw, ry + rh, RIGHT_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.text(font, title, px + 8, py + 8, TEXT, false);
        String sub = "Read just now within " + ItemBeaconClient.radius + " blocks; the rest as last seen";
        g.text(font, font.plainSubstrByWidth(sub, px + pw - 8 - 86 - (px + 8 + font.width(title) + 8)), px + 8 + font.width(title) + 8, py + 8, GREY, false);
        drawGrid(g, mouseX, mouseY);
        drawRight(g, mouseX, mouseY);
    }

    private int gridIndex(double mx, double my) {
        if (mx < gridX || my < gridY || mx >= gridX + COLS * SLOT || my >= gridY + gridRows * SLOT) return -1;
        int i = (gridScroll + (int) ((my - gridY) / SLOT)) * COLS + (int) ((mx - gridX) / SLOT);
        return i < shown.size() ? i : -1;
    }

    private void drawGrid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int hover = gridIndex(mouseX, mouseY);
        int first = gridScroll * COLS;
        for (int i = first; i < Math.min(shown.size(), first + gridRows * COLS); i++) {
            Entry e = shown.get(i);
            int x = gridX + ((i - first) % COLS) * SLOT, y = gridY + ((i - first) / COLS) * SLOT;
            boolean picked = e == selected;
            g.fill(x, y, x + SLOT - 1, y + SLOT - 1, picked ? PICKED : SLOT_DARK);
            g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, picked ? PICKED : EDGE_LIGHT);
            g.fill(x + 1, y + 1, x + SLOT - 2, y + SLOT - 2, i == hover ? SLOT_HOVER : e.item().onlyLastSeen() ? SLOT_STALE : SLOT_BG);
            g.item(e.stack(), x + 2, y + 2);
            g.itemDecorations(font, e.stack(), x + 2, y + 2, Format.count(e.item().total()));
        }
        int below = gridY + gridRows * SLOT + 4;
        if (all.isEmpty()) {
            g.text(font, "Nothing here yet.", gridX, gridY + 4, TEXT, false);
            g.text(font, "Containers you place, or open first,", gridX, gridY + 16, GREY, false);
            g.text(font, "are yours; their items show up here.", gridX, gridY + 26, GREY, false);
        } else if (shown.isEmpty()) {
            g.text(font, "No item matches.", gridX, gridY + 4, GREY, false);
        }
        int rows = (shown.size() + COLS - 1) / COLS;
        String foot = shown.size() + (shown.size() == 1 ? " item" : " items") + (rows > gridRows ? "  (scroll for more)" : "") + ". Click one.";
        g.text(font, font.plainSubstrByWidth(foot, COLS * SLOT), gridX, Math.min(below, py + ph - 12), GREY, false);
        if (hover >= 0) {
            Entry e = shown.get(hover);
            List<Component> lines = new ArrayList<>();
            lines.add(e.stack().getHoverName());
            lines.add(Component.literal(String.format(Locale.ROOT, "%,d", e.item().total()) + " in " + e.item().containers()
                + (e.item().containers() == 1 ? " container" : " containers")).withStyle(ChatFormatting.GRAY));
            if (e.item().onlyLastSeen()) lines.add(Component.literal("Last seen (none read just now)").withStyle(ChatFormatting.DARK_GRAY));
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private int listTop() { return ry + 30; }
    private int listRows() { return Math.max(1, (ry + rh - 48 - listTop()) / ROW); }

    private void drawRight(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = rx + 4, w = rw - 8;
        if (selected == null) {
            g.text(font, "Click an item to see", x, ry + 6, TEXT, false);
            g.text(font, "where it is.", x, ry + 16, TEXT, false);
        } else {
            g.item(selected.stack(), x, ry + 5);
            g.text(font, font.plainSubstrByWidth(selected.stack().getHoverName().getString(), w - 22), x + 20, ry + 4, TEXT, false);
            String total = String.format(Locale.ROOT, "%,d", selected.item().total()) + " in " + selected.item().containers()
                + (selected.item().containers() == 1 ? " container" : " containers");
            g.text(font, font.plainSubstrByWidth(total, w - 22), x + 20, ry + 14, GREY, false);
            if (places == null) g.text(font, "Looking...", x, listTop() + 4, GREY, false);
            else if (places.isEmpty()) g.text(font, "Not found any more (moved or used?).", x, listTop() + 4, GREY, false);
            else drawPlaces(g, x, w);
        }
        if (!note.isEmpty()) g.text(font, font.plainSubstrByWidth(note, w), x, ry + rh - 54, NOTE, false);
    }

    private void drawPlaces(GuiGraphicsExtractor g, int x, int w) {
        var p = minecraft.player;
        int top = listTop(), rows = listRows();
        for (int i = listScroll; i < Math.min(places.size(), listScroll + rows); i++) {
            Place pl = places.get(i);
            int y = top + (i - listScroll) * ROW;
            boolean stale = !pl.live();
            g.fill(x, y, x + w, y + ROW - 2, EDGE_DARK);
            g.fill(x + 1, y + 1, x + w - 1, y + ROW - 3, stale ? ROW_STALE : ROW_BG);
            int textColour = stale ? GREY : TEXT;
            String count = "x" + pl.count();
            String name = pl.id().equals("ender") ? "Ender Chest" : Format.kind(pl.kind());
            List<String> in = Format.insides(pl.inside());
            if (!in.isEmpty()) name += " (in " + String.join(", ", in) + ")";
            g.text(font, font.plainSubstrByWidth(name, w - 10 - font.width(count)), x + 4, y + 3, textColour, false);
            g.text(font, count, x + w - 4 - font.width(count), y + 3, textColour, false);
            StringBuilder line = new StringBuilder();
            if (!pl.sameDimension()) line.append(pl.id().equals("ender") ? "no ender chest in this dimension" : Format.dimension(pl.dimension()));
            else if (p != null) {
                double dx = pl.x() + 0.5 - p.getX(), dy = pl.y() + 0.5 - p.getY(), dz = pl.z() + 0.5 - p.getZ();
                line.append(Format.distance(Math.sqrt(dx * dx + dy * dy + dz * dz)));
                int below = (int) Math.floor(p.getY()) - pl.y();
                if (below >= 3) line.append("  below you");
                else if (below <= -3) line.append("  above you");
                if (pl.id().equals("ender")) line.append("  nearest ender chest");
            }
            if (stale) line.append("  last seen ").append(Format.ago(System.currentTimeMillis() - pl.lastSeen()));
            g.text(font, font.plainSubstrByWidth(line.toString(), w - 8), x + 4, y + 12, GREY, false);
        }
        if (places.size() > rows)
            g.text(font, (listScroll + 1) + "-" + Math.min(places.size(), listScroll + rows) + " of " + places.size() + " (scroll)", x, top + rows * ROW, GREY, false);
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        // 26.x numbers mouse buttons like SDL (left 1, right 3): compare with the constants, never 0/1.
        if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) return false;
        int i = gridIndex(e.x(), e.y());
        if (i >= 0) { pick(shown.get(i)); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int step = sy > 0 ? -1 : sy < 0 ? 1 : 0;
        if (mx < rx) {
            int rows = (shown.size() + COLS - 1) / COLS;
            gridScroll = Math.max(0, Math.min(Math.max(0, rows - gridRows), gridScroll + step));
            return true;
        }
        if (places != null) {
            listScroll = Math.max(0, Math.min(Math.max(0, places.size() - listRows()), listScroll + step));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
