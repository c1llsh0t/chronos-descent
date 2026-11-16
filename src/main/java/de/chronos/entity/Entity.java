package de.chronos.entity;

import de.chronos.combat.StatusEffect;
import de.chronos.math.Vector2D;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Abstrakte Basisklasse aller beweglichen Spielobjekte (Spieler, Gegner).
 */
public abstract class Entity {
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double knockVx;
    public double knockVy;
    public double radius;
    public double hp;
    public double maxHp;
    public boolean active = true;
    public double facingAngle = 0.0;
    public double hitFlashTimer = 0.0;
    public double animTime = 0.0;

    protected final Map<StatusEffect.Type, StatusEffect> statusEffects = new EnumMap<>(StatusEffect.Type.class);

    protected Entity(double x, double y, double radius, double maxHp) {
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public Vector2D pos() {
        return new Vector2D(x, y);
    }

    public void applyKnockback(double kx, double ky) {
        this.knockVx += kx;
        this.knockVy += ky;
    }

    public void applyStatus(StatusEffect.Type type, double duration, double magnitude) {
        StatusEffect existing = statusEffects.get(type);
        if (existing != null) {
            existing.refresh(duration, magnitude);
        } else {
            statusEffects.put(type, new StatusEffect(type, duration, magnitude));
        }
    }

    public boolean hasStatus(StatusEffect.Type type) {
        return statusEffects.containsKey(type);
    }

    public double getSpeedMultiplierFromStatus() {
        double mult = 1.0;
        if (statusEffects.containsKey(StatusEffect.Type.STUN)) {
            return 0.0;
        }
        StatusEffect slow = statusEffects.get(StatusEffect.Type.SLOW);
        if (slow != null) {
            mult *= Math.max(0.35, 1.0 - slow.magnitude);
        }
        StatusEffect frenzy = statusEffects.get(StatusEffect.Type.FRENZY);
        if (frenzy != null) {
            mult *= (1.0 + frenzy.magnitude);
        }
        return mult;
    }

    protected void updateBaseTimers(double dt, CombatWorld world) {
        animTime += dt;
        if (hitFlashTimer > 0.0) {
            hitFlashTimer = Math.max(0.0, hitFlashTimer - dt);
        }
        double decay = Math.exp(-9.0 * dt);
        knockVx *= decay;
        knockVy *= decay;

        Iterator<Map.Entry<StatusEffect.Type, StatusEffect>> it = statusEffects.entrySet().iterator();
        while (it.hasNext()) {
            StatusEffect eff = it.next().getValue();
            eff.remainingSeconds -= dt;
            if (eff.type == StatusEffect.Type.BURN || eff.type == StatusEffect.Type.POISON) {
                eff.tickAccumulator += dt;
                if (eff.tickAccumulator >= 0.5) {
                    eff.tickAccumulator -= 0.5;
                    onStatusDotTick(eff.type, eff.magnitude, world);
                }
            }
            if (eff.remainingSeconds <= 0.0) {
                it.remove();
            }
        }
    }

    protected abstract void onStatusDotTick(StatusEffect.Type type, double dmg, CombatWorld world);
}
