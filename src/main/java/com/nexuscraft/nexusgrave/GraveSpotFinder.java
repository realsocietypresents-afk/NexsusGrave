package com.nexuscraft.nexusgrave;

import org.bukkit.Material;

import java.util.Set;

/**
 * Picks WHERE a grave's two blocks may go. A grave is a base block plus a head on top of it, and
 * both are written with {@code Block#setType}, which skips every protection plugin (including
 * NexusSpawnShield and land claims) and happily overwrites whatever is there. Inside a large
 * protected spawn area that meant a death could silently replace a wall, a floor or a chest-sized
 * piece of someone's build. So a grave now only ever goes into space that is already empty (or
 * soft, like water or tall grass), searching outward from the death block for the nearest spot
 * where BOTH blocks are free.
 *
 * <p>Pure logic over a tiny {@link Probe} so it can be tested without a world.
 */
public final class GraveSpotFinder {

    /** Hard caps so a bad config can never make a death scan a huge volume. */
    public static final int MAX_RADIUS = 16;
    public static final int MAX_VERTICAL = 8;

    /** Reads the block type at a coordinate. */
    public interface Probe {
        Material typeAt(int x, int y, int z);
    }

    /** Materials a grave may overwrite besides plain air. Compared by name so this file never
     *  depends on which of these happen to exist in a given Material copy. */
    private static final Set<String> SOFT = Set.of(
            "AIR", "CAVE_AIR", "VOID_AIR",
            "WATER", "LAVA",
            "SHORT_GRASS", "TALL_GRASS", "FERN", "LARGE_FERN", "DEAD_BUSH",
            "SNOW", "VINE", "SEAGRASS", "TALL_SEAGRASS", "LIGHT");

    private GraveSpotFinder() {
    }

    public static boolean replaceable(Material material) {
        if (material == null) {
            return false;
        }
        return material.isAir() || SOFT.contains(material.name());
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @param minY lowest valid block y of the world (e.g. -64)
     * @param maxY world height limit, EXCLUSIVE (e.g. 320, so the top block is 319)
     * @return {x, y, z} of the base block, or {@code null} if nothing nearby is free
     */
    public static int[] find(Probe probe, int x, int y, int z, int radius, int vertical, int minY, int maxY) {
        int r = clamp(radius, 0, MAX_RADIUS);
        int v = clamp(vertical, 0, MAX_VERTICAL);
        int[] best = null;
        long bestScore = Long.MAX_VALUE;
        // Offsets tried 0, +1, -1, +2, -2 ... so that on an exact tie the grave goes UP, never
        // underground beneath a build where nobody would look for it.
        for (int step = 0; step <= 2 * v; step++) {
            int dy = (step % 2 == 1) ? (step + 1) / 2 : -(step / 2);
            int by = y + dy;
            if (by < minY || by + 1 > maxY - 1) {
                continue;
            }
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    long score = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (score >= bestScore) {
                        continue;
                    }
                    if (replaceable(probe.typeAt(x + dx, by, z + dz))
                            && replaceable(probe.typeAt(x + dx, by + 1, z + dz))) {
                        best = new int[] {x + dx, by, z + dz};
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }
}
