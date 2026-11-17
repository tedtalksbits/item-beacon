package dev.whizzle.itembeacon.model;

/**
 * Some of one exact item in a container. {@code inside} is empty for items lying in the container itself, or what
 * they sit in, innermost first: "Shulker Box", "Bundle in Red Shulker Box".
 */
public record Stack(ItemKey item, int count, String inside) {
    public Stack {
        if (count < 1) throw new IllegalArgumentException("count " + count);
        if (inside == null) inside = "";
    }
}
