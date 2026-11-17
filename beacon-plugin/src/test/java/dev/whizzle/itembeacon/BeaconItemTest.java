package dev.whizzle.itembeacon;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BeaconItemTest {
    @BeforeAll static void start() { MockBukkit.mock(); }
    @AfterAll static void stop() { MockBukkit.unmock(); }

    @Test void beaconIsANamedLodestoneWithModelString() {
        ItemStack i = BeaconItem.create(1);
        assertEquals(Material.LODESTONE, i.getType());
        assertTrue(BeaconItem.is(i));
        assertEquals(List.of(BeaconItem.MODEL), i.getData(DataComponentTypes.CUSTOM_MODEL_DATA).strings());
        assertFalse(BeaconItem.is(new ItemStack(Material.LODESTONE)));
        assertFalse(BeaconItem.is(null));
    }

    @Test void renamedBeaconStaysABeacon() {
        ItemStack i = BeaconItem.create(1);
        i.setData(DataComponentTypes.CUSTOM_NAME, Component.text("Base"));
        assertTrue(BeaconItem.is(i));
    }

    @Test void recipeIsAColumnTakingOnlyAPlainLodestone() {
        var r = Recipes.recipe();
        assertArrayEquals(new String[] {"C", "L", "E"}, r.getShape());
        assertTrue(r.getChoiceMap().get('C').test(new ItemStack(Material.COMPASS)));
        assertTrue(r.getChoiceMap().get('L').test(new ItemStack(Material.LODESTONE)));
        assertFalse(r.getChoiceMap().get('L').test(BeaconItem.create(1)));
        assertTrue(r.getChoiceMap().get('E').test(new ItemStack(Material.ENDER_EYE)));
        assertTrue(BeaconItem.is(r.getResult()));
    }
}
