package de.chronos.graphics;

import de.chronos.math.Vector2D;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Zentrales Effekt-System für Partikel, Schwertbögen, Schockwellen, Kettenblitze und Dash-Geister.
 */
public final class ParticleSystem {

    private static final int MAX_PARTICLES = 700;

    private static final class Shockwave {
        final double x, y, maxRadius;
        final Color color;
        double life, maxLife;

        Shockwave(double x, double y, double maxRadius, double maxLife, Color color) {
            this.x = x;
            this.y = y;
            this.maxRadius = maxRadius;
            this.life = maxLife;
            this.maxLife = maxLife;
            this.color = color;
        }
    }

    private static final class SlashEffect {
        final double x, y, angleRad, arcDeg, radius;
        final Color color;
        double life, maxLife;

        SlashEffect(double x, double y, double angleRad, double arcDeg, double radius, double maxLife, Color color) {
            this.x = x;
            this.y = y;
            this.angleRad = angleRad;
            this.arcDeg = arcDeg;
            this.radius = radius;
            this.life = maxLife;
            this.maxLife = maxLife;
            this.color = color;
        }
    }

    private static final class LightningBolt {
        final List<Vector2D> points;
        final Color color;
        double life, maxLife;

        LightningBolt(List<Vector2D> points, double maxLife, Color color) {
            this.points = points;
            this.life = maxLife;
            this.maxLife = maxLife;
            this.color = color;
        }
    }

    private static final class Afterimage {
        final double x, y, radius;
        final Color color;
        double life, maxLife;

        Afterimage(double x, double y, double radius, double maxLife, Color color) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.life = maxLife;
            this.maxLife = maxLife;
            this.color = color;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final List<Shockwave> shockwaves = new ArrayList<>();
    private final List<SlashEffect> slashes = new ArrayList<>();
    private final List<LightningBolt> bolts = new ArrayList<>();
    private final List<Afterimage> afterimages = new ArrayList<>();
    private final Random rng = new Random(1337L);

    public void clear() {
        particles.clear();
        shockwaves.clear();
        slashes.clear();
        bolts.clear();
        afterimages.clear();
    }

    public void spawnBurst(double x, double y, int count, double minSpeed, double maxSpeed, double size, double life, Color color, boolean glow) {
        for (int i = 0; i < count; i++) {
            if (particles.size() >= MAX_PARTICLES) {
                particles.remove(0);
            }
            double angle = rng.nextDouble() * Math.PI * 2.0;
            double speed = minSpeed + rng.nextDouble() * (maxSpeed - minSpeed);
            particles.add(new Particle(
                    x, y,
                    Math.cos(angle) * speed,
                    Math.sin(angle) * speed,
                    size * (0.7 + rng.nextDouble() * 0.6),
                    life * (0.75 + rng.nextDouble() * 0.5),
                    3.5,
                    color,
                    glow
            ));
        }
    }

    public void spawnDirectionalSpark(double x, double y, double dirAngle, double spreadRad, int count, double speed, Color color) {
        for (int i = 0; i < count; i++) {
            if (particles.size() >= MAX_PARTICLES) {
                particles.remove(0);
            }
            double a = dirAngle + (rng.nextDouble() * 2.0 - 1.0) * spreadRad;
            double s = speed * (0.6 + rng.nextDouble() * 0.7);
            particles.add(new Particle(x, y, Math.cos(a) * s, Math.sin(a) * s, 3.8, 0.28, 4.2, color, true));
        }
    }

    public void spawnShockwave(double x, double y, double maxRadius, double duration, Color color) {
        shockwaves.add(new Shockwave(x, y, maxRadius, duration, color));
    }

    public void spawnSlash(double x, double y, double angleRad, double arcDeg, double radius, Color color) {
        slashes.add(new SlashEffect(x, y, angleRad, arcDeg, radius, 0.16, color));
    }

    public void spawnLightning(double x1, double y1, double x2, double y2, Color color) {
        List<Vector2D> pts = new ArrayList<>();
        int segments = 6;
        pts.add(new Vector2D(x1, y1));
        for (int i = 1; i < segments; i++) {
            double t = (double) i / segments;
            double px = x1 + (x2 - x1) * t + (rng.nextDouble() - 0.5) * 18.0;
            double py = y1 + (y2 - y1) * t + (rng.nextDouble() - 0.5) * 18.0;
            pts.add(new Vector2D(px, py));
        }
        pts.add(new Vector2D(x2, y2));
        bolts.add(new LightningBolt(pts, 0.16, color));
    }

    public void spawnAfterimage(double x, double y, double radius, Color color) {
        afterimages.add(new Afterimage(x, y, radius, 0.24, color));
    }

