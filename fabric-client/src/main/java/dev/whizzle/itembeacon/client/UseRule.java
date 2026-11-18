package dev.whizzle.itembeacon.client;

/**
 * Whether a right-click on an Item Beacon is the beacon's own use (open it) rather than placing the held block,
 * mirroring the plugin's {@code BeaconKeeper.onUse}: sneaking with an item places it, except a compass (which would
 * bind to the lodestone). Pure Java.
 */
public final class UseRule {
    private UseRule() {}

    public static boolean beaconUse(boolean sneaking, boolean handEmpty, boolean compass) {
        return !(sneaking && !handEmpty && !compass);
    }
}
