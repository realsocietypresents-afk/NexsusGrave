package com.nexuscraft.nexusgrave;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Syncs a grave's menu back to its real, persistent {@link GraveRecord} the moment it's closed --
 * the payoff half of {@link GraveMenu}. Nothing here cancels clicks or moves items itself: real
 * vanilla's own default inventory-click handling already lets a viewer drag/shift-click items out
 * (or, if they really want to, back in) exactly like any other chest-shaped GUI, so this listener
 * only has to look at what's left once the menu closes. A grave that's been fully emptied is
 * marked {@link GraveRecord#setClaimed(boolean)} -- which is also what lets the corpse-run debuff/
 * compass move on to whichever grave (if any) the viewer should chase next.
 */
public final class GraveMenuListener implements Listener {

    private final GraveRegistry registry;

    public GraveMenuListener(GraveRegistry registry) {
        this.registry = registry;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }
        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof GraveMenuHolder menuHolder)) {
            return;
        }

        registry.releaseMenuLock(menuHolder.graveKey());

        GraveRecord record = registry.get(menuHolder.graveKey());
        if (record == null) {
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null) {
                remaining.add(item);
            }
        }
        record.items().clear();
        record.items().addAll(remaining);

        if (remaining.isEmpty()) {
            record.setClaimed(true);
        }
        registry.onRecordStateChanged(record);

        HumanEntity whoClicked = event.getPlayer();
        if (whoClicked instanceof Player player && remaining.isEmpty()) {
            player.sendMessage("§8[Grave] §fYou've reclaimed everything from §7" + record.ownerName() + "'s§f grave.");
        }
    }
}
