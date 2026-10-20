package com.nexuscraft.nexusgrave;

import java.util.UUID;

/**
 * A grave's own identity -- a random {@link UUID}, not a location. Unlike {@code PortalKey} in
 * this family's own NexusPortals (whose identity IS its anchor coordinate, since a portal frame is
 * a fixed, one-of-a-kind physical structure), a grave's location is NOT a safe identity: the same
 * player (or two different players) can genuinely die at the exact same block twice, and this
 * project deliberately allows every death to leave its own real, separate grave rather than
 * silently merging or overwriting one at the same spot -- so identity has to come from somewhere
 * else entirely.
 */
public record GraveKey(UUID id) {

    public static GraveKey newKey() {
        return new GraveKey(UUID.randomUUID());
    }

    public String toStorageKey() {
        return id.toString();
    }

    public static GraveKey fromStorageKey(String key) {
        try {
            return new GraveKey(UUID.fromString(key));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
