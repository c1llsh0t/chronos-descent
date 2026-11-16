package de.chronos.entity.ai;

import de.chronos.core.event.GameEvent;
import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;
import de.chronos.entity.EnemyType;
import de.chronos.entity.HazardZone;
import de.chronos.entity.Player;
import de.chronos.entity.Projectile;
import de.chronos.math.Vector2D;

import java.awt.Color;

/**
 * Zweiphasige Boss-KI für Malakor, den Chronos-Wächter:
 * - Phase 1: A*-Verfolgung, rotierende Projektil-Spiralen, gezielte Chronos-Slams
 * - Phase 2 (unter 50% HP): Schockwelle, Diener-Beschwörung, schnellere Doppelspiralen & Dreifach-Slams
 */
public final class BossBehavior implements EnemyBehavior {

    private final double speedScale;
    private double spiralAngle = 0.0;
    private double spiralTimer = 1.0;
    private double slamTimer = 3.2;

    public BossBehavior(double speedScale) {
        this.speedScale = speedScale;
    }

    @Override
    public void update(Enemy enemy, double dt, CombatWorld world) {
        Player player = world.getPlayer();

        // Phasenübergang bei 50% HP
        if (enemy.bossPhase == 1 && enemy.hp <= enemy.maxHp * 0.5) {
            enemy.bossPhase = 2;
            world.getCamera().addShake(0.6);
            world.getParticles().spawnShockwave(enemy.x, enemy.y, 200.0, 0.45, new Color(255, 65, 110));
            world.publishEvent(new GameEvent.BossPhaseChanged(2));

            // Zwei Skelett-Schützen zur Unterstützung rufen
            for (int i = 0; i < 2; i++) {
                double a = i * Math.PI;
                double sx = enemy.x + Math.cos(a) * 56.0;
                double sy = enemy.y + Math.sin(a) * 56.0;
                if (!world.getMap().collidesCircleWithWalls(sx, sy, 14.0)) {
                    Enemy minion = new Enemy(sx, sy, EnemyType.SKELETON_ARCHER, enemy.homeRoomId, 0.85, speedScale);
                    minion.alerted = true;
                    world.spawnEnemy(minion);
                }
            }
        }

        double phaseSpeedMult = (enemy.bossPhase == 2) ? 1.24 : 1.0;
        double speed = enemy.type.baseSpeed * speedScale * phaseSpeedMult * enemy.getSpeedMultiplierFromStatus();

        Vector2D target = ChaseBehavior.moveTargetAlongPath(enemy, player.pos(), world);
        Vector2D dir = target.sub(enemy.pos()).normalize();
        enemy.facingAngle = player.pos().sub(enemy.pos()).angle();

        Vector2D next = world.getMap().moveWithCollision(
                enemy.x, enemy.y,
                (dir.x * speed + enemy.knockVx * 0.35) * dt,
                (dir.y * speed + enemy.knockVy * 0.35) * dt,
                enemy.radius
        );
        enemy.x = next.x;
        enemy.y = next.y;

        // Spiral-Salven
        double rate = world.getDifficulty().enemyAttackRate;
        spiralTimer -= dt * rate;
        if (spiralTimer <= 0.0) {
            spiralTimer = (enemy.bossPhase == 2) ? 0.24 : 0.38;
            int arms = (enemy.bossPhase == 2) ? 6 : 4;
            spiralAngle += 0.38;
            for (int i = 0; i < arms; i++) {
                double a = spiralAngle + (2.0 * Math.PI * i / arms);
                Vector2D vel = Vector2D.fromAngle(a, 245.0);
                world.spawnProjectile(new Projectile(
                        enemy.x, enemy.y, vel.x, vel.y, 7.0, 15.0, false, 2.8, new Color(255, 72, 118)
                ));
            }
        }

        // Telegraphierte Chronos-Slams
        slamTimer -= dt * rate;
        if (slamTimer <= 0.0) {
            slamTimer = (enemy.bossPhase == 2) ? 2.6 : 3.8;
            int zones = (enemy.bossPhase == 2) ? 3 : 1;
            for (int i = 0; i < zones; i++) {
                double ox = (i == 0) ? 0.0 : world.getRandom().nextDouble(-95.0, 95.0);
                double oy = (i == 0) ? 0.0 : world.getRandom().nextDouble(-95.0, 95.0);
                world.spawnHazardZone(new HazardZone(
                        player.x + ox, player.y + oy,
                        66.0, 0.85, 24.0, true, new Color(255, 52, 88)
                ));
            }
        }
    }
}
