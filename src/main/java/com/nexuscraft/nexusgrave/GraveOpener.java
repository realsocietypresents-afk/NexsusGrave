package com.nexuscraft.nexusgrave;

import org.bukkit.entity.Player;

/** The single place that decides whether a player may open a grave and then opens it. Used by the
 *  right-click on the marker and by {@code /nexusgrave retrieve}, so both follow the same rules. */
public final class GraveOpener {

    public enum Result { OPENED, DENIED, BUSY }

    private final GraveConfig config;
    private final GraveRegistry registry;

    public GraveOpener(GraveConfig config, GraveRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    public boolean isStaff(Player player) {
        return player.hasPermission(GraveAccess.STAFF_PERMISSION);
    }

    public Result tryOpen(Player player, GraveRecord record) {
        boolean isOwner = record.ownerId().equals(player.getUniqueId());
        if (!GraveAccess.canOpen(config.ownerOnly, isOwner, isStaff(player))) {
            player.sendMessage("§8[Grave] §cThis is §7" + record.ownerName() + "'s§c grave -- only they (or staff) can open it.");
            return Result.DENIED;
        }
        // One viewer at a time: two open copies of the same grave would each write their own
        // leftovers back on close and could duplicate or lose items.
        if (!registry.tryLockMenu(record.key(), player.getUniqueId(), System.currentTimeMillis())) {
            player.sendMessage("§8[Grave] §7Someone is already going through this grave -- try again in a moment.");
            return Result.BUSY;
        }
        player.openInventory(GraveMenu.build(player, record, config));
        return Result.OPENED;
    }
}
