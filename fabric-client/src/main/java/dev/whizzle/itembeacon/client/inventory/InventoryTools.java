package dev.whizzle.itembeacon.client.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import dev.whizzle.itembeacon.client.ItemBeaconClient;
import dev.whizzle.itembeacon.client.SortPlan;
import dev.whizzle.itembeacon.client.ToolLayout;
import dev.whizzle.itembeacon.client.mixin.ContainerScreenAccessor;
import dev.whizzle.itembeacon.client.net.Net;
import dev.whizzle.itembeacon.client.ui.Items;
import dev.whizzle.itembeacon.model.Protocol;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.stream.Stream;

/**
 * A search bar and sort buttons beside every container screen (the creative inventory excepted: it has its own).
 * <ul>
 * <li>Search (works on any server, no plugin needed): typing dims every slot whose item doesn't match (name or id);
 * a shulker box or bundle holding a match stays lit with a gold frame.</li>
 * <li>Sort (needs plugin 0.2.0 or newer; the buttons only appear when the server takes {@code sort}): Type (creative menu order), A-Z, Count (biggest totals first), for storage containers
 * (chests of every kind, barrels, shulker boxes, ender chests, hoppers, dispensers, droppers) and for the player's main
 * 27 slots (never the hotbar, armour or off-hand). The mod works out the order; the plugin merges and lays it out.</li>
 * </ul>
 */
public final class InventoryTools {
    private InventoryTools() {}

