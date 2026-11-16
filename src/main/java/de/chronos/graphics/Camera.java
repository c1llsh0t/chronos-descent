package de.chronos.graphics;

import de.chronos.core.GameConfig;
import de.chronos.math.Vector2D;

import java.util.Random;

/**
 * Smoothe Kamera mit Cursor-Lookahead und gedämpftem Screen-Shake.
 */
public final class Camera {
    private double x;
    private double y;
    private double shakeTrauma;
    private double shakeOffsetX;
    private double shakeOffsetY;
    private final Random shakeRng = new Random(42L);

    public void snapTo(double targetX, double targetY) {
        this.x = targetX - GameConfig.LOGICAL_WIDTH / 2.0;
        this.y = targetY - GameConfig.LOGICAL_HEIGHT / 2.0;
    }

    public void addShake(double trauma) {
        this.shakeTrauma = Math.min(1.0, this.shakeTrauma + trauma);
    }

    public void update(double targetX, double targetY, double mouseLogicalX, double mouseLogicalY, double dt) {
        double lookX = (mouseLogicalX - GameConfig.LOGICAL_WIDTH / 2.0) * 0.14;
        double lookY = (mouseLogicalY - GameConfig.LOGICAL_HEIGHT / 2.0) * 0.14;

        double desiredX = targetX + lookX - GameConfig.LOGICAL_WIDTH / 2.0;
        double desiredY = targetY + lookY - GameConfig.LOGICAL_HEIGHT / 2.0;

        double smooth = 1.0 - Math.exp(-9.5 * dt);
        x += (desiredX - x) * smooth;
        y += (desiredY - y) * smooth;

        if (shakeTrauma > 0.001) {
            double power = shakeTrauma * shakeTrauma * 14.0;
            shakeOffsetX = (shakeRng.nextDouble() * 2.0 - 1.0) * power;
            shakeOffsetY = (shakeRng.nextDouble() * 2.0 - 1.0) * power;
            shakeTrauma = Math.max(0.0, shakeTrauma - 2.6 * dt);
        } else {
            shakeTrauma = 0.0;
            shakeOffsetX = 0.0;
            shakeOffsetY = 0.0;
        }
    }

    public double getX() {
        return x + shakeOffsetX;
    }

    public double getY() {
        return y + shakeOffsetY;
    }

    public Vector2D screenToWorld(double sx, double sy) {
        return new Vector2D(sx + getX(), sy + getY());
    }
}
