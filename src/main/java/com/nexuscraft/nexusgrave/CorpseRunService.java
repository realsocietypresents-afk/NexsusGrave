package com.nexuscraft.nexusgrave;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * The "corpse run" itself -- the reason a death should actually sting until it's put right. Every
 * real, online player with an unclaimed, uncollapsed grave gets a real {@code SLOWNESS}/{@code
 * WEAKNESS} debuff kept continuously refreshed (via {@link #tick()}, on a short config-driven
 * interval, re-applied for longer than that interval so a brief lag spike never lets it lapse) for
 * as long as that grave stands, plus a real compass bound (via {@code Player#setCompassTarget})
 * straight at it. Both clear together the moment the player has no more active graves left --
 * whether because they reclaimed one themselves or because someone else looted it first, since
 * either one marks a grave {@code claimed} in the same registry.
 */
public final class CorpseRunService {

    private final GraveConfig config;
    private final GraveRegistry registry;

    public CorpseRunService(GraveConfig config, GraveRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    /** Runs one real pass over every online player, refreshing or clearing the corpse-run
     *  debuff/compass depending on whether they currently have any active (unclaimed,
     *  uncollapsed) grave. Call this on the real, config-driven
     *  {@code corpse-run.tick-interval-seconds} interval. */
    public void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            apply(player);
        }
    }

    /** Refreshes (or clears) one specific player's own corpse-run state right now -- used by
     *  {@link #tick()} for everyone online, and also called directly the moment a player dies or
     *  reclaims/loses a grave, so they don't have to wait for the next tick to feel it. */
    public void apply(Player player) {
        UUID playerId = player.getUniqueId();
        GraveRecord active = registry.mostRecentActiveGrave(playerId);
        if (active == null) {
            player.setCompassTarget(null);
            return;
        }
        var location = active.location();
        if (location != null) {
            player.setCompassTarget(location);
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                config.corpseRunDebuffDurationTicks, config.corpseRunSlownessAmplifier));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                config.corpseRunDebuffDurationTicks, config.corpseRunWeaknessAmplifier));
    }
}
