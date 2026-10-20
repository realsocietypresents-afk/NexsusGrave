package com.nexuscraft.nexusgrave;

/**
 * A standalone, plain-{@code main}-method test suite (no JUnit) for this project's pure-math and
 * pure-identity logic -- same "pure logic gets a real, runnable test; Bukkit-glue gets compile
 * verification only" split this family already applies (see {@code PortalLogicTest} in this
 * family's own NexusPortals). Every check here exercises genuinely pure logic ({@link
 * GraveCondition#fromDecay}, {@link GraveDecayService#decay}, {@link GraveKey}'s own
 * identity/round-trip behavior) -- but {@code GraveDecayService} itself transitively references
 * Bukkit types through its constructor parameters ({@code GraveConfig}/{@code GraveRegistry}/
 * {@code GraveStructureService}), so unlike {@code PortalLogicTest}'s own handful of pure classes,
 * this suite needs the full stub tree on the classpath to compile even though it never actually
 * exercises a single Bukkit-dependent code path. Compile alongside {@code src/main} and
 * {@code stubs} together, then run directly with {@code java com.nexuscraft.nexusgrave.GraveLogicTest}.
 */
public final class GraveLogicTest {

    private static int failures = 0;

    public static void main(String[] args) {
        testGraveConditionBoundaries();
        testDecayMath();
        testGraveKeyStorageRoundTrip();
        testGraveKeyMalformedStorageReturnsNull();
        testTwoDeathsAtSameBlockStillGetDistinctKeys();

        if (failures == 0) {
            System.out.println("All GraveLogicTest checks passed.");
        } else {
            System.out.println(failures + " GraveLogicTest check(s) FAILED.");
            System.exit(1);
        }
    }

    private static void testGraveConditionBoundaries() {
        expect(GraveCondition.fromDecay(100.0) == GraveCondition.FRESH, "100 -> FRESH");
        expect(GraveCondition.fromDecay(76.0) == GraveCondition.FRESH, "76 -> FRESH");
        expect(GraveCondition.fromDecay(75.0) == GraveCondition.WEATHERED, "75 -> WEATHERED");
        expect(GraveCondition.fromDecay(41.0) == GraveCondition.WEATHERED, "41 -> WEATHERED");
        expect(GraveCondition.fromDecay(40.0) == GraveCondition.OVERGROWN, "40 -> OVERGROWN");
        expect(GraveCondition.fromDecay(1.0) == GraveCondition.OVERGROWN, "1 -> OVERGROWN");
        expect(GraveCondition.fromDecay(0.0) == GraveCondition.COLLAPSED, "0 -> COLLAPSED");
    }

    private static void testDecayMath() {
        expect(GraveDecayService.decay(20.0, 2.5) == 17.5, "decay 20 by 2.5 -> 17.5");
        expect(GraveDecayService.decay(1.0, 2.5) == 0.0, "decay floors at 0, never negative");
    }

    private static void testGraveKeyStorageRoundTrip() {
        GraveKey key = GraveKey.newKey();
        GraveKey roundTripped = GraveKey.fromStorageKey(key.toStorageKey());
        expect(key.equals(roundTripped), "GraveKey survives storage-key round trip");
    }

    private static void testGraveKeyMalformedStorageReturnsNull() {
        expect(GraveKey.fromStorageKey("not-a-real-uuid") == null, "malformed storage key resolves to null, not a crash");
    }

    private static void testTwoDeathsAtSameBlockStillGetDistinctKeys() {
        // The whole reason GraveKey is a random UUID rather than a location-derived key (like
        // PortalKey) -- two deaths at the exact same coordinate, even by the same player, must
        // never collide into a single merged identity. (GraveRecord itself isn't exercised here,
        // since it depends on Bukkit types -- see this class's own header comment -- but the
        // identity guarantee it relies on lives entirely in GraveKey, which this does cover.)
        GraveKey first = GraveKey.newKey();
        GraveKey second = GraveKey.newKey();
        expect(!first.equals(second), "two independently-created grave keys are never equal");
    }

    private static void expect(boolean condition, String description) {
        if (!condition) {
            failures++;
            System.out.println("FAILED: " + description);
        }
    }
}
