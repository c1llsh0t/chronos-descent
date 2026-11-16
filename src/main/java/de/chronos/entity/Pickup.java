package de.chronos.entity;

/**
 * Einsammelbare Beute (XP-Kristalle, Goldmünzen, Heiltränke, Schild-Orbs).
 */
public final class Pickup {
    public enum Kind {
        XP_GEM,
        GOLD_COIN,
        HEALTH_POTION,
        SHIELD_ORB
    }

    public double x;
    public double y;
    public double vx;
    public double vy;
    public final Kind kind;
    public final int value;
    public double animTime;
    public boolean active = true;

    public Pickup(double x, double y, Kind kind, int value, double vx, double vy) {
        this.x = x;
        this.y = y;
        this.kind = kind;
        this.value = value;
        this.vx = vx;
        this.vy = vy;
    }
}
