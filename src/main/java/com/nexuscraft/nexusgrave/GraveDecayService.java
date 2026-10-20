package com.nexuscraft.nexusgrave;

import java.util.ArrayList;
import java.util.List;

/**
 * The real-time weathering side of a grave's life -- the feature this whole project's "go crazy"
 * pitch was built around. {@link #tick()} runs on a real, config-driven interval
 * ({@code decay.interval-minutes}) and drops every non-permanent, non-collapsed, unclaimed grave's
 * decay by a fixed amount; a grave that crosses into {@link GraveCondition#COLLAPSED} as a result
 * is really, physically collapsed -- its topper block removed, its base downgraded to a bare
 * marker, and everything it was holding lost forever (see {@code GraveStructureService#collapse}).
 * A {@code permanent} grave (the rite of passage having been paid for at the graveside) is skipped
 * entirely, every pass, forever -- this is the ONLY thing the rite protects against. It does
 * nothing at all against a grave being found and looted by someone else first; that risk is never
 * mitigated by anything in this class (see this project's own README for why that's deliberate).
 * A grave that's already been claimed (looted, by the owner or anyone else) is also skipped: it
 * has nothing left to lose, so decaying it further would only be cosmetic busywork.
 */
public final class GraveDecayService {

    private final GraveConfig config;
    private final GraveRegistry registry;
    private final GraveStructureService structureService;

    public GraveDecayService(GraveConfig config, GraveRegistry registry, GraveStructureService structureService) {
        this.config = config;
        this.registry = registry;
        this.structureService = structureService;
    }

    /** Pure decay math, exposed separately so it can be unit-tested without a real World/Block --
     *  returns the new decay value, never applying it itself. Mirrors
     *  {@code PortalMaintenanceService#decay} exactly. */
    public static double decay(double currentDecay, double amountPerInterval) {
        return Math.max(0.0, currentDecay - amountPerInterval);
    }

    /** Runs one real decay pass over every registered grave. Returns the graves that collapsed
     *  this pass (crossed from non-collapsed into collapsed), so the caller can message anyone
     *  who might care -- mirrors {@code PortalMaintenanceService#tick}'s own return-list shape. */
    public List<GraveRecord> tick() {
        List<GraveRecord> newlyCollapsed = new ArrayList<>();
        boolean changed = false;
        for (GraveRecord record : registry.all()) {
            if (record.isPermanent() || record.isCollapsed() || record.isClaimed()) {
                continue;
            }
            double before = record.decay();
            record.setDecay(decay(before, config.decayAmountPerInterval));
            changed = true;
            if (record.condition() == GraveCondition.COLLAPSED && before > 0.0) {
                collapse(record);
                newlyCollapsed.add(record);
            } else {
                structureService.applyCondition(record);
            }
        }
        if (changed) {
            registry.save();
        }
        return newlyCollapsed;
    }

    /** Really, permanently collapses this grave: flips the flag, downgrades the physical blocks,
     *  and clears its contents -- everything it held is gone the moment this runs, matching this
     *  project's own "lose it forever" promise. */
    public void collapse(GraveRecord record) {
        record.setCollapsed(true);
        record.items().clear();
        record.setDroppedExp(0);
        structureService.collapse(record);
        registry.onRecordStateChanged(record);
    }

    /** Consumes the rite of passage: makes this specific grave permanently immune to decay and
     *  collapse from this point on. Deliberately does NOT touch {@code claimed} -- a permanent
     *  grave can still be found and looted by anyone else; permanence only ever stops the decay
     *  clock, never the separate, ever-present robbery risk. */
    public void grantPermanence(GraveRecord record) {
        record.setPermanent(true);
        registry.onRecordStateChanged(record);
    }
}
