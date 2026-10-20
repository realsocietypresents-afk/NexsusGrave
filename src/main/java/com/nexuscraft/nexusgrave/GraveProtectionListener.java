package com.nexuscraft.nexusgrave;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.List;

/**
 * Keeps the marker of an ACTIVE grave (items still inside, not collapsed) from being broken by
 * players or blown up. Only staff can remove one. Once a grave is emptied or has collapsed, its
 * leftover block is ordinary and anyone may clear it. The grave's items live in the record, not the
 * block, so this protects the way back to the items, not the items themselves.
 */
public final class GraveProtectionListener implements Listener {

    private final GraveConfig config;
    private final GraveRegistry registry;
    private final GraveOpener opener;

    public GraveProtectionListener(GraveConfig config, GraveRegistry registry, GraveOpener opener) {
        this.config = config;
        this.registry = registry;
        this.opener = opener;
    }

    /** The active grave this block belongs to (as marker or as the head on top), or null. */
    GraveRecord activeGraveAt(Block block) {
        if (block == null) {
            return null;
        }
        GraveRecord record = registry.findByBlock(block.getLocation());
        if (record == null) {
            record = registry.findByBlock(block.getRelative(0, -1, 0).getLocation());
        }
        if (record == null || record.isClaimed() || record.isCollapsed()) {
            return null;
        }
        return record;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        GraveRecord record = activeGraveAt(event.getBlock());
        if (record == null) {
            return;
        }
        Player player = event.getPlayer();
        boolean staff = player != null && opener.isStaff(player);
        if (GraveAccess.markerProtected(config.protectMarkers, true, staff)) {
            event.setCancelled(true);
            if (player != null) {
                player.sendMessage("§8[Grave] §cThis grave marker can't be broken -- right-click it to open it"
                        + (record.ownerId().equals(player.getUniqueId()) ? "." : ", if it's yours."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeProtected(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeProtected(event.blockList());
    }

    private void removeProtected(List<Block> blocks) {
        if (!config.protectMarkers) {
            return;
        }
        blocks.removeIf(b -> activeGraveAt(b) != null);
    }
}
