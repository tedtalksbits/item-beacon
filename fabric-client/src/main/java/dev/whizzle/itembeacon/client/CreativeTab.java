package dev.whizzle.itembeacon.client;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

import java.util.List;

/**
 * An "Item Beacon" creative tab holding the beacon, so creative search finds it. The stack is built with exactly the
 * components the plugin gives a crafted one ({@code BeaconItem.create}): a lodestone with
 * {@code PublicBukkitValues {itembeacon:beacon: 1b}}, the item name and the model string. The self-test checks the two
 * match ("creative tab").
 */
public final class CreativeTab {
    private CreativeTab() {}

    public static final String NAME = "Item Beacon", MODEL = "itembeacon:item_beacon";

    public static ItemStack stack() {
        ItemStack s = new ItemStack(Items.LODESTONE);
        CompoundTag values = new CompoundTag();
        values.putByte("itembeacon:beacon", (byte) 1);
        CompoundTag root = new CompoundTag();
        root.put("PublicBukkitValues", values);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        s.set(DataComponents.ITEM_NAME, Component.literal(NAME));
        s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(MODEL), List.of()));
        return s;
    }

    public static void register() {
        CreativeModeTab tab = FabricCreativeModeTab.builder()
            .title(Component.literal(NAME))
            .icon(CreativeTab::stack)
            .displayItems((params, output) -> output.accept(stack()))
            .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath("itembeacon", "item_beacon"), tab);
    }
}
