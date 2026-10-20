package com.nexuscraft.nexusgrave;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

/** Tests for where a grave may be placed, and the respawn wording. Plain main, no JUnit. */
public final class SpotFinderTest {

    private static int checks = 0;
    private static int failures = 0;

    /** A tiny world: everything is AIR except the blocks put in the map. */
    private static final class Fake implements GraveSpotFinder.Probe {
        final Map<String, Material> blocks = new HashMap<>();
        int reads = 0;
        Material fill = Material.AIR;

        void set(int x, int y, int z, Material m) { blocks.put(x + "," + y + "," + z, m); }

        @Override
        public Material typeAt(int x, int y, int z) {
            reads++;
            return blocks.getOrDefault(x + "," + y + "," + z, fill);
        }
    }

    public static void main(String[] args) {
        replaceable();
        openSpace();
        solidDeathBlock();
        ceilingAbove();
        spreadsAround();
        noRoom();
        twoDeathsSameSpot();
        worldLimits();
        limitsAndCaps();
        texts();
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void replaceable() {
        expect(GraveSpotFinder.replaceable(Material.AIR), "air is free");
        expect(GraveSpotFinder.replaceable(Material.WATER), "water is free");
        expect(GraveSpotFinder.replaceable(Material.LAVA), "lava is free");
        expect(!GraveSpotFinder.replaceable(Material.STONE), "stone is not free");
        expect(!GraveSpotFinder.replaceable(Material.CHEST), "a chest is never overwritten");
        expect(!GraveSpotFinder.replaceable(Material.BEDROCK), "bedrock is never overwritten");
        expect(!GraveSpotFinder.replaceable(Material.COBBLESTONE), "an existing grave marker is not free");
        expect(!GraveSpotFinder.replaceable(Material.PLAYER_HEAD), "an existing grave head is not free");
        expect(!GraveSpotFinder.replaceable(null), "null is not free");
    }

    private static void openSpace() {
        Fake w = new Fake();
        int[] s = GraveSpotFinder.find(w, 10, 70, 20, 4, 3, -64, 320);
        expect(s != null && s[0] == 10 && s[1] == 70 && s[2] == 20, "open air: grave goes exactly at the death block");
    }

    private static void solidDeathBlock() {
        Fake w = new Fake();
        w.set(10, 70, 20, Material.STONE);
        int[] s = GraveSpotFinder.find(w, 10, 70, 20, 4, 3, -64, 320);
        expect(s != null, "solid death block still finds a spot");
        expect(s != null && !(s[0] == 10 && s[1] == 70 && s[2] == 20), "...but not the solid block itself");
        expect(s != null && Math.abs(s[0] - 10) + Math.abs(s[1] - 70) + Math.abs(s[2] - 20) == 1, "...and the nearest one, one step away");
    }

    private static void ceilingAbove() {
        // Death in a one-high gap: base is free but the head's block is a ceiling.
        Fake w = new Fake();
        w.set(0, 71, 0, Material.STONE);
        int[] s = GraveSpotFinder.find(w, 0, 70, 0, 4, 3, -64, 320);
        expect(s != null && !(s[0] == 0 && s[1] == 70 && s[2] == 0), "a ceiling over the death block pushes the grave elsewhere");
        expect(s != null && w.typeAt(s[0], s[1], s[2]) == Material.AIR && w.typeAt(s[0], s[1] + 1, s[2]) == Material.AIR,
                "both blocks of the chosen spot are free");
    }

    private static void spreadsAround() {
        // Solid slab everywhere at y=70 and 71 within radius 2; the nearest free pair is vertical.
        Fake w = new Fake();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                w.set(x, 70, z, Material.STONE);
                w.set(x, 71, z, Material.STONE);
            }
        }
        int[] s = GraveSpotFinder.find(w, 0, 70, 0, 4, 3, -64, 320);
        expect(s != null && s[1] == 72 && s[0] == 0 && s[2] == 0, "build full of solid blocks: grave goes just above it (y=72), not the equally-near hole under it");
    }

    private static void noRoom() {
        Fake w = new Fake();
        w.fill = Material.STONE;
        expect(GraveSpotFinder.find(w, 0, 70, 0, 4, 3, -64, 320) == null, "solid everywhere: no grave, caller keeps vanilla drops");
        Fake w2 = new Fake();
        w2.fill = Material.STONE;
        w2.set(9, 70, 0, Material.AIR); // free base but head block solid
        expect(GraveSpotFinder.find(w2, 0, 70, 0, 4, 3, -64, 320) == null, "a single free block is not enough, both are needed");
        Fake w3 = new Fake();
        w3.fill = Material.STONE;
        w3.set(4, 70, 0, Material.AIR);
        w3.set(4, 71, 0, Material.AIR);
        int[] edge = GraveSpotFinder.find(w3, 0, 70, 0, 4, 3, -64, 320);
        expect(edge != null && edge[0] == 4, "a free pair exactly at the radius edge is found");
        expect(GraveSpotFinder.find(w3, 0, 70, 0, 3, 3, -64, 320) == null, "...and one block beyond the radius is not");
        Fake w4 = new Fake();
        w4.fill = Material.STONE;
        w4.set(0, 73, 0, Material.AIR);
        w4.set(0, 74, 0, Material.AIR);
        expect(GraveSpotFinder.find(w4, 0, 70, 0, 4, 3, -64, 320) != null, "a free pair exactly at the vertical limit is found");
        expect(GraveSpotFinder.find(w4, 0, 70, 0, 4, 2, -64, 320) == null, "...and one beyond the vertical limit is not");
        Fake w5 = new Fake();
        w5.fill = Material.STONE;
        w5.set(0, 67, 0, Material.AIR);
        w5.set(0, 68, 0, Material.AIR);
        int[] down = GraveSpotFinder.find(w5, 0, 70, 0, 4, 3, -64, 320);
        expect(down != null && down[1] == 67, "it can also search downward");
    }

    private static void twoDeathsSameSpot() {
        // The old code gave both deaths the same block and the second silently took over the first
        // grave's lookup. After the first grave is built (base + head), the second must move.
        Fake w = new Fake();
        int[] first = GraveSpotFinder.find(w, 5, 64, 5, 4, 3, -64, 320);
        w.set(first[0], first[1], first[2], Material.COBBLESTONE);
        w.set(first[0], first[1] + 1, first[2], Material.PLAYER_HEAD);
        int[] second = GraveSpotFinder.find(w, 5, 64, 5, 4, 3, -64, 320);
        expect(second != null && !(second[0] == first[0] && second[1] == first[1] && second[2] == first[2]),
                "a second death at the same spot gets its own spot");
    }

    private static void worldLimits() {
        Fake w = new Fake();
        int[] low = GraveSpotFinder.find(w, 0, -64, 0, 0, 0, -64, 320);
        expect(low != null && low[1] == -64, "lowest block of the world is allowed");
        expect(GraveSpotFinder.find(w, 0, -65, 0, 0, 0, -64, 320) == null, "below the world: no spot");
        int[] top = GraveSpotFinder.find(w, 0, 318, 0, 0, 0, -64, 320);
        expect(top != null && top[1] == 318, "base at 318 (head at 319, the top block) is allowed");
        expect(GraveSpotFinder.find(w, 0, 319, 0, 0, 0, -64, 320) == null, "base at 319 would put the head out of the world");
        int[] void_ = GraveSpotFinder.find(w, 0, -80, 0, 4, 8, -64, 320);
        expect(void_ == null, "a death far under the world finds nothing in range: drops stay vanilla");
        int[] nearVoid = GraveSpotFinder.find(w, 0, -66, 0, 4, 8, -64, 320);
        expect(nearVoid != null && nearVoid[1] >= -64, "a death just under the world is pulled up into it");
    }

    private static void limitsAndCaps() {
        expect(GraveSpotFinder.clamp(99, 0, 16) == 16, "clamp high");
        expect(GraveSpotFinder.clamp(-5, 0, 16) == 0, "clamp low");
        expect(GraveSpotFinder.clamp(7, 0, 16) == 7, "clamp inside");
        Fake w = new Fake();
        w.fill = Material.STONE;
        GraveSpotFinder.find(w, 0, 70, 0, 1000, 1000, -64, 320);
        expect(w.reads <= 33 * 33 * 17 * 2, "a silly config radius is capped, scan stays bounded (" + w.reads + " reads)");
        Fake z = new Fake();
        int[] zero = GraveSpotFinder.find(z, 3, 70, 3, 0, 0, -64, 320);
        expect(zero != null && zero[0] == 3, "radius 0 still checks the death block itself");
        Fake zs = new Fake();
        zs.set(3, 70, 3, Material.STONE);
        expect(GraveSpotFinder.find(zs, 3, 70, 3, 0, 0, -64, 320) == null, "radius 0 and solid there: no grave");
        Fake neg = new Fake();
        expect(GraveSpotFinder.find(neg, 3, 70, 3, -5, -5, -64, 320) != null, "negative config values act like 0");
    }

    private static void texts() {
        String same = GraveTexts.whereIsMyGrave("world", 100, 64, 200, "world", 100.5, 64, 230.5);
        expect(same.contains("100, 64, 200"), "coordinates shown");
        expect(same.contains("about §730§f blocks away"), "distance shown (30)");
        String one = GraveTexts.whereIsMyGrave("world", 0, 64, 0, "world", 0.5, 64, 1.5);
        expect(one.contains("1§f block away"), "singular 'block' for distance 1");
        expect(!one.contains("blocks"), "no plural at 1");
        String other = GraveTexts.whereIsMyGrave("world_nether", 1, 2, 3, "world", 0, 0, 0);
        expect(other.contains("in §7world_nether"), "different world: world name instead of distance");
        expect(!other.contains("away"), "no distance across worlds");
        expect(same.contains("compass") && same.contains("right-click"), "tells the player what to do");
        expect(GraveTexts.NO_ROOM.contains("dropped normally"), "no-room message explains items dropped");
    }

    private static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAILED: " + what);
        }
    }
}
