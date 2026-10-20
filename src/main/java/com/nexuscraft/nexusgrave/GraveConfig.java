package com.nexuscraft.nexusgrave;

import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * All of this plugin's shared, tunable numbers -- fixed shapes (the four {@link GraveCondition}
 * bands, the corpse-run debuff's own two-effect makeup) stay in code, same "shared numbers in
 * config, fixed shapes in code" split this whole family already uses (see {@code PortalConfig}
 * in this family's own NexusPortals, which this class mirrors field-for-field in spirit).
 */
public final class GraveConfig {

    private final JavaPlugin plugin;

    public boolean enabled;

    public int decayIntervalMinutes;
    public double decayAmountPerInterval;

    /** The real, consumable "rite of passage" item -- deliberately {@code NETHER_STAR} by
     *  default, not {@code TOTEM_OF_UNDYING} (see this project's own {@code Material.java} stub
     *  comment for exactly why the totem was rejected). Configurable the same way NexusPortals
     *  lets its fuel/catalyst items be re-pointed at a different real Material. */
    public Material permanenceRiteItem;

    public int corpseRunTickIntervalSeconds;
    public int corpseRunDebuffDurationTicks;
    public int corpseRunSlownessAmplifier;
    public int corpseRunWeaknessAmplifier;

    public String menuTitle;
    public String epitaphPromptPrefix;

    public int maxEpitaphLength;

    /** How far (blocks sideways / up-down) a grave may be shifted from the death block to find free space. */
    public int placementSearchRadius;
    public int placementSearchVertical;
    /** Tell the owner where the grave is when they respawn. */
    public boolean notifyOnRespawn;

    /** Only the owner (and staff) can open a grave. False brings back open grave robbing. */
    public boolean ownerOnly;
    /** Active grave markers can't be broken or blown up except by staff. */
    public boolean protectMarkers;
    /** /nexusgrave retrieve works within this many blocks of your own grave, even if its marker is gone. */
    public int retrieveRadius;

    public GraveConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(Logger logger) {
        var config = plugin.getConfig();

        enabled = config.getBoolean("enabled", true);

        decayIntervalMinutes = config.getInt("decay.interval-minutes", 45);
        decayAmountPerInterval = config.getDouble("decay.amount-per-interval", 2.5);

        permanenceRiteItem = parseMaterial(config.getString("rite.permanence-item", "NETHER_STAR"), Material.NETHER_STAR, logger);

        corpseRunTickIntervalSeconds = config.getInt("corpse-run.tick-interval-seconds", 10);
        corpseRunDebuffDurationTicks = config.getInt("corpse-run.debuff-duration-ticks", 260);
        corpseRunSlownessAmplifier = config.getInt("corpse-run.slowness-amplifier", 1);
        corpseRunWeaknessAmplifier = config.getInt("corpse-run.weakness-amplifier", 0);

        menuTitle = config.getString("menu.title", "Grave Contents");
        epitaphPromptPrefix = config.getString("epitaph.prompt-prefix", "Epitaph set for your next grave: ");

        maxEpitaphLength = config.getInt("epitaph.max-length", 96);

        placementSearchRadius = GraveSpotFinder.clamp(config.getInt("placement.search-radius", 4), 0, GraveSpotFinder.MAX_RADIUS);
        placementSearchVertical = GraveSpotFinder.clamp(config.getInt("placement.search-vertical", 3), 0, GraveSpotFinder.MAX_VERTICAL);
        notifyOnRespawn = config.getBoolean("notify.on-respawn", true);

        ownerOnly = config.getBoolean("access.owner-only", true);
        protectMarkers = config.getBoolean("access.protect-markers", true);
        retrieveRadius = GraveSpotFinder.clamp(config.getInt("access.retrieve-radius", 8), 0, 64);
    }

    private static Material parseMaterial(String name, Material fallback, Logger logger) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        if (material == null) {
            logger.warning("nexusgrave: unknown material '" + name + "', falling back to " + fallback);
            return fallback;
        }
        return material;
    }
}
