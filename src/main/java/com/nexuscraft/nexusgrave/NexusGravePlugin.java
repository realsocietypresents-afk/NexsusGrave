package com.nexuscraft.nexusgrave;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * NexusGrave -- a massive overhaul of player death, the tenth entry in the ongoing engine-overhaul
 * series and, per its own pitch, the biggest fundamental piece yet: instead of vanilla's blind
 * random item-scatter on death (unchanged since the game's earliest days), every death seals its
 * drops and XP into a real, physical, persistent grave at the death location.
 *
 * <p>A real grave ({@code GraveDeathListener}/{@code GraveRegistry}/{@code GraveStructureService}
 * -- every death is sealed into its own cataloged, physically marked grave, never vanilla's blind
 * scatter); a pre-writable epitaph ({@code GraveCommand}'s {@code epitaph} subcommand -- shown to
 * anyone who reads the grave, defaulting to vanilla's own real death message if none was set); real
 * decay across four condition tiers ({@code GraveDecayService}/{@code GraveCondition} -- an
 * unclaimed grave eventually collapses for good, taking its contents with it, leaving only a bare
 * marker behind); an earned permanence rite ({@code GraveInteractListener} consuming the config'd
 * real Nether Star -- stops decay-based collapse only, never theft); a real corpse run
 * ({@code CorpseRunService} -- a real slowness/weakness debuff plus a real compass bound to a
 * player's most recent grave, both refreshed on death and reclaim); and real, unguarded grave
 * robbing ({@code GraveMenu}/{@code GraveMenuListener} -- any player, or nothing at all, stands
 * between an unclaimed grave and whoever finds it first).
 *
 * <p>Deliberately its own standalone plugin: no other Nexus plugin touches player death, item
 * drops, or respawn at all. See README.md's own section for the full reasoning.
 */
public final class NexusGravePlugin extends JavaPlugin {

    private GraveConfig config;
    private GraveRegistry registry;
    private GraveDecayService decayService;
    private CorpseRunService corpseRunService;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.config = new GraveConfig(this);
        config.load(getLogger());

        this.registry = new GraveRegistry(this);
        registry.load(getLogger());

        GraveStructureService structureService = new GraveStructureService();
        this.decayService = new GraveDecayService(config, registry, structureService);
        this.corpseRunService = new CorpseRunService(config, registry);

        GraveOpener opener = new GraveOpener(config, registry);
        getServer().getPluginManager().registerEvents(new GraveDeathListener(this, config, registry, structureService, corpseRunService), this);
        getServer().getPluginManager().registerEvents(new GraveInteractListener(config, registry, decayService, opener), this);
        getServer().getPluginManager().registerEvents(new GraveProtectionListener(config, registry, opener), this);
        getServer().getPluginManager().registerEvents(new GraveMenuListener(registry), this);

        long decayPeriodTicks = 20L * 60L * config.decayIntervalMinutes;
        getServer().getScheduler().runTaskTimer(this, () -> decayService.tick(), decayPeriodTicks, decayPeriodTicks);

        long corpseRunPeriodTicks = 20L * config.corpseRunTickIntervalSeconds;
        getServer().getScheduler().runTaskTimer(this, () -> corpseRunService.tick(), corpseRunPeriodTicks, corpseRunPeriodTicks);

        var command = getCommand("nexusgrave");
        if (command != null) {
            command.setExecutor(new GraveCommand(config, registry, opener, this::reload));
        }

        getLogger().info("NexusGrave enabled -- death has been overhauled.");
    }

    @Override
    public void onDisable() {
        if (registry != null) {
            registry.save();
        }
        getLogger().info("NexusGrave disabled.");
    }

    private void reload() {
        reloadConfig();
        config.load(getLogger());
    }
}
