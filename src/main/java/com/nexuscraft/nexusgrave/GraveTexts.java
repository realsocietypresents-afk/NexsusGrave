package com.nexuscraft.nexusgrave;

/** Player-facing wording that needs a little arithmetic, kept separate so it can be tested. */
public final class GraveTexts {

    private GraveTexts() {
    }

    /**
     * The line shown on respawn: where the grave is, how far, and what to do. Distance is only
     * given when the player is in the same world; otherwise the world name is shown instead.
     */
    public static String whereIsMyGrave(String graveWorld, int gx, int gy, int gz,
                                        String fromWorld, double fx, double fy, double fz) {
        StringBuilder sb = new StringBuilder("§8[Grave] §fYour grave is at §7")
                .append(gx).append(", ").append(gy).append(", ").append(gz);
        if (graveWorld != null && graveWorld.equals(fromWorld)) {
            double dx = gx + 0.5 - fx;
            double dy = gy - fy;
            double dz = gz + 0.5 - fz;
            long blocks = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
            sb.append("§f (about §7").append(blocks).append("§f block").append(blocks == 1 ? "" : "s").append(" away).");
        } else {
            sb.append("§f in §7").append(graveWorld).append("§f.");
        }
        sb.append(" §7Your compass points to it -- right-click the cobblestone marker to get your things back.");
        return sb.toString();
    }

    public static final String NO_ROOM =
            "§8[Grave] §cThere was no free space nearby for a grave, so your items dropped normally. "
                    + "Grab them quickly.";
}
