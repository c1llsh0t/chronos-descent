package de.chronos.entity.ai;

import de.chronos.combat.FloatingText;
import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;
import de.chronos.entity.EnemyType;
import de.chronos.entity.Player;
import de.chronos.entity.Projectile;
import de.chronos.math.Vector2D;

import java.awt.Color;

/**
 * Nekromant-KI: hält Abstand, beschwört Diener und feuert Schattenprojektile.
 */
public final class NecromancerBehavior implements EnemyBehavior {

    private final double speedScale;
    private double summonCooldown = 3.2;

    public NecromancerBehavior(double speedScale) {
        this.speedScale = speedScale;
    }

    @Override
    public void update(Enemy enemy, double dt, CombatWorld world) {
        Player player = world.getPlayer();
        double dist = enemy.pos().distanceTo(player.pos());
        double speed = enemy.type.baseSpeed * speedScale * enemy.getSpeedMultiplierFromStatus();
        summonCooldown = Math.max(0.0, summonCooldown - dt);

        Vector2D dir = Vector2D.ZERO;
        if (dist < 200.0) {
            dir = enemy.pos().sub(player.pos()).normalize();
        } else if (dist > 360.0 || !world.getMap().hasLineOfSight(enemy.x, enemy.y, player.x, player.y)) {
            Vector2D target = ChaseBehavior.moveTargetAlongPath(enemy, player.pos(), world);
            dir = target.sub(enemy.pos()).normalize();
        }
        enemy.facingAngle = player.pos().sub(enemy.pos()).angle();

        Vector2D next = world.getMap().moveWithCollision(
                enemy.x, enemy.y,
                (dir.x * speed + enemy.knockVx) * dt,
                (dir.y * speed + enemy.knockVy) * dt,
                enemy.radius
        );
        enemy.x = next.x;
        enemy.y = next.y;

        if (summonCooldown <= 0.0 && world.getEnemies().size() < 26) {
            summonCooldown = 5.5;
            double angle = world.getRandom().nextDouble(0, Math.PI * 2);
            double sx = enemy.x + Math.cos(angle) * 44.0;
            double sy = enemy.y + Math.sin(angle) * 44.0;
            if (!world.getMap().collidesCircleWithWalls(sx, sy, 14.0)) {
                Enemy minion = new Enemy(sx, sy, EnemyType.SLIME, enemy.homeRoomId, 0.75, speedScale);
                minion.alerted = true;
                world.spawnEnemy(minion);
                world.getParticles().spawnBurst(sx, sy, 12, 30, 120, 4.0, 0.35, new Color(185, 85, 255), true);
                world.addFloatingText(new FloatingText(enemy.x, enemy.y - 24, "SUMMON!", new Color(205, 130, 255), 13f, 0.7));
            }
        }

        if (enemy.attackCooldownTimer <= 0.0 && world.getMap().hasLineOfSight(enemy.x, enemy.y, player.x, player.y)) {
            enemy.attackCooldownTimer = 2.2;
            double baseAngle = enemy.facingAngle;
            for (int i = -1; i <= 1; i++) {
                double a = baseAngle + i * 0.22;
                Vector2D vel = Vector2D.fromAngle(a, 270.0);
                world.spawnProjectile(new Projectile(
                        enemy.x, enemy.y, vel.x, vel.y, 6.0, 12.0, false, 2.3, new Color(196, 92, 255)
                ));
            }
        }
    }
}
