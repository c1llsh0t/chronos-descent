package de.chronos.entity;

import de.chronos.combat.StatusEffect;
import de.chronos.entity.ai.BossBehavior;
import de.chronos.entity.ai.ChargeBehavior;
import de.chronos.entity.ai.ChaseBehavior;
import de.chronos.entity.ai.EnemyBehavior;
import de.chronos.entity.ai.NecromancerBehavior;
import de.chronos.entity.ai.RangedKiteBehavior;
import de.chronos.math.Vector2D;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

/**
 * Gegner-Entität mit delegiertem KI-Verhalten (Strategy Pattern),
 * Telegraphing-Markern für Angriffswarnungen und Home-Room-Zuordnung.
 */
public final class Enemy extends Entity {

    public final EnemyType type;
    public final int homeRoomId;
    public final EnemyBehavior behavior;

    public boolean alerted = false;
    public double attackCooldownTimer = 0.6;
    public double contactCooldown = 0.0;
    public int bossPhase = 1;

    // KI-Pfad-Cache für A*
    public List<Vector2D> currentPath = Collections.emptyList();
    public double pathRecalcTimer = 0.0;

    // Telegraph-Zustand für visuelles Feedback (z.B. Schütze / Berserker)
    public boolean telegraphing = false;
    public double telegraphX1, telegraphY1, telegraphX2, telegraphY2;
    public Color telegraphColor = new Color(255, 70, 70);

    public Enemy(double x, double y, EnemyType type, int homeRoomId, double hpScale, double speedScale) {
        super(x, y, type.radius, type.baseHp * hpScale);
        this.type = type;
        this.homeRoomId = homeRoomId;
        this.behavior = createBehavior(type, speedScale);
    }

    private static EnemyBehavior createBehavior(EnemyType type, double speedScale) {
        return switch (type) {
            case SLIME, ELDER_SLIME -> new ChaseBehavior(speedScale);
            case SKELETON_ARCHER -> new RangedKiteBehavior(speedScale);
            case BERSERKER -> new ChargeBehavior(speedScale);
            case NECROMANCER -> new NecromancerBehavior(speedScale);
            case BOSS_MALAKOR -> new BossBehavior(speedScale);
        };
    }

    public void update(double dt, CombatWorld world) {
        if (!active) {
            return;
        }
        updateBaseTimers(dt, world);
        if (attackCooldownTimer > 0.0) {
            attackCooldownTimer = Math.max(0.0, attackCooldownTimer - dt * world.getDifficulty().enemyAttackRate);
        }
        if (contactCooldown > 0.0) {
            contactCooldown = Math.max(0.0, contactCooldown - dt);
        }
        if (pathRecalcTimer > 0.0) {
            pathRecalcTimer = Math.max(0.0, pathRecalcTimer - dt);
        }

        Player player = world.getPlayer();
        if (!alerted) {
            double dist = pos().distanceTo(player.pos());
            boolean sameRoom = world.getMap().getRoomAt(player.x, player.y) != null
                    && world.getMap().getRoomAt(player.x, player.y).id == homeRoomId;
            if (sameRoom || (dist < 380.0 && world.getMap().hasLineOfSight(x, y, player.x, player.y))) {
                alerted = true;
            } else {
                return;
            }
        }

        if (hasStatus(StatusEffect.Type.STUN)) {
            telegraphing = false;
            Vector2D next = world.getMap().moveWithCollision(x, y, knockVx * dt, knockVy * dt, radius);
            x = next.x;
            y = next.y;
            return;
        }

        behavior.update(this, dt, world);
    }

    @Override
    protected void onStatusDotTick(StatusEffect.Type type, double dmg, CombatWorld world) {
        Color col = (type == StatusEffect.Type.BURN) ? new Color(255, 140, 50) : new Color(110, 235, 95);
        world.damageEnemy(this, dmg, false, 0, 0, col);
    }
}
