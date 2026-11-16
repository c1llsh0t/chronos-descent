package de.chronos.entity;

/**
 * Interaktive Dungeon-Objekte (Schatztruhen, Heilschreine, Relikt-Altäre, Ausgangsportale).
 */
public final class InteractiveProp {
    public enum PropType {
        CHEST,
        HEALING_SHRINE,
        RELIC_ALTAR,
        EXIT_PORTAL
    }

    public final double x;
    public final double y;
    public final PropType type;
    public boolean used = false;
    public double pulseTime = 0.0;

    public InteractiveProp(double x, double y, PropType type) {
        this.x = x;
        this.y = y;
        this.type = type;
    }
}
