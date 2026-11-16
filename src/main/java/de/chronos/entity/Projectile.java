package de.chronos.entity;

import de.chronos.combat.StatusEffect;
import de.chronos.math.Vector2D;
import de.chronos.world.DungeonMap;

import java.awt.Color;
import java.util.HashSet;
import java.util.Set;

/**
 * Projektil für Spieler- und Gegnerangriffe mit Unterstützung für
 * Durchschlag (Pierce), Abpraller (Ricochet), Homing und Statuseffekte.
 */
public final class Projectile {
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double radius;
    public double damage;
    public boolean fromPlayer;
    public boolean crit;
    public int pierceLeft;
    public int bouncesLeft;
    public boolean homing;
    public boolean explosive;
    public double life;
    public Color color;
    public StatusEffect.Type applyStatus;
    public double statusDuration;
    public double statusMagnitude;
    public boolean active = true;
    public final Set<Enemy> hitEnemies = new HashSet<>();

    public Projectile(
            double x, double y,
            double vx, double vy,
            double radius,
            double damage,
            boolean fromPlayer,
            double life,
            Color color
    ) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.damage = damage;
        this.fromPlayer = fromPlayer;
        this.life = life;
        this.color = color;
    }

    public void update(double dt, CombatWorld world) {
        if (!active) {
            return;
        }
        life -= dt;
        if (life <= 0.0) {
            active = false;
            return;
        }

        // Leichtes Homing auf nächsten sichtbaren Gegner für Arkanmagier-Projektile
        if (homing && fromPlayer) {
            Enemy nearest = null;
            double bestDistSq = 280.0 * 280.0;
            for (Enemy e : world.getEnemies()) {
                if (!e.active) continue;
                double d2 = (e.x - x) * (e.x - x) + (e.y - y) * (e.y - y);
                if (d2 < bestDistSq) {
                    bestDistSq = d2;
                    nearest = e;
                }
            }
            if (nearest != null) {
                double speed = Math.sqrt(vx * vx + vy * vy);
                Vector2D desired = new Vector2D(nearest.x - x, nearest.y - y).normalize().scale(speed);
                double turn = Math.min(1.0, dt * 4.5);
                vx += (desired.x - vx) * turn;
                vy += (desired.y - vy) * turn;
                double newSpeed = Math.sqrt(vx * vx + vy * vy);
                if (newSpeed > 1e-4) {
                    vx = (vx / newSpeed) * speed;
                    vy = (vy / newSpeed) * speed;
                }
            }
        }

        DungeonMap map = world.getMap();
        double nx = x + vx * dt;
        double ny = y + vy * dt;

        boolean hitX = map.collidesCircleWithWalls(nx, y, radius);
        boolean hitY = map.collidesCircleWithWalls(x, ny, radius);

        if (hitX || hitY) {
            if (bouncesLeft > 0) {
                bouncesLeft--;
                if (hitX) vx = -vx;
                if (hitY) vy = -vy;
                x += vx * dt * 0.5;
                y += vy * dt * 0.5;
            } else {
                active = false;
                world.getParticles().spawnBurst(x, y, 5, 30, 90, 3.5, 0.22, color, true);
            }
        } else {
            x = nx;
            y = ny;
        }
    }
}
