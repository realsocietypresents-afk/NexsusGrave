package com.nexuscraft.nexusgrave;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * {@code /nexusgrave epitaph <text>|clearepitaph|find|list|reload} -- same shape as every
 * sibling's own command. {@code reload} is admin-only, double-checked here rather than trusting
 * {@code plugin.yml}'s own default alone.
 */
final class GraveCommand implements CommandExecutor {

    private final GraveConfig config;
    private final GraveRegistry registry;
    private final GraveOpener opener;
    private final Runnable reload;

    GraveCommand(GraveConfig config, GraveRegistry registry, GraveOpener opener, Runnable reload) {
        this.opener = opener;
        this.config = config;
        this.registry = registry;
        this.reload = reload;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> handleReload(sender);
            case "list" -> handleList(sender);
            case "find" -> handleFind(sender);
            case "retrieve" -> handleRetrieve(sender);
            case "epitaph" -> handleEpitaph(sender, args);
            case "clearepitaph" -> handleClearEpitaph(sender);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§7Usage: /nexusgrave <epitaph <text>|clearepitaph|find|retrieve|list|reload>");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("nexusgrave.admin")) {
            sender.sendMessage("§cYou don't have permission to do that.");
            return;
        }
        reload.run();
        sender.sendMessage("§aNexusGrave reloaded.");
    }

    private void handleList(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player has graves.");
            return;
        }
        List<GraveRecord> owned = List.copyOf(registry.ownedBy(player.getUniqueId()));
        if (owned.isEmpty()) {
            player.sendMessage("§7You have no graves recorded -- may it stay that way.");
            return;
        }
        player.sendMessage("§6=== Your graves ===");
        for (GraveRecord record : owned) {
            String status = record.isCollapsed() ? "§8collapsed" : record.isClaimed() ? "§7emptied" : record.condition().display();
            player.sendMessage(status + " §f" + record.worldName() + " (" + record.x() + ", " + record.y() + ", " + record.z()
                    + ")" + (record.isPermanent() ? " §d[permanent]" : ""));
        }
    }

    private void handleFind(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can be pointed at a grave.");
            return;
        }
        GraveRecord active = registry.mostRecentActiveGrave(player.getUniqueId());
        if (active == null) {
            player.sendMessage("§7You have no unclaimed grave right now.");
            return;
        }
        var location = active.location();
        if (location == null) {
            player.sendMessage("§cYour most recent grave's world isn't loaded right now.");
            return;
        }
        player.setCompassTarget(location);
        player.sendMessage("§8[Grave] §fYour compass now points at your most recent grave in §7"
                + active.worldName() + "§f.");
    }

    /** Opens your own nearest active grave if you are close to it, whether or not its marker block
     *  still exists -- so a broken, burned or griefed marker never strands your items. */
    private void handleRetrieve(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can retrieve a grave.");
            return;
        }
        GraveRecord nearest = null;
        double best = Double.MAX_VALUE;
        GraveRecord anyActive = null;
        var here = player.getLocation();
        for (GraveRecord record : registry.ownedBy(player.getUniqueId())) {
            if (record.isClaimed() || record.isCollapsed()) {
                continue;
            }
            anyActive = record;
            var there = record.location();
            if (there == null || here == null || here.getWorld() == null || there.getWorld() == null
                    || !here.getWorld().getName().equals(there.getWorld().getName())) {
                continue;
            }
            double d = here.distanceSquared(there);
            if (GraveAccess.withinRange(d, config.retrieveRadius) && d < best) {
                best = d;
                nearest = record;
            }
        }
        if (nearest != null) {
            opener.tryOpen(player, nearest);
            return;
        }
        if (anyActive == null) {
            player.sendMessage("§7You have no unclaimed grave right now.");
            return;
        }
        player.sendMessage("§8[Grave] §fYou need to be within §7" + config.retrieveRadius + "§f blocks of your grave to retrieve it. "
                + "It is at §7" + anyActive.x() + ", " + anyActive.y() + ", " + anyActive.z() + "§f in §7" + anyActive.worldName() + "§f.");
    }

    private void handleEpitaph(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can set an epitaph.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§7Usage: /nexusgrave epitaph <text>");
            return;
        }
        String epitaph = String.join(" ", List.of(args).subList(1, args.length));
        if (epitaph.length() > config.maxEpitaphLength) {
            epitaph = epitaph.substring(0, config.maxEpitaphLength);
        }
        registry.setPendingEpitaph(player.getUniqueId(), epitaph);
        player.sendMessage("§8[Grave] §f" + config.epitaphPromptPrefix + "\"" + epitaph + "\"");
    }

    private void handleClearEpitaph(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly a player can clear an epitaph.");
            return;
        }
        registry.setPendingEpitaph(player.getUniqueId(), null);
        player.sendMessage("§8[Grave] §fCleared -- your next death will show vanilla's own death message instead.");
    }
}
