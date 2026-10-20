package com.nexuscraft.nexusgrave;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * The persistent grave catalog -- every grave this plugin has ever sealed, disk-backed in
 * {@code grave_records.yml} (this project's own data file, separate from {@code config.yml}),
 * architecturally identical to {@code PortalRegistry} in this family's own NexusPortals: an
 * in-memory cache backed by an eager-saved file, plus a location-based block index for right-click
 * resolution. The one real structural difference is {@link GraveKey} itself -- a random UUID, not
 * an anchor coordinate, because two different graves can legitimately share a block (see
 * {@code GraveKey}'s own header comment) -- so this registry also keeps a *per-player* index of
 * that player's own most-recent unclaimed grave, which is what the corpse-run compass and debuff
 * actually bind to.
 *
 * <p>Items round-trip through real Bukkit's own {@code ConfigurationSerializable} support for
 * {@link ItemStack} -- {@code section.set("items", List<ItemStack>)} on save, and
 * {@code section.getList("items")} (each element already deserialized back into a real
 * {@code ItemStack} by real Bukkit) on load. This stub tree's own {@code YamlConfiguration} is a
 * deliberately inert mock (it never actually persists anything -- see its own file), so this
 * exact round-trip only proves itself out against a real server; it's the same real, standard
 * pattern used to persist inventories to YAML across countless real Bukkit plugins.
 */
public final class GraveRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<GraveKey, GraveRecord> records = new HashMap<>();
    /** Reverse index: a specific real grave marker block's coordinate -> which grave it belongs
     *  to. Mirrors {@code PortalRegistry#blockIndex} exactly, keyed the same "world:x:y:z" way. */
    private final Map<String, GraveKey> blockIndex = new HashMap<>();
    /** A player's own most recent unclaimed, uncollapsed grave -- what the corpse-run compass
     *  points at and what the debuff clears against. Recomputed incrementally on every register/
     *  claim/collapse rather than scanned for on every tick. */
    private final Map<UUID, GraveKey> mostRecentActiveGrave = new HashMap<>();
    /** A player's own pre-written "living will" -- set any time, well before their next death,
     *  and consumed (read, never cleared) the moment a new grave is sealed for them. Kept in this
     *  same registry/file rather than a separate class or store: it's exactly one string per
     *  player, with the same disk-backed-cache shape as everything else here. */
    private final Map<UUID, String> pendingEpitaphs = new HashMap<>();

    public GraveRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "grave_records.yml");
    }

    public void load(Logger logger) {
        records.clear();
        blockIndex.clear();
        mostRecentActiveGrave.clear();
        pendingEpitaphs.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection epitaphsSection = data.getConfigurationSection("pending-epitaphs");
        if (epitaphsSection != null) {
            for (String playerIdString : epitaphsSection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(playerIdString);
                    String epitaph = epitaphsSection.getString(playerIdString, null);
                    if (epitaph != null && !epitaph.isBlank()) {
                        pendingEpitaphs.put(playerId, epitaph);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Malformed player id -- skip just that one entry.
                }
            }
        }
        ConfigurationSection gravesSection = data.getConfigurationSection("graves");
        if (gravesSection == null) {
            return;
        }
        for (String storageKey : gravesSection.getKeys(false)) {
            GraveKey key = GraveKey.fromStorageKey(storageKey);
            if (key == null) {
                logger.warning("nexusgrave: skipping malformed grave_records.yml entry '" + storageKey + "'");
                continue;
            }
            ConfigurationSection section = gravesSection.getConfigurationSection(storageKey);
            if (section == null) {
                continue;
            }
            UUID ownerId;
            try {
                ownerId = UUID.fromString(section.getString("owner-id", ""));
            } catch (IllegalArgumentException e) {
                logger.warning("nexusgrave: skipping grave '" + storageKey + "' with malformed owner-id");
                continue;
            }
            GraveRecord record = new GraveRecord(
                    key,
                    ownerId,
                    section.getString("owner-name", "Unknown"),
                    section.getString("world", "world"),
                    section.getInt("x", 0),
                    section.getInt("y", 64),
                    section.getInt("z", 0),
                    section.getLong("died-at-millis", System.currentTimeMillis()),
                    section.getString("death-message", ""));
            record.setCustomEpitaph(section.getString("custom-epitaph", null));
            record.setDroppedExp(section.getInt("dropped-exp", 0));
            record.setDecay(section.getDouble("decay", 100.0));
            record.setPermanent(section.getBoolean("permanent", false));
            record.setCollapsed(section.getBoolean("collapsed", false));
            record.setClaimed(section.getBoolean("claimed", false));
            for (Object rawItem : section.getList("items")) {
                if (rawItem instanceof ItemStack itemStack) {
                    record.items().add(itemStack);
                }
            }
            records.put(key, record);
            reindexBlock(record);
            refreshMostRecentActive(record);
        }
    }

    public void save() {
        YamlConfiguration data = new YamlConfiguration();
        ConfigurationSection epitaphsSection = data.createSection("pending-epitaphs");
        for (Map.Entry<UUID, String> entry : pendingEpitaphs.entrySet()) {
            epitaphsSection.set(entry.getKey().toString(), entry.getValue());
        }
        ConfigurationSection gravesSection = data.createSection("graves");
        for (GraveRecord record : records.values()) {
            ConfigurationSection section = gravesSection.createSection(record.key().toStorageKey());
            section.set("owner-id", record.ownerId().toString());
            section.set("owner-name", record.ownerName());
            section.set("world", record.worldName());
            section.set("x", record.x());
            section.set("y", record.y());
            section.set("z", record.z());
            section.set("died-at-millis", record.diedAtMillis());
            section.set("death-message", record.deathMessage());
            section.set("custom-epitaph", record.customEpitaph());
            section.set("dropped-exp", record.droppedExp());
            section.set("decay", record.decay());
            section.set("permanent", record.isPermanent());
            section.set("collapsed", record.isCollapsed());
            section.set("claimed", record.isClaimed());
            section.set("items", new ArrayList<>(record.items()));
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("nexusgrave: failed to save grave_records.yml: " + e.getMessage());
        }
    }

    public void register(GraveRecord record) {
        records.put(record.key(), record);
        reindexBlock(record);
        refreshMostRecentActive(record);
        save();
    }

    private void reindexBlock(GraveRecord record) {
        blockIndex.put(record.worldName() + ":" + record.x() + ":" + record.y() + ":" + record.z(), record.key());
    }

    private void refreshMostRecentActive(GraveRecord record) {
        if (record.isClaimed() || record.isCollapsed()) {
            if (record.key().equals(mostRecentActiveGrave.get(record.ownerId()))) {
                mostRecentActiveGrave.remove(record.ownerId());
                recomputeMostRecentActive(record.ownerId());
            }
            return;
        }
        GraveKey currentMostRecent = mostRecentActiveGrave.get(record.ownerId());
        GraveRecord current = currentMostRecent != null ? records.get(currentMostRecent) : null;
        if (current == null || record.diedAtMillis() >= current.diedAtMillis()) {
            mostRecentActiveGrave.put(record.ownerId(), record.key());
        }
    }

    private void recomputeMostRecentActive(UUID ownerId) {
        GraveRecord latest = null;
        for (GraveRecord candidate : records.values()) {
            if (!candidate.ownerId().equals(ownerId) || candidate.isClaimed() || candidate.isCollapsed()) {
                continue;
            }
            if (latest == null || candidate.diedAtMillis() > latest.diedAtMillis()) {
                latest = candidate;
            }
        }
        if (latest != null) {
            mostRecentActiveGrave.put(ownerId, latest.key());
        }
    }

    /** Call this after flipping a record's claimed/collapsed flag so the per-player "most recent
     *  active grave" index (and therefore the corpse-run compass/debuff) stays correct. */
    public void onRecordStateChanged(GraveRecord record) {
        refreshMostRecentActive(record);
        save();
    }

    /** How long an open grave menu keeps its lock if the close event never arrives. */
    public static final long MENU_LOCK_TTL_MILLIS = 10L * 60L * 1000L;
    private final Map<GraveKey, long[]> menuLocks = new HashMap<>();
    private final Map<GraveKey, UUID> menuLockHolders = new HashMap<>();

    /** Takes the single-viewer lock on a grave. False if someone else (not expired) holds it. The
     *  same player re-opening their own grave is allowed. */
    public synchronized boolean tryLockMenu(GraveKey key, UUID viewer, long nowMillis) {
        long[] since = menuLocks.get(key);
        UUID holder = menuLockHolders.get(key);
        if (since != null && holder != null && !holder.equals(viewer) && nowMillis - since[0] < MENU_LOCK_TTL_MILLIS) {
            return false;
        }
        menuLocks.put(key, new long[] {nowMillis});
        menuLockHolders.put(key, viewer);
        return true;
    }

    public synchronized void releaseMenuLock(GraveKey key) {
        menuLocks.remove(key);
        menuLockHolders.remove(key);
    }

    public GraveRecord get(GraveKey key) {
        return records.get(key);
    }

    /** Which registered grave (if any) the given location's exact block belongs to -- the real
     *  hook the interact listener uses to resolve "which grave marker is this player right-
     *  clicking," mirroring {@code PortalRegistry#findByBlock}. */
    public GraveRecord findByBlock(Location location) {
        String lookupKey = location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        GraveKey key = blockIndex.get(lookupKey);
        return key != null ? records.get(key) : null;
    }

    /** The grave the corpse-run compass/debuff should track -- a player's own most recent grave
     *  that's neither been claimed nor collapsed yet, or {@code null} if they have none. */
    public GraveRecord mostRecentActiveGrave(UUID playerId) {
        GraveKey key = mostRecentActiveGrave.get(playerId);
        return key != null ? records.get(key) : null;
    }

    public Collection<GraveRecord> all() {
        return records.values();
    }

    public Collection<GraveRecord> ownedBy(UUID playerId) {
        List<GraveRecord> result = new ArrayList<>();
        for (GraveRecord record : records.values()) {
            if (record.ownerId().equals(playerId)) {
                result.add(record);
            }
        }
        return result;
    }

    /** Pre-writes (or overwrites) a player's own "living will," well ahead of their next death.
     *  Blank/null clears it, same as never having set one. */
    public void setPendingEpitaph(UUID playerId, String epitaph) {
        if (epitaph == null || epitaph.isBlank()) {
            pendingEpitaphs.remove(playerId);
        } else {
            pendingEpitaphs.put(playerId, epitaph);
        }
        save();
    }

    /** {@code null} if this player has never set one (or cleared it) -- callers fall back to
     *  vanilla's own real death message, see {@code GraveRecord#effectiveEpitaph()}. Deliberately
     *  NOT cleared on read: an epitaph is a standing instruction, reused for every future death
     *  until the player changes or clears it, not a one-shot note. */
    public String pendingEpitaph(UUID playerId) {
        return pendingEpitaphs.get(playerId);
    }
}
