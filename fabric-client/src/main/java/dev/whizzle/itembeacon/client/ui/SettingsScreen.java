package dev.whizzle.itembeacon.client.ui;

import dev.whizzle.itembeacon.client.ItemBeaconClient;
import dev.whizzle.itembeacon.client.ToolSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;

/** {@code /itembeaconmod settings}: where the search bar and sort buttons go. Saved on Done. */
public final class SettingsScreen extends Screen {
    private final Screen parent;
    private final ToolSettings edit = new ToolSettings();

    public SettingsScreen(Screen parent) {
        super(Component.literal("Item Beacon settings"));
        this.parent = parent;
        edit.container = ItemBeaconClient.SETTINGS.container;
        edit.inventory = ItemBeaconClient.SETTINGS.inventory;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110, y = height / 2 - 40;
        addRenderableWidget(Button.builder(containerLabel(), b -> { edit.container = ToolSettings.next(edit.container); b.setMessage(containerLabel()); })
            .bounds(x, y, 220, 20)
            .tooltip(Tooltip.create(Component.literal("Right or Left of the panel, Top (a row above it), or the sort buttons in the container's title row (search bar above).")))
            .build());
        addRenderableWidget(Button.builder(inventoryLabel(), b -> { edit.inventory = ToolSettings.next(edit.inventory); b.setMessage(inventoryLabel()); })
            .bounds(x, y + 26, 220, 20)
            .tooltip(Tooltip.create(Component.literal("Right or Left of your inventory rows, Bottom (below the panel), or in the Inventory label row.")))
            .build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + 60, y + 62, 100, 20).build());
    }

    private Component containerLabel() { return Component.literal("Search + container buttons: " + edit.container.label); }
    private Component inventoryLabel() { return Component.literal("Your inventory buttons: " + edit.inventory.label); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.centeredText(font, title, width / 2, height / 2 - 66, 0xFFFFFFFF);
        g.centeredText(font, Component.literal("A spot that doesn't fit (a long title, no room) falls back to another."), width / 2, height / 2 + 50, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        ItemBeaconClient.SETTINGS = edit;
        try { edit.save(ItemBeaconClient.SETTINGS_FILE); }
        catch (IOException e) { ItemBeaconClient.LOG.warn("Couldn't save {}: {}", ItemBeaconClient.SETTINGS_FILE, e.toString()); }
        minecraft.gui.setScreen(parent);
    }
}
