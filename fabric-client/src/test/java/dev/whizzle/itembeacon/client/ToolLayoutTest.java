package dev.whizzle.itembeacon.client;

import dev.whizzle.itembeacon.client.ToolLayout.Rect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Where the search bar and sort rows go, and the fallbacks when a spot doesn't fit. */
class ToolLayoutTest {
    /** A shulker box screen (176 x 166) centred in a 427 x 240 GUI: panel at 125, 37. */
    static ToolLayout.Input shulker(int screenW, int screenH, int top, int titleEnd) {
        return new ToolLayout.Input(screenW, screenH, (screenW - 176) / 2, top, 176, 166, true, 18, titleEnd, 6, true, 84, 53, 72);
    }

    static ToolSettings settings(ToolSettings.Container c, ToolSettings.Inventory i) {
        ToolSettings s = new ToolSettings();
        s.container = c;
        s.inventory = i;
        return s;
    }

    @Test void rightIsBesideThePanel() {
        var l = ToolLayout.compute(shulker(427, 240, 37, 68), new ToolSettings());
        assertEquals(new Rect(305, 37, 92, 14), l.search());
        assertEquals(new Rect(305, 54, 92, 14), l.containerRow());
        assertEquals(new Rect(305, 120, 92, 14), l.playerRow());
    }

    @Test void titleRowsInsideThePanelWithSearchAbove() {
        var l = ToolLayout.compute(shulker(427, 240, 37, 68), settings(ToolSettings.Container.TITLE, ToolSettings.Inventory.TITLE));
        assertEquals(new Rect(209, 21, 92, 14), l.search());
        assertEquals(new Rect(202, 41, 92, 11), l.containerRow());
        assertEquals(new Rect(202, 107, 92, 11), l.playerRow());
    }

    @Test void aLongTitleMovesTheContainerRowToTheTop() {
        var l = ToolLayout.compute(shulker(427, 240, 37, 150), settings(ToolSettings.Container.TITLE, ToolSettings.Inventory.RIGHT));
        assertEquals(new Rect(125, 21, 80, 14), l.search());
        assertEquals(new Rect(209, 21, 92, 14), l.containerRow());
    }

    @Test void noRoomAboveFallsBackToTheRight() {
        for (var c : new ToolSettings.Container[] {ToolSettings.Container.TOP, ToolSettings.Container.TITLE}) {
            var l = ToolLayout.compute(shulker(427, 200, 10, 68), settings(c, ToolSettings.Inventory.RIGHT));
            assertEquals(305, l.search().x(), c.name());
            assertEquals(10, l.search().y(), c.name());
        }
    }

    @Test void bottomBelowThePanelOrRightWithoutRoom() {
        assertEquals(new Rect(209, 205, 92, 14), ToolLayout.compute(shulker(427, 240, 37, 68), settings(ToolSettings.Container.RIGHT, ToolSettings.Inventory.BOTTOM)).playerRow());
        assertEquals(305, ToolLayout.compute(shulker(427, 215, 37, 68), settings(ToolSettings.Container.RIGHT, ToolSettings.Inventory.BOTTOM)).playerRow().x());
    }

    /** The recipe book pushes the panel right; then there's no room on the right. */
    @Test void noRoomOnTheRightGoesLeft() {
        var in = new ToolLayout.Input(400, 240, 200, 37, 176, 166, true, 18, 68, 6, true, 84, 53, 72);
        var l = ToolLayout.compute(in, new ToolSettings());
        assertEquals(200 - 4 - 92, l.search().x());
        assertEquals(200 - 4 - 92, l.playerRow().x());
    }

    @Test void leftAndRightAreIndependent() {
        var l = ToolLayout.compute(shulker(427, 240, 37, 68), settings(ToolSettings.Container.RIGHT, ToolSettings.Inventory.LEFT));
        assertEquals(305, l.search().x());
        assertEquals(29, l.playerRow().x());
    }

    @Test void theInventoryRowNeverSitsOnTheContainerRow() {
        var in = new ToolLayout.Input(427, 240, 125, 37, 176, 166, true, 18, 68, 6, true, 20, 53, 72);
        var l = ToolLayout.compute(in, new ToolSettings());
        assertTrue(l.playerRow().y() >= l.containerRow().y() + l.containerRow().h() + 2, l.toString());
    }

    @Test void withoutButtonsOnlyTheSearchBar() {
        var in = new ToolLayout.Input(427, 240, 125, 37, 176, 166, false, -1, 68, 6, false, -1, 53, 72);
        var l = ToolLayout.compute(in, settings(ToolSettings.Container.TOP, ToolSettings.Inventory.BOTTOM));
        assertEquals(new Rect(209, 21, 92, 14), l.search());
        assertNull(l.containerRow());
        assertNull(l.playerRow());
    }

    @Test void settingsSaveLoadAndCycle(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("config/itembeacon.properties");
        assertEquals(ToolSettings.Container.RIGHT, ToolSettings.load(f).container);
        ToolSettings s = settings(ToolSettings.Container.TITLE, ToolSettings.Inventory.BOTTOM);
        s.save(f);
        ToolSettings back = ToolSettings.load(f);
        assertEquals(ToolSettings.Container.TITLE, back.container);
        assertEquals(ToolSettings.Inventory.BOTTOM, back.inventory);
        Files.writeString(f, "container=sideways\n");
        assertEquals(ToolSettings.Container.RIGHT, ToolSettings.load(f).container);   // nonsense: default
        assertEquals(ToolSettings.Container.RIGHT, ToolSettings.next(ToolSettings.Container.TITLE));
    }
}
