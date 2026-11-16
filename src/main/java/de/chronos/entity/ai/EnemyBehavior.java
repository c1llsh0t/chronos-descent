package de.chronos.entity.ai;

import de.chronos.entity.CombatWorld;
import de.chronos.entity.Enemy;

/**
 * Strategy-Interface für modulares Gegner-Verhalten.
 */
public interface EnemyBehavior {
    void update(Enemy enemy, double dt, CombatWorld world);
}
