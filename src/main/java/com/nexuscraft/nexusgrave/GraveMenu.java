package com.nexuscraft.nexusgrave;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Builds the real, lootable grave menu -- a plain inventory GUI standing in for a real vanilla
 * chest, deliberately: real item/XP/epitaph/decay state all live in {@link GraveRecord} via
 * {@link GraveRegistry}, never in a real vanilla {@code Chest} block, so a grave's contents survive
 * even if its physical marker blocks are broken or replaced by anyone (see
 * {@code GraveStructureService}'s own header comment for the full reasoning). Who may open
 * a grave is decided by {@link GraveOpener}, not here.
 */
public final class GraveMenu {

    private GraveMenu() {
    }

    public static Inventory build(Player viewer, GraveRecord record, GraveConfig config) {
        int size = Math.max(9, Math.min(54, roundUpToNine(record.items().size())));
        GraveMenuHolder holder = new GraveMenuHolder(viewer.getUniqueId(), record.key());
        Inventory inventory = Bukkit.createInventory(holder, size, config.menuTitle + ": " + record.ownerName());
        holder.setInventory(inventory);

        int slot = 0;
        for (ItemStack item : record.items()) {
            if (slot >= size) {
                break;
            }
            inventory.setItem(slot, item);
            slot++;
        }
        return inventory;
    }

    private static int roundUpToNine(int count) {
        if (count == 0) {
            return 9;
        }
        return ((count + 8) / 9) * 9;
    }
}
