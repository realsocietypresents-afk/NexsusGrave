package com.nexuscraft.nexusgrave;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single grave's full real state -- everything sealed away at the moment of death, plus
 * everything this project tracks about it afterward. Mutable on purpose (mirrors {@code
 * PortalRecord} in this family's own NexusPortals): decay ticks down, an epitaph can be written
 * after the fact, and the claimed/collapsed/permanent flags all flip in place across a grave's
 * real lifetime rather than requiring a fresh record every time something about it changes.
 *
 * <p>Location is stored as raw world name + coordinates, not a live {@link Location}, for the
 * same reason {@code PortalRecord} does it that way: a {@link World} reference doesn't survive a
 * server restart/world reload cleanly, but a name does, and {@link #location()} reconstructs a
 * fresh {@link Location} from the raw fields on demand (returning {@code null} if the named world
 * isn't currently loaded, exactly like {@code PortalRecord#location()}).
 */
public final class GraveRecord {

    private final GraveKey key;
    private final UUID ownerId;
    private final String ownerName;
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final long diedAtMillis;
    private final String deathMessage;

    private String customEpitaph;
    private final List<ItemStack> items = new ArrayList<>();
    private int droppedExp;
    private double decay = 100.0;
    private boolean permanent;
    private boolean collapsed;
    private boolean claimed;

    public GraveRecord(GraveKey key, UUID ownerId, String ownerName, String worldName,
                        int x, int y, int z, long diedAtMillis, String deathMessage) {
        this.key = key;
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.diedAtMillis = diedAtMillis;
        this.deathMessage = deathMessage;
    }

    public GraveKey key() {
        return key;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String ownerName() {
        return ownerName;
    }

    public String worldName() {
        return worldName;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public long diedAtMillis() {
        return diedAtMillis;
    }

    /** Vanilla's own real death message ("Steve was slain by Zombie"), already plain text --
     *  this is what a grave shows when the owner never wrote a custom epitaph. */
    public String deathMessage() {
        return deathMessage;
    }

    public String customEpitaph() {
        return customEpitaph;
    }

    public void setCustomEpitaph(String customEpitaph) {
        this.customEpitaph = customEpitaph;
    }

    /** The epitaph actually shown: a pre-written custom one if the owner set one before this
     *  death, otherwise vanilla's own real death message as the honest default. */
    public String effectiveEpitaph() {
        return (customEpitaph != null && !customEpitaph.isBlank()) ? customEpitaph : deathMessage;
    }

    public List<ItemStack> items() {
        return items;
    }

    public int droppedExp() {
        return droppedExp;
    }

    public void setDroppedExp(int droppedExp) {
        this.droppedExp = droppedExp;
    }

    public double decay() {
        return decay;
    }

    public void setDecay(double decay) {
        this.decay = Math.max(0.0, Math.min(100.0, decay));
    }

    public GraveCondition condition() {
        return GraveCondition.fromDecay(decay);
    }

    /** A permanent grave never decays, full stop -- set once, at the graveside, by consuming the
     *  real "rite of passage" item (a PDC-tagged Nether Star). This does NOT protect the grave
     *  from being looted by anyone else; it only stops the decay clock. See this class's own
     *  header comment and GraveCondition's for why those are two separate, orthogonal risks. */
    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    /** Whether the owner (or anyone else, since robbing is real and unguarded) has already
     *  opened and emptied this grave. A claimed grave keeps existing as a historical marker but
     *  no longer holds anything worth returning for. */
    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    /** Reconstructs a live {@link Location} from the raw stored fields, or {@code null} if the
     *  named world isn't currently loaded -- mirrors {@code PortalRecord#location()} exactly. */
    public Location location() {
        World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x + 0.5, y, z + 0.5);
    }
}
