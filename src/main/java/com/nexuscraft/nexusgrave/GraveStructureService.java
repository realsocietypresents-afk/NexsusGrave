package com.nexuscraft.nexusgrave;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * The physical, cosmetic half of a grave -- ordinary placed blocks, nothing more. Deliberately
 * kept dumb on purpose: every piece of REAL state (items, XP, epitaph, decay, permanence, claimed/
 * collapsed flags) lives entirely in {@link GraveRecord} via {@link GraveRegistry}, so a grave
 * survives even if these blocks are broken, replaced, or griefed by anyone -- this service only
 * ever reflects the record's own condition back into the world, never the other way around.
 *
 * <p>A grave is two blocks: a base marker (the condition-dependent cobblestone/mossy-cobblestone
 * block, at the exact death-block coordinate) and a {@code PLAYER_HEAD} topper directly above it.
 * The topper is a plain, generic head via {@link Block#setType} -- not the actual dead player's
 * own skin (that would need a real {@code Skull} {@code BlockState}/{@code setOwnerProfile}, judged
 * unnecessary added scope for this project, see this project's own README for that call). The
 * topper disappears for good once a grave collapses; the base marker never does, matching this
 * project's own "a bare marker remains" promise even after everything inside is lost forever.
 */
public final class GraveStructureService {

    /**
     * Finds the nearest free spot for a new grave around where the player died (see
     * {@link GraveSpotFinder}). Returns {x, y, z} of the base block, or {@code null} if there is no
     * room -- in which case the caller must NOT take the player's items.
     */
    public int[] findSpot(Location death, GraveConfig config) {
        if (death == null || death.getWorld() == null) {
            return null;
        }
        World world = death.getWorld();
        return GraveSpotFinder.find(
                (x, y, z) -> world.getBlockAt(x, y, z).getType(),
                death.getBlockX(), death.getBlockY(), death.getBlockZ(),
                config.placementSearchRadius, config.placementSearchVertical,
                world.getMinHeight(), world.getMaxHeight());
    }

    public void place(GraveRecord record) {
        applyCondition(record);
    }

    /** Re-reads the record's current {@link GraveCondition} and updates the physical blocks to
     *  match -- called once at death, and again every time {@code GraveDecayService} moves a
     *  grave into a new condition tier. Never touches the record itself. */
    public void applyCondition(GraveRecord record) {
        Block base = baseBlock(record);
        Block topper = topperBlock(record);
        if (base == null || topper == null) {
            return;
        }
        GraveCondition condition = record.condition();
        base.setType(baseMaterialFor(condition));
        topper.setType(condition == GraveCondition.COLLAPSED ? Material.AIR : Material.PLAYER_HEAD);
    }

    /** The permanent, decay-proof end state: topper gone, base downgraded to a bare marker that
     *  is never removed again. Whatever the grave held is gone for good the moment this runs --
     *  {@code GraveDecayService} is the only real caller, and only once, right as a grave crosses
     *  into {@code GraveCondition.COLLAPSED}. */
    public void collapse(GraveRecord record) {
        Block base = baseBlock(record);
        Block topper = topperBlock(record);
        if (base != null) {
            base.setType(Material.COBBLESTONE);
        }
        if (topper != null) {
            topper.setType(Material.AIR);
        }
    }

    private static Material baseMaterialFor(GraveCondition condition) {
        return switch (condition) {
            case FRESH -> Material.COBBLESTONE;
            case WEATHERED, OVERGROWN -> Material.MOSSY_COBBLESTONE;
            case COLLAPSED -> Material.COBBLESTONE;
        };
    }

    private Block baseBlock(GraveRecord record) {
        World world = Bukkit.getWorld(record.worldName());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(record.x(), record.y(), record.z());
    }

    private Block topperBlock(GraveRecord record) {
        World world = Bukkit.getWorld(record.worldName());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(record.x(), record.y() + 1, record.z());
    }
}
