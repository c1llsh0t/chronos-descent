package de.chronos.entity.ai;

import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;
import de.chronos.entity.Player;
import de.chronos.math.Vector2D;

import java.util.ArrayList;

/**
 * Verfolgungs-KI mit A*-Pfadfindung um Hindernisse herum und direkter Sichtlinien-Steuerung.
 */
public final class ChaseBehavior implements EnemyBehavior {

    private final double speedScale;

    public ChaseBehavior(double speedScale) {
        this.speedScale = speedScale;
    }

    @Override
    public void update(Enemy enemy, double dt, CombatWorld world) {
        Player player = world.getPlayer();
        double speed = enemy.type.baseSpeed * speedScale * enemy.getSpeedMultiplierFromStatus();

        Vector2D target = moveTargetAlongPath(enemy, player.pos(), world);
        Vector2D dir = target.sub(enemy.pos()).normalize();
        enemy.facingAngle = dir.angle();

        double stepX = (dir.x * speed + enemy.knockVx) * dt;
        double stepY = (dir.y * speed + enemy.knockVy) * dt;
        Vector2D next = world.getMap().moveWithCollision(enemy.x, enemy.y, stepX, stepY, enemy.radius);
        enemy.x = next.x;
        enemy.y = next.y;
    }

    public static Vector2D moveTargetAlongPath(Enemy enemy, Vector2D goal, CombatWorld world) {
        if (world.getMap().hasLineOfSight(enemy.x, enemy.y, goal.x, goal.y)) {
            return goal;
        }
        // Nur im Takt neu berechnen – auch wenn kein Pfad gefunden wurde (spart teure A*-Suchen)
        if (enemy.pathRecalcTimer <= 0.0) {
            enemy.pathRecalcTimer = 0.40;
            enemy.currentPath = new ArrayList<>(world.getPathfinder().findPath(world.getMap(), enemy.pos(), goal));
        }
        while (!enemy.currentPath.isEmpty()) {
            Vector2D wp = enemy.currentPath.get(0);
            if (enemy.pos().distanceTo(wp) < 16.0 && enemy.currentPath.size() > 1) {
                enemy.currentPath.remove(0);
            } else {
                return wp;
            }
        }
        return goal;
    }
}
