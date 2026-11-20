package dev.whizzle.itembeacon.client;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the inventory tools sit (config/itembeacon.properties; {@code /itembeaconmod settings}). Pure Java.
 * <ul>
 * <li>{@link Container}: the search bar and the container's sort buttons. RIGHT or LEFT of the panel, TOP (a row
 * above it), or TITLE (the sort buttons in the container's title row, the search bar above the panel).</li>
 * <li>{@link Inventory}: the sort buttons for your inventory. RIGHT, LEFT, BOTTOM (below the panel), or TITLE (in the
 * "Inventory" label row).</li>
 * </ul>
 * A spot that doesn't fit (a long title, no room above or below) falls back as {@link ToolLayout} says.
 */
public final class ToolSettings {
    public enum Container { RIGHT("Right"), LEFT("Left"), TOP("Top"), TITLE("In the title row");
        public final String label; Container(String l) { label = l; } }
    public enum Inventory { RIGHT("Right"), LEFT("Left"), BOTTOM("Bottom"), TITLE("In the Inventory row");
        public final String label; Inventory(String l) { label = l; } }

    public Container container = Container.RIGHT;
    public Inventory inventory = Inventory.RIGHT;

    public static ToolSettings load(Path file) {
        ToolSettings s = new ToolSettings();
        if (!Files.exists(file)) return s;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file)) { p.load(r); }
        catch (IOException e) { return s; }
        s.container = parse(Container.class, p.getProperty("container"), s.container);
        s.inventory = parse(Inventory.class, p.getProperty("inventory"), s.inventory);
        return s;
    }

    public void save(Path file) throws IOException {
        Properties p = new Properties();
        p.setProperty("container", container.name().toLowerCase());
        p.setProperty("inventory", inventory.name().toLowerCase());
        Files.createDirectories(file.getParent());
        try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "Item Beacon: where the search bar and sort buttons go. container: right, left, top, title. inventory: right, left, bottom, title.");
        }
    }

    static <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try { return Enum.valueOf(type, value.trim().toUpperCase()); } catch (IllegalArgumentException e) { return fallback; }
    }

    public static <E extends Enum<E>> E next(E e) {
        E[] all = e.getDeclaringClass().getEnumConstants();
        return all[(e.ordinal() + 1) % all.length];
    }
}