    public void update(double dt) {
        particles.removeIf(p -> !p.update(dt));

        Iterator<Shockwave> swIt = shockwaves.iterator();
        while (swIt.hasNext()) {
            Shockwave sw = swIt.next();
            sw.life -= dt;
            if (sw.life <= 0.0) swIt.remove();
        }

        Iterator<SlashEffect> slIt = slashes.iterator();
        while (slIt.hasNext()) {
            SlashEffect sl = slIt.next();
            sl.life -= dt;
            if (sl.life <= 0.0) slIt.remove();
        }

        Iterator<LightningBolt> lbIt = bolts.iterator();
        while (lbIt.hasNext()) {
            LightningBolt lb = lbIt.next();
            lb.life -= dt;
            if (lb.life <= 0.0) lbIt.remove();
        }

        Iterator<Afterimage> aiIt = afterimages.iterator();
        while (aiIt.hasNext()) {
            Afterimage ai = aiIt.next();
            ai.life -= dt;
            if (ai.life <= 0.0) aiIt.remove();
        }
    }

    public void render(Graphics2D g, double camX, double camY) {
        // Afterimages
        for (Afterimage ai : afterimages) {
            double alpha = Math.max(0.0, ai.life / ai.maxLife) * 0.45;
            g.setColor(withAlpha(ai.color, alpha));
            double r = ai.radius;
            g.fill(new Ellipse2D.Double(ai.x - camX - r, ai.y - camY - r, r * 2, r * 2));
        }

        // Schockwellen
        for (Shockwave sw : shockwaves) {
            double prog = 1.0 - (sw.life / sw.maxLife);
            double r = sw.maxRadius * (0.2 + 0.8 * prog);
            double alpha = (1.0 - prog) * 0.85;
            g.setColor(withAlpha(sw.color, alpha));
            g.setStroke(new BasicStroke((float) (4.0 * (1.0 - prog) + 1.0)));
            g.draw(new Ellipse2D.Double(sw.x - camX - r, sw.y - camY - r, r * 2, r * 2));
        }

        // Schwertbögen
        for (SlashEffect sl : slashes) {
            double alpha = Math.max(0.0, sl.life / sl.maxLife);
            double r = sl.radius;
            double centerDeg = -Math.toDegrees(sl.angleRad);
            double startDeg = centerDeg - sl.arcDeg / 2.0;

            g.setColor(withAlpha(sl.color, alpha * 0.55));
            g.setStroke(new BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(sl.x - camX - r, sl.y - camY - r, r * 2, r * 2, startDeg, sl.arcDeg, Arc2D.OPEN));

            g.setColor(withAlpha(Color.WHITE, alpha * 0.9));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Arc2D.Double(sl.x - camX - r, sl.y - camY - r, r * 2, r * 2, startDeg, sl.arcDeg, Arc2D.OPEN));
        }

        // Kettenblitze
        for (LightningBolt lb : bolts) {
            double alpha = Math.max(0.0, lb.life / lb.maxLife);
            g.setColor(withAlpha(lb.color, alpha));
            g.setStroke(new BasicStroke(3.2f));
            for (int i = 0; i < lb.points.size() - 1; i++) {
                Vector2D a = lb.points.get(i);
                Vector2D b = lb.points.get(i + 1);
                g.draw(new Line2D.Double(a.x - camX, a.y - camY, b.x - camX, b.y - camY));
            }
            g.setColor(withAlpha(Color.WHITE, alpha));
            g.setStroke(new BasicStroke(1.3f));
            for (int i = 0; i < lb.points.size() - 1; i++) {
                Vector2D a = lb.points.get(i);
                Vector2D b = lb.points.get(i + 1);
                g.draw(new Line2D.Double(a.x - camX, a.y - camY, b.x - camX, b.y - camY));
            }
        }

        // Partikel
        for (Particle p : particles) {
            double prog = p.progress();
            double alpha = 1.0 - prog;
            double s = p.size * (1.0 - prog * 0.5);
            double sx = p.x - camX;
            double sy = p.y - camY;
            if (p.glow) {
                g.setColor(withAlpha(p.color, alpha * 0.3));
                g.fill(new Ellipse2D.Double(sx - s * 1.4, sy - s * 1.4, s * 2.8, s * 2.8));
            }
            g.setColor(withAlpha(p.color, alpha));
            g.fill(new Ellipse2D.Double(sx - s * 0.5, sy - s * 0.5, s, s));
        }
    }

    public static Color withAlpha(Color c, double alpha) {
        int a = Math.max(0, Math.min(255, (int) Math.round(alpha * 255.0)));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }
}
