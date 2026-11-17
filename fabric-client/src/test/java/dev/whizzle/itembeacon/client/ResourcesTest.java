package dev.whizzle.itembeacon.client;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** The mod's resources and source: mistakes that compile fine and only show up in the game. */
class ResourcesTest {
    static Path dir() { return Path.of(System.getProperty("itembeacon.client.dir", "")); }
    static Path res(String p) { return dir().resolve("src/main/resources").resolve(p); }

    @Test void beaconTexturesAre16x16AndOpaque() throws IOException {
        for (String f : List.of("item_beacon_side", "item_beacon_top")) {
            BufferedImage img = ImageIO.read(res("assets/itembeacon/textures/block/" + f + ".png").toFile());
            assertEquals(16, img.getWidth(), f);
            assertEquals(16, img.getHeight(), f);
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) assertEquals(0xFF, img.getRGB(x, y) >>> 24, f + " pixel " + x + "," + y + " isn't opaque");
        }
    }

    @Test void modelUsesTheTexturesAndTheItemSelectsTheModelString() throws IOException {
        String model = Files.readString(res("assets/itembeacon/models/block/item_beacon.json"));
        assertTrue(model.contains("itembeacon:block/item_beacon_side") && model.contains("itembeacon:block/item_beacon_top"), model);
        String item = Files.readString(res("assets/minecraft/items/lodestone.json"));
        assertTrue(item.contains("\"when\": \"" + CreativeTabStrings.MODEL + "\""), item);
        assertTrue(item.contains("itembeacon:block/item_beacon"), item);
        assertTrue(item.contains("\"model\": \"minecraft:block/lodestone\""), "vanilla fallback: " + item);
        // The plugin's item carries the same string (BeaconItem.MODEL).
        String plugin = Files.readString(dir().resolve("../beacon-plugin/src/main/java/dev/whizzle/itembeacon/BeaconItem.java"));
        assertTrue(plugin.contains("MODEL = \"" + CreativeTabStrings.MODEL + "\""), "BeaconItem.MODEL differs");
        String tab = Files.readString(dir().resolve("src/main/java/dev/whizzle/itembeacon/client/CreativeTab.java"));
        assertTrue(tab.contains("MODEL = \"" + CreativeTabStrings.MODEL + "\"") && tab.contains("\"itembeacon:beacon\""), "CreativeTab differs");
    }

    /** 26.x numbers mouse buttons like SDL (left 1, right 3): compare with InputConstants.MOUSE_BUTTON_*, never a literal. */
    @Test void mouseButtonsByName() throws IOException {
        Pattern literal = Pattern.compile("button\\(\\)\\s*[=!]=\\s*\\d");
        try (Stream<Path> files = Files.walk(dir().resolve("src/main/java"))) {
            for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList())
                for (String line : Files.readAllLines(f))
                    assertFalse(literal.matcher(line).find(), f.getFileName() + ": " + line.trim());
        }
    }

    /** The GUI font has no ✓ ✕ ≠ ≤ ≥ →: they'd show as boxes. */
    @Test void noGlyphsTheFontLacks() throws IOException {
        try (Stream<Path> files = Files.walk(dir().resolve("src/main/java"))) {
            for (Path f : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String s = Files.readString(f);
                for (String bad : List.of("✓", "✕", "≠", "≤", "≥", "→")) assertFalse(s.contains(bad), f.getFileName() + " uses " + bad);
            }
        }
    }

    static final class CreativeTabStrings { static final String MODEL = "itembeacon:item_beacon"; }
}
