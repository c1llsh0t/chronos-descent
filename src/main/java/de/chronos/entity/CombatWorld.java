package de.chronos.entity;

import java.awt.Color;

import de.chronos.combat.FloatingText;
import de.chronos.core.event.GameEvent;
import de.chronos.graphics.Camera;
import de.chronos.graphics.ParticleSystem;
import de.chronos.math.SeededRandom;
import de.chronos.world.AStarPathfinder;
import de.chronos.world.DungeonMap;

import java.util.List;

/**
 * Abstrakte Schnittstelle der Spielwelt für Entitäten und KI-Verhalten (Dependency Inversion).
 */
public interface CombatWorld {
    DungeonMap getMap();
    Player getPlayer();
    List<Enemy> getEnemies();
    AStarPathfinder getPathfinder();
    ParticleSystem getParticles();
    Camera getCamera();
    SeededRandom getRandom();

    void spawnProjectile(Projectile projectile);
    void spawnEnemy(Enemy enemy);
    void spawnHazardZone(HazardZone zone);
    void addFloatingText(FloatingText text);

    void damageEnemy(Enemy enemy, double amount, boolean crit, double knockX, double knockY);

    /** Wie {@link #damageEnemy(Enemy, double, boolean, double, double)}, aber mit eigener Farbe für die Schadenszahl (z.B. Gift/Brand). */
    void damageEnemy(Enemy enemy, double amount, boolean crit, double knockX, double knockY, Color textColor);
    void damagePlayer(double amount, double knockX, double knockY);
    void publishEvent(GameEvent event);
}
