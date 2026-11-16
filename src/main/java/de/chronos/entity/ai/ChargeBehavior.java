package de.chronos.entity.ai;

import de.chronos.combat.FloatingText;
import de.chronos.combat.StatusEffect;
import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;
import de.chronos.entity.Player;
import de.chronos.math.Vector2D;

import java.awt.Color;

/**
 * Berserker-KI: verfolgt den Spieler, telegraphiert einen Sturmangriff und betäubt sich
 * bei Wandkollision selbst (klare Konter-Mechanik für den Spieler).
 */
public final class ChargeBehavior implements EnemyBehavior {

    private enum Phase { CHASE, WINDUP, CHARGING }

    private final double speedScale;
    private Phase phase = Phase.CHASE;
    private double phaseTimer = 0.0;
    private double chargeDirX = 0.0;
    private double chargeDirY = 0.0;

    public ChargeBehavior(double speedScale) {
        this.speedScale = speedScale;
    }

    @Override
    public void update(Enemy enemy, double dt, CombatWorld world) {
        Player player = world.getPlayer();
        double dist = enemy.pos().distanceTo(player.pos());
        boolean hasLos = world.getMap().hasLineOfSight(enemy.x, enemy.y, player.x, player.y);

        switch (phase) {
            case CHASE -> {
                enemy.telegraphing = false;
                double speed = enemy.type.baseSpeed * speedScale * enemy.getSpeedMultiplierFromStatus();
                Vector2D target = ChaseBehavior.moveTargetAlongPath(enemy, player.pos(), world);
                Vector2D dir = target.sub(enemy.pos()).normalize();
                enemy.facingAngle = dir.angle();

                Vector2D next = world.getMap().moveWithCollision(
                        enemy.x, enemy.y,
                        (dir.x * speed + enemy.knockVx) * dt,
                        (dir.y * speed + enemy.knockVy) * dt,
                        enemy.radius
                );
                enemy.x = next.x;
                enemy.y = next.y;

                if (hasLos && dist < 245.0 && enemy.attackCooldownTimer <= 0.0) {
                    phase = Phase.WINDUP;
                    phaseTimer = 0.55;
                    Vector2D cDir = player.pos().sub(enemy.pos()).normalize();
                    chargeDirX = cDir.x;
                    chargeDirY = cDir.y;
                    enemy.facingAngle = cDir.angle();
                }
            }
            case WINDUP -> {
                phaseTimer -= dt;
                enemy.telegraphing = true;
                enemy.telegraphX1 = enemy.x;
                enemy.telegraphY1 = enemy.y;
                enemy.telegraphX2 = enemy.x + chargeDirX * 260.0;
                enemy.telegraphY2 = enemy.y + chargeDirY * 260.0;
                enemy.telegraphColor = new Color(255, 60, 40);
                if (phaseTimer <= 0.0) {
                    enemy.telegraphing = false;
                    phase = Phase.CHARGING;
                    phaseTimer = 0.52;
                }
            }
            case CHARGING -> {
                phaseTimer -= dt;
                double chargeSpeed = 440.0 * speedScale;
                double dx = chargeDirX * chargeSpeed * dt;
                double dy = chargeDirY * chargeSpeed * dt;

                boolean hitWall = world.getMap().collidesCircleWithWalls(enemy.x + dx, enemy.y + dy, enemy.radius);
                if (hitWall) {
                    phase = Phase.CHASE;
                    enemy.attackCooldownTimer = 2.1;
                    enemy.applyStatus(StatusEffect.Type.STUN, 1.15, 1.0);
                    world.getCamera().addShake(0.22);
                    world.getParticles().spawnBurst(enemy.x, enemy.y, 12, 40, 160, 4.5, 0.3, new Color(255, 200, 90), true);
                    world.addFloatingText(new FloatingText(enemy.x, enemy.y - 22, "STUN!", new Color(255, 220, 80), 14f, 0.75));
                } else {
                    enemy.x += dx;
                    enemy.y += dy;
                    world.getParticles().spawnAfterimage(enemy.x, enemy.y, enemy.radius * 0.85, new Color(235, 88, 68));
                    if (phaseTimer <= 0.0) {
                        phase = Phase.CHASE;
                        enemy.attackCooldownTimer = 2.0;
                    }
                }
            }
        }
    }
}
