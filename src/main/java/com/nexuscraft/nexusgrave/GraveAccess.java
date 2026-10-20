package com.nexuscraft.nexusgrave;

/** Who may do what to a grave. Pure rules, kept apart from Bukkit so they can be tested. */
public final class GraveAccess {

    /** Permission held by operators by default: open anyone's grave, and break an active marker. */
    public static final String STAFF_PERMISSION = "nexusgrave.staff";

    private GraveAccess() {
    }

    /** May this player open the grave's menu? With owner-only off, anyone may (the old robbing rule). */
    public static boolean canOpen(boolean ownerOnly, boolean isOwner, boolean isStaff) {
        return !ownerOnly || isOwner || isStaff;
    }

    /** Is a break/explosion against this grave block blocked? Only an active grave (items still
     *  inside, not collapsed) is protected; once it is emptied or has collapsed it is just a
     *  cobblestone block anyone may clear. */
    public static boolean markerProtected(boolean protectMarkers, boolean graveIsActive, boolean isStaff) {
        return protectMarkers && graveIsActive && !isStaff;
    }

    public static boolean withinRange(double distanceSquared, int radius) {
        return radius >= 0 && distanceSquared <= (double) radius * radius;
    }
}
