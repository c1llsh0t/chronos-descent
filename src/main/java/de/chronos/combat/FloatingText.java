package de.chronos.combat;

import de.chronos.core.I18n;

import java.awt.Color;

/**
 * Schwebende Schadens-, Heilungs- und Statustexte in Weltkoordinaten.
 */
public final class FloatingText {
    public double x;
    public double y;
    public double vy;
    public String text;
    public Color color;
    public double life;
    public double maxLife;
    public float fontSize;

    public FloatingText(double x, double y, String text, Color color, float fontSize, double maxLife) {
        this.x = x;
        this.y = y;
        this.vy = -38.0;
        this.text = text;
        this.color = color;
        this.fontSize = fontSize;
        this.life = maxLife;
        this.maxLife = maxLife;
    }

    public boolean update(double dt) {
        y += vy * dt;
        vy *= Math.pow(0.25, dt);
        life -= dt;
        return life > 0.0;
    }

    public double alpha() {
        return Math.max(0.0, Math.min(1.0, life / maxLife));
    }
}
