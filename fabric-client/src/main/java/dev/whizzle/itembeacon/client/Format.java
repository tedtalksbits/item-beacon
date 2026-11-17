package dev.whizzle.itembeacon.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Text for the screen. Pure Java. */
public final class Format {
    private Format() {}

    /** A count that fits under an item icon: 412, 1.5k, 12k, 1.2M. */
    public static String count(long n) {
        if (n < 1000) return Long.toString(n);
        if (n < 10_000) return trim(String.format(Locale.ROOT, "%.1f", n / 1000.0)) + "k";
        if (n < 1_000_000) return (n / 1000) + "k";
        if (n < 10_000_000) return trim(String.format(Locale.ROOT, "%.1f", n / 1_000_000.0)) + "M";
        return (n / 1_000_000) + "M";
    }

    private static String trim(String s) { return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s; }

    /** "just now", "5 min ago", "2 h ago", "3 days ago". */
    public static String ago(long ms) {
        long min = Math.max(0, ms) / 60000;
        if (min < 1) return "just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 48) return h + " h ago";
        return h / 24 + " days ago";
    }

    /** "Chest minecart" for {@code chest_minecart}. */
    public static String kind(String key) {
        String s = key.replace('_', ' ').trim();
        return s.isEmpty() ? "Container" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "in the Nether" for {@code minecraft:the_nether}. */
    public static String dimension(String key) {
        return switch (key) {
            case "minecraft:overworld" -> "in the Overworld";
            case "minecraft:the_nether" -> "in the Nether";
            case "minecraft:the_end" -> "in the End";
            default -> "in " + key.substring(key.indexOf(':') + 1).replace('_', ' ');
        };
    }

    /** The non-empty "inside" labels of a place ("" parts mean loose in the container). */
    public static List<String> insides(String inside) {
        List<String> out = new ArrayList<>();
        for (String s : inside.split("; ", -1)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    /** "12 m", "1.2 km". */
    public static String distance(double blocks) {
        if (blocks < 1000) return Math.round(blocks) + " m";
        return trim(String.format(Locale.ROOT, "%.1f", blocks / 1000)) + " km";
    }
}
