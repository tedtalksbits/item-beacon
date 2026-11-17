package dev.whizzle.itembeacon.client.ui;

import com.mojang.serialization.Dynamic;
import dev.whizzle.itembeacon.client.ItemBeaconClient;
import dev.whizzle.itembeacon.model.ItemKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Turning the server's item bytes into stacks, and sorting items into the screen's filters.
 * <p>
 * The bytes are Paper's {@code ItemStack#serializeAsBytes}: compressed NBT of {@code ItemStack.CODEC} with a
 * {@code DataVersion} (read from the Paper 26.3 jar: {@code CraftItemStack.serializeAsBytes} ->
 * {@code MCUtil.serializeTagToBytes}). Read back the same way, through the data fixer if the versions differ.
 */
public final class Items {
    private Items() {}

    private static int currentVersion() { return NbtUtils.getDataVersion(NbtUtils.addCurrentDataVersion(new CompoundTag()), 0); }

    /** The item, or a barrier if this client can't read it. */
    public static ItemStack decode(ItemKey key, HolderLookup.Provider registries) {
        try {
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(key.bytes()), NbtAccounter.unlimitedHeap());
            int version = NbtUtils.getDataVersion(tag, -1);
            tag.remove("DataVersion");
            int current = currentVersion();
            if (version >= 0 && version != current)
                tag = (CompoundTag) DataFixers.getDataFixer().update(References.ITEM_STACK, new Dynamic<>(NbtOps.INSTANCE, tag), version, current).getValue();
            return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result().orElseGet(Items::unreadable);
        } catch (Exception e) {
            ItemBeaconClient.LOG.warn("Couldn't read an item from the server: {}", e.toString());
            return unreadable();
        }
    }

    static ItemStack unreadable() { return new ItemStack(net.minecraft.world.item.Items.BARRIER); }

    // ---- filters ----

    public enum Filter {
        ALL("All"), BLOCKS("Blocks"), TOOLS("Tools"), FOOD("Food"), REDSTONE("Redstone"), INGREDIENTS("Ingredients");
        public final String label;
        Filter(String label) { this.label = label; }
    }

    /** Creative tab -> filter, by the tab's id. */
    static final Map<String, Filter> TABS = Map.of(
        "minecraft:building_blocks", Filter.BLOCKS, "minecraft:colored_blocks", Filter.BLOCKS, "minecraft:natural_blocks", Filter.BLOCKS,
        "minecraft:functional_blocks", Filter.BLOCKS, "minecraft:redstone_blocks", Filter.REDSTONE, "minecraft:tools_and_utilities", Filter.TOOLS,
        "minecraft:combat", Filter.TOOLS, "minecraft:food_and_drinks", Filter.FOOD, "minecraft:ingredients", Filter.INGREDIENTS);

    /**
     * Which filters each item type belongs to, from the creative tabs (an item can be in more than one: redstone
     * blocks are blocks too). Builds the tabs' contents if the creative screen hasn't yet.
     */
    public static Map<Item, EnumSet<Filter>> filters(FeatureFlagSet features, HolderLookup.Provider registries) {
        Map<Item, EnumSet<Filter>> out = new HashMap<>();
        try {
            CreativeModeTabs.tryRebuildTabContents(features, false, registries);
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                var id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
                Filter f = id == null ? null : TABS.get(id.toString());
                if (f == null) continue;
                for (ItemStack s : tab.getDisplayItems()) out.computeIfAbsent(s.getItem(), i -> EnumSet.noneOf(Filter.class)).add(f);
            }
        } catch (RuntimeException e) {
            ItemBeaconClient.LOG.warn("Couldn't read the creative tabs for the filters: {}", e.toString());
        }
        return out;
    }
}
