package de.chronos.math;

/**
 * Unveränderlicher 2D-Vektor mit Standard-Vektorarithmetik für Physik,
 * Kollisionserkennung und KI-Steuerung.
 */
public final class Vector2D {
    public static final Vector2D ZERO = new Vector2D(0.0, 0.0);

    public final double x;
    public final double y;

    public Vector2D(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static Vector2D fromAngle(double radians, double length) {
        return new Vector2D(Math.cos(radians) * length, Math.sin(radians) * length);
    }

    public Vector2D add(Vector2D other) {
        return new Vector2D(this.x + other.x, this.y + other.y);
    }

    public Vector2D add(double dx, double dy) {
        return new Vector2D(this.x + dx, this.y + dy);
    }

    public Vector2D sub(Vector2D other) {
        return new Vector2D(this.x - other.x, this.y - other.y);
    }

    public Vector2D scale(double factor) {
        return new Vector2D(this.x * factor, this.y * factor);
    }

    public double lengthSquared() {
        return x * x + y * y;
    }

    public double length() {
        return Math.sqrt(x * x + y * y);
    }

    public Vector2D normalize() {
        double len = length();
        if (len < 1e-9) {
            return ZERO;
        }
        return new Vector2D(x / len, y / len);
    }

    public double distanceTo(Vector2D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distanceSquaredTo(Vector2D other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        return dx * dx + dy * dy;
    }

    public double dot(Vector2D other) {
        return this.x * other.x + this.y * other.y;
    }

    public double angle() {
        return Math.atan2(y, x);
    }

    public Vector2D rotate(double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vector2D(x * cos - y * sin, x * sin + y * cos);
    }

    public Vector2D lerp(Vector2D target, double t) {
        double clamped = Math.max(0.0, Math.min(1.0, t));
        return new Vector2D(
                x + (target.x - x) * clamped,
                y + (target.y - y) * clamped
        );
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", x, y);
    }
}
