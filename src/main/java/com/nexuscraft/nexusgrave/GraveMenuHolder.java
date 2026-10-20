package com.nexuscraft.nexusgrave;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

/**
 * The real {@code InventoryHolder} tag on a browsable grave-loot menu -- same "InventoryHolder
 * subclass carries its own context" pattern as {@code PortalMenuHolder} in this family's own
 * NexusPortals. Unlike {@code PortalMenuHolder} (a browse-and-teleport menu whose clicks are
 * always cancelled), this one is a real loot container: vanilla's own default click handling is
 * left alone entirely, so items really move into whoever's looting it. {@link GraveMenuListener}
 * only needs this holder to know which {@link GraveRecord} to sync back to on close.
 */
public final class GraveMenuHolder implements InventoryHolder {

    private final UUID viewerId;
    private final GraveKey graveKey;
    private Inventory inventory;

    public GraveMenuHolder(UUID viewerId, GraveKey graveKey) {
        this.viewerId = viewerId;
        this.graveKey = graveKey;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public GraveKey graveKey() {
        return graveKey;
    }
}
