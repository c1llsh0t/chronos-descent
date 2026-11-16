package de.chronos.graphics;

import java.awt.Color;

/**
 * Einzelner Partikel mit Position, Geschwindigkeit, Reibung, Größe und Farbverlauf.
 */
public final class Particle {
    public double x;
    public double y;
    public double vx;
    public double vy;
    public double size;
    public double life;
    public double maxLife;
    public double drag;
    public Color color;
    public boolean glow;

    public Particle(double x, double y, double vx, double vy, double size, double life, double drag, Color color, boolean glow) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.size = size;
        this.life = life;
        this.maxLife = life;
        this.drag = drag;
        this.color = color;
        this.glow = glow;
    }

    public boolean update(double dt) {
        x += vx * dt;
        y += vy * dt;
        double damping = Math.exp(-drag * dt);
        vx *= damping;
        vy *= damping;
        life -= dt;
        return life > 0.0;
    }

    public double progress() {
        return 1.0 - Math.max(0.0, Math.min(1.0, life / maxLife));
    }
}
