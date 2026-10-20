package com.nexuscraft.nexusgrave;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Drives the real listeners against a small in-memory world. Plain main, no JUnit. */
public final class ListenerTest {

    private static int checks = 0;
    private static int failures = 0;

    /** A world that remembers its blocks; everything starts as air. */
    static final class MemWorld extends World {
        final Map<String, Block> blocks = new HashMap<>();
        MemWorld(String name) { super(name); }
        @Override
        public Block getBlockAt(int x, int y, int z) {
            return blocks.computeIfAbsent(x + "," + y + "," + z, k -> {
                Block b = new Block(this, x, y, z);
                b.setType(Material.AIR);
                return b;
            });
        }
        void fillBox(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
            for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) getBlockAt(x, y, z).setType(m);
        }
    }

    static class Tester extends Player {
        final String name; final Location where; final List<String> said = new ArrayList<>();
        boolean online = true; int menus = 0; ItemStack hand; boolean staff = false; Inventory lastOpened;
        Tester(String name, Location where) { this.name = name; this.where = where; }
        @Override public String getName() { return name; }
        @Override public Location getLocation() { return where; }
        @Override public void sendMessage(String m) { said.add(m); }
        @Override public boolean isOnline() { return online; }
        @Override public boolean hasPermission(String node) { return staff && node.equals(GraveAccess.STAFF_PERMISSION); }
        @Override public void openInventory(Inventory inv) { menus++; lastOpened = inv; }
    }

    static JavaPlugin plugin(File dir) {
        return new JavaPlugin() { @Override public File getDataFolder() { return dir; } };
    }

    static final class Rig {
        final MemWorld world = new MemWorld("world");
        final GraveConfig config;
        final GraveRegistry registry;
        final GraveStructureService structure = new GraveStructureService();
        final GraveDeathListener death;
        final GraveInteractListener interact;
        final GraveProtectionListener protect;
        final GraveOpener opener;
        final JavaPlugin plugin;
        Rig() throws Exception {
            Bukkit.WORLDS.put("world", world);
            BukkitScheduler.QUEUED.clear();
            File dir = Files.createTempDirectory("ng").toFile();
            plugin = plugin(dir);
            config = new GraveConfig(plugin);
            config.load(plugin.getLogger());
            registry = new GraveRegistry(plugin);
            GraveDecayService decay = new GraveDecayService(config, registry, structure);
            CorpseRunService run = new CorpseRunService(config, registry);
            death = new GraveDeathListener(plugin, config, registry, structure, run);
            opener = new GraveOpener(config, registry);
            interact = new GraveInteractListener(config, registry, decay, opener);
            protect = new GraveProtectionListener(config, registry, opener);
        }
        Tester at(String name, int x, int y, int z) { return new Tester(name, new Location(world, x + 0.5, y, z + 0.5)); }
        PlayerDeathEvent die(Tester t, int items, int exp) {
            List<ItemStack> drops = new ArrayList<>();
            for (int i = 0; i < items; i++) drops.add(new ItemStack(Material.STONE, 1));
            PlayerDeathEvent e = new PlayerDeathEvent(t, drops, exp, Component.text(t.name + " was slain"));
            death.onPlayerDeath(e);
            return e;
        }
    }

    public static void main(String[] args) throws Exception {
        openAirDeath();
        deathInsideSolidBuild();
        noRoomKeepsItems();
        twoDeathsSameSpot();
        clickGrave();
        offHandAndNonGraveClicks();
        respawnMessage();
        riteAndKeepInventory();
        ownerAndStaffOnly();
        lockAndRelease();
        markerProtection();
        explosions();
        retrieveCommand();
        System.out.println(checks + " checks, " + failures + " failed");
        if (failures > 0) System.exit(1);
    }

    private static void openAirDeath() throws Exception {
        Rig r = new Rig();
        Tester t = r.at("Ann", 10, 70, 20);
        PlayerDeathEvent e = r.die(t, 3, 40);
        expect(e.getDrops().isEmpty() && e.getDroppedExp() == 0, "open air: vanilla drops and xp are taken");
        expect(r.registry.all().size() == 1, "one grave registered");
        GraveRecord g = r.registry.all().iterator().next();
        expect(g.x() == 10 && g.y() == 70 && g.z() == 20, "grave is exactly at the death block");
        expect(r.world.getBlockAt(10, 70, 20).getType() == Material.COBBLESTONE, "base marker placed");
        expect(r.world.getBlockAt(10, 71, 20).getType() == Material.PLAYER_HEAD, "head placed on top");
        expect(g.items().size() == 3 && g.droppedExp() == 40, "items and xp sealed in the record");
    }

    private static void deathInsideSolidBuild() throws Exception {
        Rig r = new Rig();
        // A protected 5x5x5 stone build with the player "inside" a solid block (phased in).
        r.world.fillBox(8, 68, 8, 12, 72, 12, Material.STONE);
        r.world.getBlockAt(10, 70, 10).setType(Material.CHEST);
        Tester t = r.at("Bea", 10, 70, 10);
        r.die(t, 2, 0);
        GraveRecord g = r.registry.all().iterator().next();
        expect(r.world.getBlockAt(10, 70, 10).getType() == Material.CHEST, "the chest at the death block is NOT overwritten");
        expect(r.world.getBlockAt(10, 71, 10).getType() == Material.STONE, "the wall above is NOT overwritten");
        expect(!(g.x() == 10 && g.y() == 70 && g.z() == 10), "grave moved off the solid block");
        expect(r.world.getBlockAt(g.x(), g.y(), g.z()).getType() == Material.COBBLESTONE, "marker is at the record's own coordinates");
        expect(r.world.getBlockAt(g.x(), g.y() + 1, g.z()).getType() == Material.PLAYER_HEAD, "head is at the record's own coordinates");
        int solidBefore = 0;
        for (int x = 8; x <= 12; x++) for (int y = 68; y <= 72; y++) for (int z = 8; z <= 12; z++) {
            Material m = r.world.getBlockAt(x, y, z).getType();
            if (m == Material.STONE || m == Material.CHEST) solidBefore++;
        }
        expect(solidBefore == 125, "all 125 blocks of the build are untouched (" + solidBefore + ")");
        expect(r.registry.findByBlock(new Location(r.world, g.x(), g.y(), g.z())) == g, "the moved grave can be found by its block");
    }

    private static void noRoomKeepsItems() throws Exception {
        Rig r = new Rig();
        r.world.fillBox(-30, 40, -30, 30, 100, 30, Material.STONE);
        Tester t = r.at("Cy", 0, 70, 0);
        PlayerDeathEvent e = r.die(t, 4, 55);
        expect(e.getDrops().size() == 4, "no room: items stay in vanilla's drops");
        expect(e.getDroppedExp() == 55, "no room: xp stays vanilla");
        expect(r.registry.all().isEmpty(), "no room: no grave record");
        expect(t.said.size() == 1 && t.said.get(0).equals(GraveTexts.NO_ROOM), "no room: player is told");
        expect(r.world.getBlockAt(0, 70, 0).getType() == Material.STONE, "no room: nothing overwritten");
    }

    private static void twoDeathsSameSpot() throws Exception {
        Rig r = new Rig();
        Tester t = r.at("Di", 5, 64, 5);
        r.die(t, 1, 0);
        r.die(t, 2, 0);
        expect(r.registry.all().size() == 2, "two graves");
        boolean distinct = true;
        for (GraveRecord g : r.registry.all()) {
            GraveRecord found = r.registry.findByBlock(new Location(r.world, g.x(), g.y(), g.z()));
            if (found != g) distinct = false;
        }
        expect(distinct, "each grave is found by its own block, the second did not take over the first");
    }

    private static void clickGrave() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Eli", 3, 70, 3);
        r.die(owner, 2, 0);
        Block marker = r.world.getBlockAt(3, 70, 3);
        Tester other = owner; // only the owner can open it now
        // Simulate NexusSpawnShield having run first: it un-cancels and force-ALLOWs.
        PlayerInteractEvent e = new PlayerInteractEvent(other, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.ENDER_PEARL), marker);
        e.setCancelled(false);
        e.setUseInteractedBlock(Event.Result.ALLOW);
        e.setUseItemInHand(Event.Result.ALLOW);
        r.interact.onPlayerInteract(e);
        expect(other.menus == 1, "click opens the grave menu");
        expect(e.isCancelled(), "click ends up cancelled even after a force-allow");
        expect(e.useItemInHand() == Event.Result.DENY, "held item (pearl) is denied, it won't be thrown");
        expect(e.useInteractedBlock() == Event.Result.DENY, "block use is denied");
        expect(other.said.stream().anyMatch(m -> m.contains("was slain")), "epitaph is shown");
        // Clicking the head on top works too.
        Tester third = r.at("Gus", 60, 70, 60);
        third.staff = true; // staff can open anyone's
        r.registry.releaseMenuLock(r.registry.all().iterator().next().key()); // first viewer closed
        PlayerInteractEvent head = new PlayerInteractEvent(third, Action.RIGHT_CLICK_BLOCK, null, r.world.getBlockAt(3, 71, 3));
        r.interact.onPlayerInteract(head);
        expect(third.menus == 1 && head.isCancelled(), "clicking the head opens it too");
    }

    private static void offHandAndNonGraveClicks() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Hal", 3, 70, 3);
        r.die(owner, 1, 0);
        Tester p = r.at("Ivy", 9, 70, 9);
        PlayerInteractEvent off = new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK, null, r.world.getBlockAt(3, 70, 3), EquipmentSlot.OFF_HAND);
        r.interact.onPlayerInteract(off);
        expect(p.menus == 0 && !off.isCancelled(), "off-hand click is ignored (no double open)");
        r.world.getBlockAt(20, 70, 20).setType(Material.STONE);
        PlayerInteractEvent other = new PlayerInteractEvent(p, Action.RIGHT_CLICK_BLOCK, null, r.world.getBlockAt(20, 70, 20));
        other.setUseItemInHand(Event.Result.ALLOW);
        r.interact.onPlayerInteract(other);
        expect(p.menus == 0 && !other.isCancelled() && other.useItemInHand() == Event.Result.ALLOW, "a normal block is left completely alone");
        PlayerInteractEvent left = new PlayerInteractEvent(p, Action.LEFT_CLICK_BLOCK, null, r.world.getBlockAt(3, 70, 3));
        r.interact.onPlayerInteract(left);
        expect(p.menus == 0 && !left.isCancelled(), "left-click is left alone");
    }

    private static void respawnMessage() throws Exception {
        Rig r = new Rig();
        Tester t = r.at("Jo", 100, 70, 200);
        r.die(t, 1, 0);
        Location spawn = new Location(r.world, 100.5, 64, 230.5);
        BukkitScheduler.QUEUED.clear();
        r.death.onPlayerRespawn(new PlayerRespawnEvent(t, spawn));
        expect(BukkitScheduler.QUEUED.size() == 1, "respawn queues one delayed message");
        BukkitScheduler.QUEUED.forEach(Runnable::run);
        String last = t.said.get(t.said.size() - 1);
        expect(last.contains("100, 70, 200"), "message has the grave coordinates");
        expect(last.contains("about §7"), "message has the distance");
        BukkitScheduler.QUEUED.clear();
        t.said.clear();
        t.online = false;
        r.death.onPlayerRespawn(new PlayerRespawnEvent(t, spawn));
        BukkitScheduler.QUEUED.forEach(Runnable::run);
        expect(t.said.isEmpty(), "a player who logged off before the tick is not messaged");
        t.online = true;
        r.config.notifyOnRespawn = false;
        BukkitScheduler.QUEUED.clear();
        r.death.onPlayerRespawn(new PlayerRespawnEvent(t, spawn));
        expect(BukkitScheduler.QUEUED.isEmpty(), "notify.on-respawn: false sends nothing");
        r.config.notifyOnRespawn = true;
        Tester nobody = r.at("Kit", 0, 70, 0);
        BukkitScheduler.QUEUED.clear();
        r.death.onPlayerRespawn(new PlayerRespawnEvent(nobody, spawn));
        expect(BukkitScheduler.QUEUED.isEmpty(), "a player with no grave gets no message");
    }

    private static void riteAndKeepInventory() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Lou", 3, 70, 3);
        PlayerDeathEvent died = new PlayerDeathEvent(owner, new ArrayList<>(List.of(new ItemStack(Material.STONE, 1))), 0, Component.text("x"));
        died.setKeepInventory(true);
        r.death.onPlayerDeath(died);
        expect(!died.getKeepInventory(), "keep-inventory is switched off so the grave gets the items");
        GraveRecord g = r.registry.all().iterator().next();
        Block marker = r.world.getBlockAt(3, 70, 3);

        ItemStack two = new ItemStack(Material.NETHER_STAR, 2);
        PlayerInteractEvent e = new PlayerInteractEvent(owner, Action.RIGHT_CLICK_BLOCK, two, marker);
        r.interact.onPlayerInteract(e);
        expect(g.isPermanent(), "owner + rite item: grave becomes permanent");
        expect(owner.menus == 0, "rite click does not also open the menu");
        expect(two.getAmount() == 1, "rite consumes one item from a stack of two");
        expect(owner.getInventory().mainHandWasSet && owner.getInventory().lastSetMainHand == two, "the reduced stack is put back in hand");

        Rig r2 = new Rig();
        Tester o2 = r2.at("Max", 3, 70, 3);
        r2.die(o2, 1, 0);
        ItemStack one = new ItemStack(Material.NETHER_STAR, 1);
        r2.interact.onPlayerInteract(new PlayerInteractEvent(o2, Action.RIGHT_CLICK_BLOCK, one, r2.world.getBlockAt(3, 70, 3)));
        expect(o2.getInventory().mainHandWasSet && o2.getInventory().lastSetMainHand == null, "a single rite item leaves the hand empty");

        Rig r3 = new Rig();
        Tester o3 = r3.at("Ned", 3, 70, 3);
        r3.die(o3, 1, 0);
        Tester thief = r3.at("Oz", 9, 70, 9);
        thief.staff = true; // even staff can't make someone else's grave permanent with the rite item
        r3.interact.onPlayerInteract(new PlayerInteractEvent(thief, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.NETHER_STAR, 1), r3.world.getBlockAt(3, 70, 3)));
        expect(!r3.registry.all().iterator().next().isPermanent() && thief.menus == 1, "someone else's rite item does nothing, they just see the menu (staff here)");
        Rig r4 = new Rig();
        Tester o4 = r4.at("Pam", 3, 70, 3);
        r4.die(o4, 1, 0);
        r4.interact.onPlayerInteract(new PlayerInteractEvent(o4, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.STONE, 1), r4.world.getBlockAt(3, 70, 3)));
        expect(!r4.registry.all().iterator().next().isPermanent() && o4.menus == 1, "owner holding a non-rite item just opens the menu");
        r4.registry.all().iterator().next().setPermanent(true);
        r4.interact.onPlayerInteract(new PlayerInteractEvent(o4, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.NETHER_STAR, 1), r4.world.getBlockAt(3, 70, 3)));
        expect(o4.menus == 2, "an already-permanent grave does not eat another rite item");
    }

    private static PlayerInteractEvent click(Tester t, Block b) {
        return new PlayerInteractEvent(t, Action.RIGHT_CLICK_BLOCK, null, b);
    }

    private static void ownerAndStaffOnly() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Quin", 3, 70, 3);
        r.die(owner, 2, 0);
        Block marker = r.world.getBlockAt(3, 70, 3);
        Tester stranger = r.at("Rex", 9, 70, 9);
        PlayerInteractEvent e = click(stranger, marker);
        r.interact.onPlayerInteract(e);
        expect(stranger.menus == 0, "a default player cannot open someone else's grave");
        expect(e.isCancelled(), "...and the click is still cancelled");
        expect(stranger.said.stream().anyMatch(m -> m.contains("Quin") && m.contains("only they")), "...they are told whose grave it is");
        expect(stranger.said.stream().anyMatch(m -> m.contains("was slain")), "...but can still read the epitaph");
        expect(r.registry.all().iterator().next().items().size() == 2, "...and the items are untouched");

        Tester op = r.at("Sol", 9, 70, 9);
        op.staff = true;
        r.interact.onPlayerInteract(click(op, marker));
        expect(op.menus == 1, "staff can open anyone's grave");
        r.registry.releaseMenuLock(r.registry.all().iterator().next().key());

        r.interact.onPlayerInteract(click(owner, marker));
        expect(owner.menus == 1, "the owner can open their own grave");
        r.registry.releaseMenuLock(r.registry.all().iterator().next().key());

        r.config.ownerOnly = false;
        r.interact.onPlayerInteract(click(stranger, marker));
        expect(stranger.menus == 1, "access.owner-only: false brings back open robbing");
        expect(GraveAccess.withinRange(0, 0) && !GraveAccess.withinRange(1, 0) && GraveAccess.withinRange(64, 8) && !GraveAccess.withinRange(65, 8) && !GraveAccess.withinRange(0, -1), "range rule: radius 0 means exactly the grave's block; negative never");
        expect(GraveAccess.canOpen(true, false, false) == false && GraveAccess.canOpen(true, true, false)
                && GraveAccess.canOpen(true, false, true) && GraveAccess.canOpen(false, false, false), "access rule table");
    }

    private static void lockAndRelease() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Tia", 3, 70, 3);
        r.die(owner, 2, 0);
        Block marker = r.world.getBlockAt(3, 70, 3);
        Tester op = r.at("Uma", 9, 70, 9);
        op.staff = true;
        r.interact.onPlayerInteract(click(owner, marker));
        expect(owner.menus == 1, "owner opens it");
        r.interact.onPlayerInteract(click(op, marker));
        expect(op.menus == 0, "while the owner has it open, a second viewer is turned away");
        expect(op.said.stream().anyMatch(m -> m.contains("already going through")), "...with a message");
        // owner closes
        new GraveMenuListener(r.registry).onInventoryClose(new org.bukkit.event.inventory.InventoryCloseEvent(owner, owner.lastOpened));
        r.interact.onPlayerInteract(click(op, marker));
        expect(op.menus == 1, "after the first viewer closes, the next can open");
        GraveKey k = r.registry.all().iterator().next().key();
        expect(r.registry.tryLockMenu(k, op.getUniqueId(), System.currentTimeMillis()), "the holder can re-open their own");
        expect(!r.registry.tryLockMenu(k, owner.getUniqueId(), System.currentTimeMillis()), "another viewer is refused");
        expect(r.registry.tryLockMenu(k, owner.getUniqueId(), System.currentTimeMillis() + GraveRegistry.MENU_LOCK_TTL_MILLIS), "a lock whose close never arrived expires");
        expect(!r.registry.tryLockMenu(k, op.getUniqueId(), System.currentTimeMillis() + GraveRegistry.MENU_LOCK_TTL_MILLIS - 1000), "...but not a second early");
    }

    private static void markerProtection() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Vic", 3, 70, 3);
        r.die(owner, 1, 0);
        GraveRecord g = r.registry.all().iterator().next();
        Tester stranger = r.at("Wyn", 9, 70, 9);
        BlockBreakEvent base = new BlockBreakEvent(r.world.getBlockAt(3, 70, 3), stranger);
        r.protect.onBreak(base);
        expect(base.isCancelled(), "a stranger cannot break an active marker");
        BlockBreakEvent head = new BlockBreakEvent(r.world.getBlockAt(3, 71, 3), stranger);
        r.protect.onBreak(head);
        expect(head.isCancelled(), "...nor the head on top");
        BlockBreakEvent own = new BlockBreakEvent(r.world.getBlockAt(3, 70, 3), owner);
        r.protect.onBreak(own);
        expect(own.isCancelled(), "...nor the owner");
        Tester op = r.at("Xan", 9, 70, 9);
        op.staff = true;
        BlockBreakEvent staff = new BlockBreakEvent(r.world.getBlockAt(3, 70, 3), op);
        r.protect.onBreak(staff);
        expect(!staff.isCancelled(), "staff can break a marker");
        r.world.getBlockAt(8, 70, 8).setType(Material.STONE);
        BlockBreakEvent plain = new BlockBreakEvent(r.world.getBlockAt(8, 70, 8), stranger);
        r.protect.onBreak(plain);
        expect(!plain.isCancelled(), "ordinary blocks are not touched");
        BlockBreakEvent above = new BlockBreakEvent(r.world.getBlockAt(3, 72, 3), stranger);
        r.protect.onBreak(above);
        expect(!above.isCancelled(), "the block above the head is not part of the grave");
        r.config.protectMarkers = false;
        BlockBreakEvent off = new BlockBreakEvent(r.world.getBlockAt(3, 70, 3), stranger);
        r.protect.onBreak(off);
        expect(!off.isCancelled(), "access.protect-markers: false turns protection off");
        r.config.protectMarkers = true;
        g.setClaimed(true);
        BlockBreakEvent emptied = new BlockBreakEvent(r.world.getBlockAt(3, 70, 3), stranger);
        r.protect.onBreak(emptied);
        expect(!emptied.isCancelled(), "an emptied grave's marker can be cleared by anyone");
        g.setClaimed(false);
        g.setCollapsed(true);
        BlockBreakEvent gone = new BlockBreakEvent(r.world.getBlockAt(3, 70, 3), stranger);
        r.protect.onBreak(gone);
        expect(!gone.isCancelled(), "a collapsed grave's marker can be cleared by anyone");
        expect(GraveAccess.markerProtected(true, true, false) && !GraveAccess.markerProtected(true, true, true)
                && !GraveAccess.markerProtected(false, true, false) && !GraveAccess.markerProtected(true, false, false), "marker rule table");
    }

    private static void explosions() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Yas", 3, 70, 3);
        r.die(owner, 1, 0);
        r.world.getBlockAt(5, 70, 5).setType(Material.STONE);
        List<Block> list = new ArrayList<>(List.of(r.world.getBlockAt(3, 70, 3), r.world.getBlockAt(3, 71, 3), r.world.getBlockAt(5, 70, 5)));
        r.protect.onEntityExplode(new org.bukkit.event.entity.EntityExplodeEvent(null, list, 1f));
        expect(list.size() == 1 && list.get(0).getX() == 5, "a creeper/TNT blast keeps the grave blocks and still breaks the rest");
        List<Block> list2 = new ArrayList<>(List.of(r.world.getBlockAt(3, 70, 3), r.world.getBlockAt(5, 70, 5)));
        r.protect.onBlockExplode(new org.bukkit.event.block.BlockExplodeEvent(r.world.getBlockAt(5, 70, 5), list2, 1f));
        expect(list2.size() == 1 && list2.get(0).getX() == 5, "a bed/anchor blast keeps the grave blocks");
        r.config.protectMarkers = false;
        List<Block> list3 = new ArrayList<>(List.of(r.world.getBlockAt(3, 70, 3)));
        r.protect.onEntityExplode(new org.bukkit.event.entity.EntityExplodeEvent(null, list3, 1f));
        expect(list3.size() == 1, "protection off: blast list untouched");
    }

    private static void retrieveCommand() throws Exception {
        Rig r = new Rig();
        Tester owner = r.at("Zed", 3, 70, 3);
        r.die(owner, 2, 0);
        GraveCommand cmd = new GraveCommand(r.config, r.registry, r.opener, () -> { });
        org.bukkit.command.Command c = new org.bukkit.command.Command();
        // standing right at the grave, marker deleted by something
        r.world.getBlockAt(3, 70, 3).setType(Material.AIR);
        r.world.getBlockAt(3, 71, 3).setType(Material.AIR);
        cmd.onCommand(owner, c, "grave", new String[] {"retrieve"});
        expect(owner.menus == 1, "retrieve opens your grave even though its marker is gone");
        r.registry.releaseMenuLock(r.registry.all().iterator().next().key());

        Tester farOwner = new Tester("Zed", new Location(r.world, 103.5, 70, 3.5)) {
            @Override public java.util.UUID getUniqueId() { return owner.getUniqueId(); }
        };
        cmd.onCommand(farOwner, c, "grave", new String[] {"retrieve"});
        expect(farOwner.menus == 0, "too far away: retrieve does not open it");
        expect(farOwner.said.stream().anyMatch(m -> m.contains("within") && m.contains("3, 70, 3")), "...and says where the grave is");

        Tester edge = new Tester("Zed", new Location(r.world, 3.5 + 8, 70, 3.5)) {
            @Override public java.util.UUID getUniqueId() { return owner.getUniqueId(); }
        };
        cmd.onCommand(edge, c, "grave", new String[] {"retrieve"});
        expect(edge.menus == 1, "exactly at the radius edge it works (8 blocks)");
        r.registry.releaseMenuLock(r.registry.all().iterator().next().key());
        Tester beyond = new Tester("Zed", new Location(r.world, 3.5 + 9, 70, 3.5)) {
            @Override public java.util.UUID getUniqueId() { return owner.getUniqueId(); }
        };
        cmd.onCommand(beyond, c, "grave", new String[] {"retrieve"});
        expect(beyond.menus == 0, "one block past the radius it does not");

        Tester stranger = r.at("Nobody", 3, 70, 3);
        cmd.onCommand(stranger, c, "grave", new String[] {"retrieve"});
        expect(stranger.menus == 0 && stranger.said.stream().anyMatch(m -> m.contains("no unclaimed grave")), "retrieve only ever considers your OWN graves");

        GraveRecord g = r.registry.all().iterator().next();
        g.setClaimed(true);
        cmd.onCommand(owner, c, "grave", new String[] {"retrieve"});
        expect(owner.menus == 1, "an emptied grave is not offered again");
        expect(owner.said.get(owner.said.size() - 1).contains("no unclaimed grave"), "...you are told you have none");
        cmd.onCommand(owner, c, "grave", new String[] {});
        expect(owner.said.get(owner.said.size() - 1).contains("retrieve"), "usage lists retrieve");
    }

    private static void expect(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FAILED: " + what); }
    }
}
