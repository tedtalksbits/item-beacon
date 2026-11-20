package dev.whizzle.itembeacon.client;

import java.util.*;

/**
 * The new order of a container's slots for each sort button (pure Java; the plugin merges partial stacks and lays
 * them out in this order). Ties keep the current order; empty slots go last.
 */
public final class SortPlan {
    private SortPlan() {}

    public enum Mode { TYPE, NAME, COUNT }

    /**
     * One slot as the sort sees it. {@code typeOrder}: the item's place in the creative menu (unknown items last);
     * {@code group}: the same number for the same item with the same components; {@code count}: the stack size.
     */
    public record Slot(int index, boolean empty, int typeOrder, String name, int group, int count) {}

    public static List<Integer> order(List<Slot> slots, Mode mode) {
        Map<Integer, Long> totals = new HashMap<>();
        for (Slot s : slots) if (!s.empty()) totals.merge(s.group(), (long) s.count(), Long::sum);
        Comparator<Slot> byType = Comparator.comparingInt(Slot::typeOrder);
        Comparator<Slot> byName = Comparator.comparing(s -> s.name().toLowerCase(Locale.ROOT));
        Comparator<Slot> sameItemTogether = Comparator.comparingInt(Slot::group);
        Comparator<Slot> biggerStackFirst = Comparator.comparingInt(Slot::count).reversed();
        Comparator<Slot> c = switch (mode) {
            case TYPE -> byType.thenComparing(byName).thenComparing(sameItemTogether).thenComparing(biggerStackFirst);
            case NAME -> byName.thenComparing(byType).thenComparing(sameItemTogether).thenComparing(biggerStackFirst);
            case COUNT -> Comparator.<Slot>comparingLong(s -> -totals.get(s.group())).thenComparing(byType).thenComparing(byName)
                .thenComparing(sameItemTogether).thenComparing(biggerStackFirst);
        };
        List<Slot> full = new ArrayList<>(), empty = new ArrayList<>();
        for (Slot s : slots) (s.empty() ? empty : full).add(s);
        full.sort(c.thenComparingInt(Slot::index));
        List<Integer> out = new ArrayList<>(slots.size());
        for (Slot s : full) out.add(s.index());
        for (Slot s : empty) out.add(s.index());
        return out;
    }
}
