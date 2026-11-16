package de.chronos.entity;

import java.awt.Color;

/**
 * Telegraphierte Gefahren- oder Wirkungszone am Boden (z.B. Meteorsturm, Boss-Slam).
 */
public final class HazardZone {
    public final double x;
    public final double y;
    public final double radius;
    public final double totalDelay;
    public double remainingDelay;
    public final double damage;
    public final boolean hurtsPlayer;
    public final Color color;
    public boolean detonated = false;

    public HazardZone(double x, double y, double radius, double delay, double damage, boolean hurtsPlayer, Color color) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.totalDelay = delay;
        this.remainingDelay = delay;
        this.damage = damage;
        this.hurtsPlayer = hurtsPlayer;
        this.color = color;
    }

    public double progress() {
        return 1.0 - Math.max(0.0, Math.min(1.0, remainingDelay / totalDelay));
    }
}
