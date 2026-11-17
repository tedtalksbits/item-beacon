package dev.whizzle.itembeacon;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Enchanting-table glyphs drifting into each beacon a player is near (the owner's low-priority wish). Server-side,
 * so everyone sees them, mod or not. Like vanilla's enchanting table: an {@code ENCHANT} particle with a velocity is
 * drawn flying from the beacon plus that offset into the beacon. Config {@code particles: false} turns them off.
 */
public final class Particles {
    /** Only beacons this close to a player get glyphs. */
    static final double NEAR = 24;
    private final Beacons beacons;

    public Particles(Beacons beacons) { this.beacons = beacons; }

    /** Called every few ticks: a couple of glyphs per beacon near a player. */
    public void tick(Iterable<World> worlds) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (World w : worlds) {
            List<Player> players = w.getPlayers();
            if (players.isEmpty()) continue;
            for (Block b : beacons.loaded(w)) {
                Location c = b.getLocation().add(0.5, 1.1, 0.5);
                if (!near(players, c) || !beacons.is(b)) continue;
                for (int i = 0; i < 2; i++) {
                    double dx = r.nextDouble(-2.5, 2.5), dy = r.nextDouble(0.2, 1.8), dz = r.nextDouble(-2.5, 2.5);
                    w.spawnParticle(Particle.ENCHANT, c, 0, dx, dy, dz, 1.0);
                }
            }
        }
    }

    static boolean near(List<Player> players, Location c) {
        for (Player p : players) if (p.getLocation().distanceSquared(c) <= NEAR * NEAR) return true;
        return false;
    }
}
