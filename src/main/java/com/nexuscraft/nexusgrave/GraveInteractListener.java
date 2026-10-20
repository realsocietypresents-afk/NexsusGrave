package com.nexuscraft.nexusgrave;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Right-click resolution for a grave marker: reads its epitaph to whoever clicks it (owner or
 * not -- an epitaph is meant to be found, same as vanilla's own death message would have been),
 * then either consumes the real "rite of passage" item (if the owner is holding one) or asks
 * {@link GraveOpener} to open the lootable {@link GraveMenu}. Since v0.3.0 only the owner and staff
 * (permission {@code nexusgrave.staff}) can open a grave, unless {@code access.owner-only} is off.
 * Permanence is owner-only, as before: it is an earned insurance a player buys for their own death.
 */
public final class GraveInteractListener implements Listener {

    private final GraveConfig config;
    private final GraveRegistry registry;
    private final GraveDecayService decayService;
    private final GraveOpener opener;

    public GraveInteractListener(GraveConfig config, GraveRegistry registry, GraveDecayService decayService, GraveOpener opener) {
        this.opener = opener;
        this.config = config;
        this.registry = registry;
        this.decayService = decayService;
    }

    /**
     * Runs at MONITOR, the last tier, because NexusSpawnShield also runs there and force-ALLOWs
     * every right-click inside its zone (which is nearly the whole map). At the normal tier our
     * cancel was simply undone, so clicking a grave also let the item in hand fire (a thrown pearl,
     * a block placement attempt that SpawnShield then answered with "you can't place here").
     * NexusSpawnShield is a softdepend in plugin.yml so this plugin registers after it and
     * therefore runs after it at the same tier.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.hasBlock()) {
            return;
        }
        Block clicked = event.getClickedBlock();
        GraveRecord record = registry.findByBlock(clicked.getLocation());
        if (record == null) {
            // Might have clicked the PLAYER_HEAD topper instead of the base marker block.
            record = registry.findByBlock(clicked.getRelative(0, -1, 0).getLocation());
        }
        if (record == null || record.isCollapsed()) {
            return;
        }

        Player player = event.getPlayer();
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        player.sendMessage("§7\"" + record.effectiveEpitaph() + "\"");

        boolean isOwner = record.ownerId().equals(player.getUniqueId());
        ItemStack inHand = event.getItem();
        if (isOwner && !record.isPermanent() && inHand != null && inHand.getType() == config.permanenceRiteItem) {
            consumeOne(player, inHand);
            decayService.grantPermanence(record);
            player.sendMessage("§8[Grave] §fThe rite holds -- this grave will never decay or collapse. "
                    + "§7(It can still be found and looted by anyone before you reclaim it.)");
            return;
        }

        opener.tryOpen(player, record);
    }

    private void consumeOne(Player player, ItemStack inHand) {
        if (inHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            inHand.setAmount(inHand.getAmount() - 1);
            player.getInventory().setItemInMainHand(inHand);
        }
    }
}
