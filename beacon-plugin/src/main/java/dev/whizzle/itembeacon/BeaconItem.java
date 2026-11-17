package dev.whizzle.itembeacon;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * The Item Beacon item: a lodestone (the carrier) with {@code itembeacon:beacon} in its persistent data, the item
 * name "Item Beacon" and the custom model data string {@code itembeacon:item_beacon} that the mod's item definition
 * selects on. Players without the mod see a lodestone named Item Beacon. An anvil name doesn't remove it.
 */
public final class BeaconItem {
    private BeaconItem() {}

    public static final NamespacedKey KEY = new NamespacedKey("itembeacon", "beacon");
    public static final String NAME = "Item Beacon";
    public static final String MODEL = "itembeacon:item_beacon";
    public static final Material CARRIER = Material.LODESTONE;

    public static ItemStack create(int amount) {
        ItemStack item = new ItemStack(CARRIER, amount);
        item.editPersistentDataContainer(pdc -> pdc.set(KEY, PersistentDataType.BYTE, (byte) 1));
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(NAME));
        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, CustomModelData.customModelData().addString(MODEL).build());
        return item;
    }

    public static boolean is(ItemStack item) {
        return item != null && item.getType() == CARRIER && item.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
