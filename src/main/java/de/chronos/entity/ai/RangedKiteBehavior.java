package de.chronos.entity.ai;

import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;
import de.chronos.entity.Player;
import de.chronos.entity.Projectile;
import de.chronos.math.Vector2D;

import java.awt.Color;

/**
 * Fernkampf-KI: hält optimalen Abstand (Kiting) und telegraphiert Schüsse mit Ziel-Laser.
 */
public final class RangedKiteBehavior implements EnemyBehavior {

    private final double speedScale;
    private double aimTimer = 0.0;
    private double lockedAngle = 0.0;

    public RangedKiteBehavior(double speedScale) {
        this.speedScale = speedScale;
    }

    @Override
    public void update(Enemy enemy, double dt, CombatWorld world) {
        Player player = world.getPlayer();
        double dist = enemy.pos().distanceTo(player.pos());
        boolean hasLos = world.getMap().hasLineOfSight(enemy.x, enemy.y, player.x, player.y);
        double speed = enemy.type.baseSpeed * speedScale * enemy.getSpeedMultiplierFromStatus();

        if (aimTimer > 0.0) {
            aimTimer -= dt;
            enemy.telegraphing = true;
            enemy.telegraphX1 = enemy.x;
            enemy.telegraphY1 = enemy.y;
            enemy.telegraphX2 = enemy.x + Math.cos(lockedAngle) * 320.0;
            enemy.telegraphY2 = enemy.y + Math.sin(lockedAngle) * 320.0;
            enemy.telegraphColor = new Color(255, 78, 78);

            if (aimTimer <= 0.0) {
                enemy.telegraphing = false;
                Vector2D vel = Vector2D.fromAngle(lockedAngle, 345.0);
                Projectile p = new Projectile(
                        enemy.x + Math.cos(lockedAngle) * 14.0,
                        enemy.y + Math.sin(lockedAngle) * 14.0,
                        vel.x, vel.y, 5.5, 14.0, false, 2.0, new Color(255, 95, 75)
                );
                world.spawnProjectile(p);
                enemy.attackCooldownTimer = 1.65;
            } else {
                Vector2D next = world.getMap().moveWithCollision(enemy.x, enemy.y, enemy.knockVx * dt, enemy.knockVy * dt, enemy.radius);
                enemy.x = next.x;
                enemy.y = next.y;
                return;
            }
        }

        enemy.telegraphing = false;
        Vector2D moveDir;
        if (!hasLos || dist > 330.0) {
            Vector2D target = ChaseBehavior.moveTargetAlongPath(enemy, player.pos(), world);
            moveDir = target.sub(enemy.pos()).normalize();
        } else if (dist < 170.0) {
            moveDir = enemy.pos().sub(player.pos()).normalize();
        } else {
            // Seitliches Ausweichen (Strafing)
            Vector2D toP = player.pos().sub(enemy.pos()).normalize();
            moveDir = new Vector2D(-toP.y, toP.x);
            speed *= 0.55;
        }

        enemy.facingAngle = player.pos().sub(enemy.pos()).angle();
        Vector2D next = world.getMap().moveWithCollision(
                enemy.x, enemy.y,
                (moveDir.x * speed + enemy.knockVx) * dt,
                (moveDir.y * speed + enemy.knockVy) * dt,
                enemy.radius
        );
        enemy.x = next.x;
        enemy.y = next.y;

        if (hasLos && dist < 390.0 && enemy.attackCooldownTimer <= 0.0) {
            aimTimer = 0.45;
            lockedAngle = player.pos().sub(enemy.pos()).angle();
        }
    }
}
