package com.nexuscraft.nexusgrave;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * The whole plugin's real entry point: instead of vanilla's blind random item-scatter on death,
 * every death seals its drops and XP into a real, physical, persistent grave at the death
 * location. Real vanilla drop/XP behavior is fully suppressed here ({@code getDrops().clear()},
 * {@code setDroppedExp(0)}, {@code setKeepInventory(false)} -- a grave, not vanilla's own optional
 * "keep everything" rule, is the whole point) and replaced with this project's own sealed,
 * lootable-by-anyone grave. The corpse-run debuff/compass is deliberately NOT applied here -- see
 * {@link #onPlayerRespawn} and {@code PlayerRespawnEvent}'s own stub comment for why.
 */
public final class GraveDeathListener implements Listener {

    private final Plugin plugin;
    private final GraveConfig config;
    private final GraveRegistry registry;
    private final GraveStructureService structureService;
    private final CorpseRunService corpseRunService;

    public GraveDeathListener(Plugin plugin, GraveConfig config, GraveRegistry registry,
                              GraveStructureService structureService, CorpseRunService corpseRunService) {
        this.plugin = plugin;
        this.config = config;
        this.registry = registry;
        this.structureService = structureService;
        this.corpseRunService = corpseRunService;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        Location deathLocation = player.getLocation();
        if (deathLocation == null || deathLocation.getWorld() == null) {
            return;
        }

        // Find room FIRST. If there is none, leave vanilla's drops completely alone -- a player
        // must never lose their items to a grave that could not be built.
        int[] spot = structureService.findSpot(deathLocation, config);
        if (spot == null) {
            player.sendMessage(GraveTexts.NO_ROOM);
            return;
        }

        String plainDeathMessage = PlainTextComponentSerializer.plainText().serialize(event.deathMessage());
        if (plainDeathMessage == null || plainDeathMessage.isBlank()) {
            plainDeathMessage = player.getName() + " died";
        }

        GraveRecord record = new GraveRecord(
                GraveKey.newKey(),
                player.getUniqueId(),
                player.getName(),
                deathLocation.getWorld().getName(),
                spot[0],
                spot[1],
                spot[2],
                System.currentTimeMillis(),
                plainDeathMessage);

        record.setCustomEpitaph(registry.pendingEpitaph(player.getUniqueId()));
        record.items().addAll(event.getDrops());
        record.setDroppedExp(event.getDroppedExp());

        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(false);

        registry.register(record);
        structureService.place(record);
    }

    /** Applies the corpse-run debuff/compass right as the player actually respawns -- the moment
     *  it will really stick, unlike applying it during {@link PlayerDeathEvent} itself. */
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        corpseRunService.apply(player);

        if (!config.notifyOnRespawn) {
            return;
        }
        GraveRecord active = registry.mostRecentActiveGrave(player.getUniqueId());
        Location from = event.getRespawnLocation();
        if (active == null || from == null || from.getWorld() == null) {
            return;
        }
        String line = GraveTexts.whereIsMyGrave(active.worldName(), active.x(), active.y(), active.z(),
                from.getWorld().getName(), from.getX(), from.getY(), from.getZ());
        // One tick later: a message sent inside the respawn event itself can be lost in the
        // death-to-respawn transition, same reason the corpse-run is applied here, not at death.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.sendMessage(line);
            }
        }, 1L);
    }
}
