package dev.whizzle.itembeacon.client;

import dev.whizzle.itembeacon.model.Protocol.Place;

import java.util.*;

/**
 * The waypoints the player chose with "Show waypoints" (slice 3 draws them; until then the server outlines them).
 * Cleared when the server says the container was opened, after the server's lifetime, or by hand. Pure Java.
 */
public final class Waypoints {
    private Waypoints() {}

    private static final Map<String, Place> ACTIVE = new LinkedHashMap<>();
    private static long expires;
    static java.util.function.LongSupplier clock = System::currentTimeMillis;

    public static void show(List<Place> places, long lifetimeMs) {
        ACTIVE.clear();
        for (Place p : places) if (p.sameDimension()) ACTIVE.put(p.id(), p);
        expires = clock.getAsLong() + lifetimeMs;
    }

    public static void opened(String id) { ACTIVE.remove(id); }

    public static void clear() { ACTIVE.clear(); }

    public static void tick() { if (!ACTIVE.isEmpty() && clock.getAsLong() >= expires) ACTIVE.clear(); }

    public static Set<String> ids() { return Collections.unmodifiableSet(ACTIVE.keySet()); }

    public static Collection<Place> places() { return Collections.unmodifiableCollection(ACTIVE.values()); }
}
