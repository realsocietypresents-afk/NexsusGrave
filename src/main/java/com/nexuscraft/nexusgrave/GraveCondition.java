package com.nexuscraft.nexusgrave;

/**
 * A grave's own real-time weathering ladder, 100 (freshly dug) down to 0 (collapsed for good).
 * Entirely this project's own invented mechanic -- real vanilla item drops have no concept of
 * decaying in place at all (they simply despawn on a flat timer, no stages, no marker left
 * behind). A {@code permanent} grave (the real "rite of passage" item consumed at the graveside)
 * never decays at all regardless of this value -- see {@code GraveRecord#isPermanent()}.
 */
public enum GraveCondition {
    FRESH("§fFresh", "recently dug -- holding steady"),
    WEATHERED("§7Weathered", "moss creeping in around the edges"),
    OVERGROWN("§2Overgrown", "cracking, sinking -- claim it soon or lose it"),
    COLLAPSED("§8Collapsed", "gone -- only a bare marker remains");

    private final String display;
    private final String description;

    GraveCondition(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public String display() {
        return display;
    }

    public String description() {
        return description;
    }

    public static GraveCondition fromDecay(double decay) {
        if (decay <= 0.0) {
            return COLLAPSED;
        } else if (decay <= 40.0) {
            return OVERGROWN;
        } else if (decay <= 75.0) {
            return WEATHERED;
        }
        return FRESH;
    }
}