    static final int COLUMN = ToolLayout.COLUMN, BUTTON = ToolLayout.BUTTON, ROW = ToolLayout.ROW;
    static final int DIM = 0xC0101010, FRAME = 0xFFFFC832;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> acs && !(screen instanceof CreativeModeInventoryScreen)) new Tools(mc, acs).attach();
        });
    }

    /** Storage menus whose non-player slots are one container the plugin can sort. */
    static boolean sortable(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu || menu instanceof HopperMenu || menu instanceof DispenserMenu;
    }

    static final class Tools {
        final Minecraft mc;
        final AbstractContainerScreen<?> screen;
        final ContainerScreenAccessor pos;
        EditBox search;
        /** "..." beside the search bar: shows the two placement choices at the top of the screen. */
        Button gear, containerSpot, inventorySpot;
        final List<Button> containerButtons = new ArrayList<>(), playerButtons = new ArrayList<>();
        int containerTop = -1, playerTop = -1;

        Tools(Minecraft mc, AbstractContainerScreen<?> screen) {
            this.mc = mc;
            this.screen = screen;
            this.pos = (ContainerScreenAccessor) screen;
        }

        void attach() {
            Inventory own = mc.player == null ? null : mc.player.getInventory();
            for (Slot s : screen.getMenu().slots) {
                if (playerMain(s, own)) playerTop = playerTop < 0 ? s.y : Math.min(playerTop, s.y);
                else if (s.container != own) containerTop = containerTop < 0 ? s.y : Math.min(containerTop, s.y);
            }
            var font = mc.font;
            search = new EditBox(font, 0, 0, COLUMN, ROW, Component.literal("Search"));
            search.setHint(Component.literal("Search...").withStyle(ChatFormatting.GRAY));
            search.setMaxLength(50);
            search.setTooltip(Tooltip.create(Component.literal("Dims what doesn't match. Shulker boxes and bundles holding a match get a gold frame.")));
            Screens.getWidgets(screen).add(search);
            gear = Button.builder(Component.literal("..."), b -> arrange(!containerSpot.visible)).bounds(0, 0, ROW, ROW)
                .tooltip(Tooltip.create(Component.literal("Move the search bar and sort buttons"))).build();
            Screens.getWidgets(screen).add(gear);
            containerSpot = Button.builder(containerLabel(), b -> {
                ItemBeaconClient.SETTINGS.container = dev.whizzle.itembeacon.client.ToolSettings.next(ItemBeaconClient.SETTINGS.container);
                ItemBeaconClient.saveSettings();
                b.setMessage(containerLabel());
            }).bounds(0, 0, 170, 16).build();
            inventorySpot = Button.builder(inventoryLabel(), b -> {
                ItemBeaconClient.SETTINGS.inventory = dev.whizzle.itembeacon.client.ToolSettings.next(ItemBeaconClient.SETTINGS.inventory);
                ItemBeaconClient.saveSettings();
                b.setMessage(inventoryLabel());
            }).bounds(0, 0, 170, 16).build();
            Screens.getWidgets(screen).add(containerSpot);
            Screens.getWidgets(screen).add(inventorySpot);
            arrange(false);
            if (ItemBeaconClient.active() && Net.canSend(Protocol.SORT)) {   // plugin 0.2.0 or newer
                if (sortable(screen.getMenu()) && containerTop >= 0) addRow(containerButtons, false);
                if (playerTop >= 0) addRow(playerButtons, true);
            }
            place();
            ScreenEvents.beforeExtract(screen).register((s, g, mx, my, d) -> place());   // the recipe book moves the panel
            ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> dim(g));
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, key) -> {
                if (!search.isFocused() || key.input() == InputConstants.KEY_ESCAPE) return true;
                search.keyPressed(key);   // typing goes to the search bar, not to the screen (E would close it, 1-9 swap slots)
                return false;
            });
        }

        static Component containerLabel() { return Component.literal("Search + container: " + ItemBeaconClient.SETTINGS.container.label); }
        static Component inventoryLabel() { return Component.literal("Inventory buttons: " + ItemBeaconClient.SETTINGS.inventory.label); }

        /** Shows or hides the placement choices (they apply at once, so you see where things go). */
        void arrange(boolean show) {
            containerSpot.visible = show;
            inventorySpot.visible = show && !playerButtons.isEmpty();
        }

        void addRow(List<Button> row, boolean player) {
            String what = player ? "your inventory (not the hotbar)" : "this container";
            String[][] labels = {{"Type", "Sort " + what + " by type, like the creative menu"}, {"A-Z", "Sort " + what + " by name"},
                {"Count", "Sort " + what + ": the items you have most of first"}};
            SortPlan.Mode[] modes = {SortPlan.Mode.TYPE, SortPlan.Mode.NAME, SortPlan.Mode.COUNT};
            for (int i = 0; i < 3; i++) {
                SortPlan.Mode mode = modes[i];
                Button b = Button.builder(Component.literal(labels[i][0]), btn -> sort(player, mode)).bounds(0, 0, BUTTON, ROW)
                    .tooltip(Tooltip.create(Component.literal(labels[i][1]))).build();
                row.add(b);
                Screens.getWidgets(screen).add(b);
            }
        }

        /** Where {@link ItemBeaconClient#SETTINGS} puts things ({@link ToolLayout}); every frame, as the recipe book moves the panel. */
        void place() {
            var font = mc.font;
            int titleEnd = screen.getTitle().getString().isEmpty() ? -1 : pos.itembeacon$titleX() + font.width(screen.getTitle());
            int labelEnd = pos.itembeacon$inventoryLabelX() + font.width(pos.itembeacon$inventoryLabel());
            var layout = ToolLayout.compute(new ToolLayout.Input(screen.width, screen.height, pos.itembeacon$left(), pos.itembeacon$top(),
                pos.itembeacon$width(), pos.itembeacon$height(), !containerButtons.isEmpty(), containerTop, titleEnd, pos.itembeacon$titleY(),
                !playerButtons.isEmpty(), playerTop, labelEnd, pos.itembeacon$inventoryLabelY()), ItemBeaconClient.SETTINGS);
            var r = layout.search();
            search.setX(r.x());
            search.setY(r.y());
            search.setWidth(r.w() - ROW - 2);
            gear.setX(r.x() + r.w() - ROW);
            gear.setY(r.y());
            // The placement choices: a fixed spot at the top centre, above anything the layout uses.
            int cx = screen.width / 2;
            containerSpot.setX(cx - 172);
            containerSpot.setY(2);
            inventorySpot.setX(cx + 2);
            inventorySpot.setY(2);
            row(containerButtons, layout.containerRow());
            row(playerButtons, layout.playerRow());
        }

        static void row(List<Button> buttons, ToolLayout.Rect r) {
            if (r == null) return;
            for (int i = 0; i < buttons.size(); i++) {
                Button b = buttons.get(i);
                b.setX(r.x() + i * (BUTTON + 1));
                b.setY(r.y());
                b.setHeight(r.h());
            }
        }

        void dim(GuiGraphicsExtractor g) {
            String q = search.getValue().trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) return;
            int left = pos.itembeacon$left(), top = pos.itembeacon$top();
            g.nextStratum();   // above the items
            for (Slot s : screen.getMenu().slots) {
                if (!s.isActive()) continue;
                ItemStack stack = s.getItem();
                int x = left + s.x, y = top + s.y;
                if (!stack.isEmpty() && matches(stack, q)) continue;
                if (!stack.isEmpty() && holdsMatch(stack, q, 0)) { g.outline(x - 1, y - 1, 18, 18, FRAME); continue; }
                g.fill(x, y, x + 16, y + 16, DIM);
            }
        }

        void sort(boolean player, SortPlan.Mode mode) {
            if (!ItemBeaconClient.active() || !Net.canSend(Protocol.SORT) || mc.player == null) return;
            Inventory own = mc.player.getInventory();
            List<Slot> slots = new ArrayList<>();
            for (Slot s : screen.getMenu().slots) if (player ? playerMain(s, own) : s.container != own) slots.add(s);
            slots.sort(Comparator.comparingInt(Slot::getContainerSlot));
            Map<Item, Integer> creative = Items.creativeOrder(mc);
            List<ItemStack> groups = new ArrayList<>();
            List<SortPlan.Slot> plan = new ArrayList<>();
            for (Slot s : slots) {
                ItemStack st = s.getItem();
                if (st.isEmpty()) { plan.add(new SortPlan.Slot(s.getContainerSlot(), true, 0, "", -1, 0)); continue; }
                int group = -1;
                for (int i = 0; i < groups.size() && group < 0; i++) if (ItemStack.isSameItemSameComponents(groups.get(i), st)) group = i;
                if (group < 0) { group = groups.size(); groups.add(st); }
                plan.add(new SortPlan.Slot(s.getContainerSlot(), false, creative.getOrDefault(st.getItem(), Integer.MAX_VALUE),
                    st.getHoverName().getString(), group, st.getCount()));
            }
            if (plan.isEmpty() || plan.size() > Protocol.MAX_SORT) return;
            Net.send(Protocol.SORT, Protocol.sort(player, SortPlan.order(plan, mode)));
        }
    }

    static boolean playerMain(Slot s, Inventory own) {
        return s.container == own && s.getContainerSlot() >= 9 && s.getContainerSlot() <= 35;
    }

    static boolean matches(ItemStack s, String q) {
        if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) return true;
        return BuiltInRegistries.ITEM.getKey(s.getItem()).getPath().replace('_', ' ').contains(q);
    }

    /** Whether a shulker box or bundle (or one inside it, up to 4 deep) holds a matching item. */
    static boolean holdsMatch(ItemStack s, String q, int depth) {
        if (depth >= 4) return false;
        var box = s.get(DataComponents.CONTAINER);
        var bundle = s.get(DataComponents.BUNDLE_CONTENTS);
        Stream<ItemStack> inside = Stream.concat(box == null ? Stream.empty() : box.itemCopies(), bundle == null ? Stream.empty() : bundle.itemCopies());
        return inside.anyMatch(i -> !i.isEmpty() && (matches(i, q) || holdsMatch(i, q, depth + 1)));
    }
}
