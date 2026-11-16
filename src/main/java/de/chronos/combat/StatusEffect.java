package de.chronos.combat;

/**
 * Temporäre Statuseffekte auf Entitäten (Verbrennung, Gift, Verlangsamung, Betäubung, Rausch).
 */
public final class StatusEffect {
    public enum Type {
        BURN,
        POISON,
        SLOW,
        STUN,
        FRENZY
    }

    public final Type type;
    public double remainingSeconds;
    public double magnitude;
    public double tickAccumulator;

    public StatusEffect(Type type, double durationSeconds, double magnitude) {
        this.type = type;
        this.remainingSeconds = durationSeconds;
        this.magnitude = magnitude;
        this.tickAccumulator = 0.0;
    }

    public void refresh(double durationSeconds, double magnitude) {
        this.remainingSeconds = Math.max(this.remainingSeconds, durationSeconds);
        this.magnitude = Math.max(this.magnitude, magnitude);
    }
}
