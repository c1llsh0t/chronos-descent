package de.chronos.graphics;

import de.chronos.combat.FloatingText;
import de.chronos.combat.StatusEffect;
import de.chronos.core.GameConfig;
import de.chronos.entity.Enemy;
import de.chronos.entity.EnemyType;
import de.chronos.entity.HazardZone;
import de.chronos.entity.HeroClass;
import de.chronos.entity.InteractiveProp;
import de.chronos.entity.Pickup;
import de.chronos.entity.Player;
import de.chronos.entity.Projectile;
import de.chronos.math.Vector2D;
import de.chronos.ui.state.GameSession;
import de.chronos.world.DungeonMap;
import de.chronos.world.Room;
import de.chronos.world.TileType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

/**
 * Prozedurale Java2D-Render-Pipeline für Dungeon-Tiles, dynamische Lichter,
 * Fog-of-War, Gefahrenzonen, Entitäten, Projektile und Effekte.
 */
public final class WorldRenderer {

    private static final Color BG_ABYSS = new Color(10, 11, 18);

    // Ebenenabhängige Farbpaletten (Ebene 1: Krypta, Ebene 2: Tiefenruine, Ebene 3: Chronos-Kern)
    private Color floorBase(int floor) {
        return switch (floor) {
            case 2 -> new Color(33, 38, 52);
            case 3 -> new Color(42, 26, 44);
            default -> new Color(34, 34, 44);
        };
    }

    private Color wallTop(int floor) {
        return switch (floor) {
            case 2 -> new Color(56, 66, 90);
            case 3 -> new Color(76, 42, 72);
            default -> new Color(62, 62, 78);
        };
    }

    private Color wallFront(int floor) {
        return switch (floor) {
            case 2 -> new Color(38, 46, 64);
            case 3 -> new Color(48, 26, 48);
            default -> new Color(42, 42, 56);
        };
    }

    public void render(Graphics2D g, GameSession session, double mouseLogicalX, double mouseLogicalY, double globalTime) {
        g.setColor(BG_ABYSS);
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        DungeonMap map = session.getMap();
        Camera cam = session.getCamera();
        double camX = cam.getX();
        double camY = cam.getY();
        int ts = GameConfig.TILE_SIZE;
        int floor = session.getCurrentFloor();

        int minTx = Math.max(0, (int) Math.floor(camX / ts) - 1);
        int maxTx = Math.min(map.getCols() - 1, (int) Math.ceil((camX + GameConfig.LOGICAL_WIDTH) / ts) + 1);
        int minTy = Math.max(0, (int) Math.floor(camY / ts) - 1);
        int maxTy = Math.min(map.getRows() - 1, (int) Math.ceil((camY + GameConfig.LOGICAL_HEIGHT) / ts) + 1);

        Color fBase = floorBase(floor);
        Color wTop = wallTop(floor);
        Color wFront = wallFront(floor);

        // 1. Boden- und Wand-Tiles im sichtbaren Kamerabereich zeichnen
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                DungeonMap.Visibility vis = map.getVisibility(tx, ty);
                if (vis == DungeonMap.Visibility.UNSEEN) {
                    continue;
                }
                TileType tile = map.getTile(tx, ty);
                if (tile == TileType.VOID) {
                    continue;
                }
                int sx = (int) Math.round(tx * ts - camX);
                int sy = (int) Math.round(ty * ts - camY);

                if (tile == TileType.WALL) {
                    g.setColor(wTop);
                    g.fillRect(sx, sy, ts, ts - 10);
                    g.setColor(wFront);
                    g.fillRect(sx, sy + ts - 10, ts, 10);
                    g.setColor(new Color(255, 255, 255, 18));
                    g.drawRect(sx, sy, ts - 1, ts - 1);
                } else if (tile == TileType.PILLAR) {
                    drawFloorTile(g, sx, sy, ts, tx, ty, fBase);
                    g.setColor(new Color(18, 18, 26, 130));
                    g.fillOval(sx + 6, sy + ts - 12, ts - 12, 10);
                    g.setColor(wFront);
                    g.fillRoundRect(sx + 8, sy + 6, ts - 16, ts - 12, 8, 8);
                    g.setColor(wTop);
                    g.fillRoundRect(sx + 8, sy + 2, ts - 16, ts - 18, 8, 8);
                } else {
                    drawFloorTile(g, sx, sy, ts, tx, ty, fBase);
                    if (tile == TileType.SPIKE_TRAP) {
                        drawSpikeTrap(g, sx, sy, ts, session.trapTimer);
                    }
                }
            }
        }

        // Raum-Bodenakzente (Boss-Siegel, Schatz-Teppich, Schrein-Rune)
        for (Room r : map.getRooms()) {
            if (!r.discovered) continue;
            Vector2D rc = r.centerWorld();
            double rx = rc.x - camX;
            double ry = rc.y - camY;
            if (rx < -200 || rx > GameConfig.LOGICAL_WIDTH + 200 || ry < -200 || ry > GameConfig.LOGICAL_HEIGHT + 200) {
                continue;
            }
            if (r.type == Room.RoomType.BOSS) {
                g.setColor(new Color(220, 48, 82, 42));
                g.setStroke(new BasicStroke(2.5f));
                g.drawOval((int) (rx - 95), (int) (ry - 95), 190, 190);
                g.drawOval((int) (rx - 65), (int) (ry - 65), 130, 130);
            } else if (r.type == Room.RoomType.TREASURE) {
                g.setColor(new Color(255, 196, 62, 32));
                g.fillRoundRect((int) (rx - 65), (int) (ry - 45), 130, 90, 14, 14);
            } else if (r.type == Room.RoomType.SHRINE) {
                g.setColor(new Color(80, 235, 165, 30));
                g.drawOval((int) (rx - 72), (int) (ry - 72), 144, 144);
            }
        }

        // 2. Fackel-Lichtscheine
        for (Vector2D torch : map.getTorches()) {
            int tx = map.worldToTile(torch.x);
            int ty = map.worldToTile(torch.y);
            if (map.getVisibility(tx, ty) != DungeonMap.Visibility.VISIBLE) continue;
            double sx = torch.x - camX;
            double sy = torch.y - camY;
            double flicker = 1.0 + 0.08 * Math.sin(globalTime * 11.0 + torch.x);
            float rad = (float) (58.0 * flicker);
            RadialGradientPaint glow = new RadialGradientPaint(
                    (float) sx, (float) sy, rad,
                    new float[]{0f, 0.5f, 1f},
                    new Color[]{new Color(255, 160, 55, 90), new Color(255, 110, 30, 35), new Color(255, 80, 0, 0)}
            );
            g.setPaint(glow);
            g.fill(new Ellipse2D.Double(sx - rad, sy - rad, rad * 2, rad * 2));
            g.setColor(new Color(255, 220, 110));
            g.fill(new Ellipse2D.Double(sx - 3, sy - 3, 6, 6));
        }

        // 3. Gefahrenzonen (Telegraph)
        for (HazardZone hz : session.getHazardZones()) {
            double sx = hz.x - camX;
            double sy = hz.y - camY;
            double p = hz.progress();
            g.setColor(ParticleSystem.withAlpha(hz.color, 0.18 + 0.14 * p));
            g.fill(new Ellipse2D.Double(sx - hz.radius, sy - hz.radius, hz.radius * 2, hz.radius * 2));
            g.setColor(ParticleSystem.withAlpha(hz.color, 0.85));
            g.setStroke(new BasicStroke(2.2f));
            g.draw(new Ellipse2D.Double(sx - hz.radius, sy - hz.radius, hz.radius * 2, hz.radius * 2));
            double innerR = hz.radius * p;
            g.setColor(ParticleSystem.withAlpha(hz.color, 0.42));
            g.fill(new Ellipse2D.Double(sx - innerR, sy - innerR, innerR * 2, innerR * 2));
        }

        // 4. Interaktive Objekte
        for (InteractiveProp prop : session.getProps()) {
            int tx = map.worldToTile(prop.x);
            int ty = map.worldToTile(prop.y);
            if (map.getVisibility(tx, ty) == DungeonMap.Visibility.UNSEEN) continue;
            drawInteractiveProp(g, prop, camX, camY, session.portalUnlocked);
        }

        // 5. Pickups
        for (Pickup pk : session.getPickups()) {
            int tx = map.worldToTile(pk.x);
            int ty = map.worldToTile(pk.y);
            if (map.getVisibility(tx, ty) != DungeonMap.Visibility.VISIBLE) continue;
            drawPickup(g, pk, camX, camY);
        }

        // 6. Gegner-Telegraph-Linien (hinter Entitäten)
        for (Enemy e : session.getEnemies()) {
            if (!e.active || !e.telegraphing) continue;
            g.setColor(ParticleSystem.withAlpha(e.telegraphColor, 0.72));
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{8f, 6f}, 0f));
            g.draw(new Line2D.Double(e.telegraphX1 - camX, e.telegraphY1 - camY, e.telegraphX2 - camX, e.telegraphY2 - camY));
        }

        // 7. Gegner (nur wenn im sichtbaren Feld oder Boss)
        for (Enemy e : session.getEnemies()) {
            if (!e.active) continue;
            int tx = map.worldToTile(e.x);
            int ty = map.worldToTile(e.y);
            if (map.getVisibility(tx, ty) != DungeonMap.Visibility.VISIBLE) continue;
            drawEnemy(g, e, camX, camY, globalTime);
        }

        // 8. Spieler
        drawPlayer(g, session.getPlayer(), camX, camY, globalTime);

        // 9. Projektile
        for (Projectile p : session.getProjectiles()) {
            if (!p.active) continue;
            int tx = map.worldToTile(p.x);
            int ty = map.worldToTile(p.y);
            if (map.getVisibility(tx, ty) != DungeonMap.Visibility.VISIBLE && !p.fromPlayer) continue;
            drawProjectile(g, p, camX, camY);
        }

        // 10. Partikel, Bögen, Blitze, Schockwellen
        session.getParticles().render(g, camX, camY);

        // 11. Fog-of-War-Verdunklung für nur erkundete, nicht aktiv gesehene Tiles
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                DungeonMap.Visibility vis = map.getVisibility(tx, ty);
                if (vis == DungeonMap.Visibility.EXPLORED) {
                    int sx = (int) Math.round(tx * ts - camX);
                    int sy = (int) Math.round(ty * ts - camY);
                    g.setColor(new Color(6, 7, 14, 155));
                    g.fillRect(sx, sy, ts, ts);
                } else if (vis == DungeonMap.Visibility.VISIBLE) {
                    double light = map.getLightLevel(tx, ty);
                    if (light < 0.95) {
                        int alpha = (int) Math.round((1.0 - light) * 115.0);
                        int sx = (int) Math.round(tx * ts - camX);
                        int sy = (int) Math.round(ty * ts - camY);
                        g.setColor(new Color(6, 7, 14, Math.max(0, Math.min(180, alpha))));
                        g.fillRect(sx, sy, ts, ts);
                    }
                }
            }
        }

        // 12. Schwebende Schadens- & Statustexte (über Fog-of-War lesbar)
        for (FloatingText ft : session.getFloatingTexts()) {
            double sx = ft.x - camX;
            double sy = ft.y - camY;
            g.setFont(new Font("SansSerif", Font.BOLD, Math.round(ft.fontSize)));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(ft.text);
            double alpha = ft.alpha();
            g.setColor(new Color(0, 0, 0, (int) (alpha * 190)));
            g.drawString(ft.text, (float) (sx - tw / 2.0 + 1), (float) (sy + 1));
            g.setColor(ParticleSystem.withAlpha(ft.color, alpha));
            g.drawString(ft.text, (float) (sx - tw / 2.0), (float) sy);
        }

        // 13. Taktisches Fadenkreuz an Mausposition
        drawCrosshair(g, mouseLogicalX, mouseLogicalY, session.getPlayer().heroClass.accentColor);
    }

    private void drawFloorTile(Graphics2D g, int sx, int sy, int ts, int tx, int ty, Color base) {
        int shade = ((tx + ty) % 2 == 0) ? 0 : -4;
        g.setColor(new Color(
                Math.max(0, base.getRed() + shade),
                Math.max(0, base.getGreen() + shade),
                Math.max(0, base.getBlue() + shade)
        ));
        g.fillRect(sx, sy, ts, ts);
        g.setColor(new Color(0, 0, 0, 35));
        g.drawRect(sx, sy, ts - 1, ts - 1);
    }

    private void drawSpikeTrap(Graphics2D g, int sx, int sy, int ts, double trapTimer) {
        g.setColor(new Color(20, 20, 28));
        g.fillRect(sx + 6, sy + 6, ts - 12, ts - 12);

        if (trapTimer >= 2.1) {
            // Ausgefahrene Stacheln
            g.setColor(new Color(220, 75, 75));
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    int bx = sx + 13 + col * 16;
                    int by = sy + 18 + row * 14;
                    Polygon spike = new Polygon(
                            new int[]{bx - 5, bx, bx + 5},
                            new int[]{by, by - 12, by},
                            3
                    );
                    g.fillPolygon(spike);
                    g.setColor(new Color(235, 240, 250));
                    g.drawPolygon(spike);
                    g.setColor(new Color(220, 75, 75));
                }
            }
        } else if (trapTimer >= 1.7) {
            // Orange Warnphase
            g.setColor(new Color(255, 165, 50, 145));
            g.fillRect(sx + 8, sy + 8, ts - 16, ts - 16);
        } else {
            g.setColor(new Color(45, 45, 58));
            g.fillOval(sx + 11, sy + 11, 6, 6);
            g.fillOval(sx + ts - 17, sy + 11, 6, 6);
            g.fillOval(sx + 11, sy + ts - 17, 6, 6);
            g.fillOval(sx + ts - 17, sy + ts - 17, 6, 6);
        }
    }

    private void drawInteractiveProp(Graphics2D g, InteractiveProp prop, double camX, double camY, boolean portalUnlocked) {
        double sx = prop.x - camX;
        double sy = prop.y - camY;
        double pulse = 0.5 + 0.5 * Math.sin(prop.pulseTime * 4.0);

        switch (prop.type) {
            case CHEST -> {
                g.setColor(new Color(0, 0, 0, 120));
                g.fillOval((int) (sx - 16), (int) (sy + 6), 32, 12);
                g.setColor(prop.used ? new Color(82, 62, 46) : new Color(148, 92, 42));
                g.fillRoundRect((int) (sx - 14), (int) (sy - 11), 28, 22, 5, 5);
                g.setColor(prop.used ? new Color(110, 100, 85) : new Color(255, 204, 68));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect((int) (sx - 14), (int) (sy - 11), 28, 22, 5, 5);
                if (!prop.used) {
                    g.fillRect((int) (sx - 3), (int) (sy - 3), 6, 7);
                }
            }
            case HEALING_SHRINE -> {
                Color c = prop.used ? new Color(80, 95, 90) : new Color(80, 245, 155);
                if (!prop.used) {
                    g.setColor(ParticleSystem.withAlpha(c, 0.16 + 0.12 * pulse));
                    g.fill(new Ellipse2D.Double(sx - 28, sy - 28, 56, 56));
                }
                g.setColor(new Color(70, 82, 88));
                g.fillRoundRect((int) (sx - 12), (int) (sy - 4), 24, 16, 4, 4);
                g.setColor(c);
                Polygon crystal = new Polygon(
                        new int[]{(int) sx, (int) (sx + 9), (int) sx, (int) (sx - 9)},
                        new int[]{(int) (sy - 22), (int) (sy - 8), (int) (sy + 2), (int) (sy - 8)},
                        4
                );
                g.fillPolygon(crystal);
            }
            case RELIC_ALTAR -> {
                Color c = prop.used ? new Color(90, 82, 105) : new Color(188, 108, 255);
                if (!prop.used) {
                    g.setColor(ParticleSystem.withAlpha(c, 0.18 + 0.12 * pulse));
                    g.fill(new Ellipse2D.Double(sx - 28, sy - 28, 56, 56));
                }
                g.setColor(new Color(62, 56, 82));
                g.fillRoundRect((int) (sx - 13), (int) (sy - 6), 26, 18, 4, 4);
                g.setColor(c);
                double bob = Math.sin(prop.pulseTime * 3.2) * 3.0;
                g.fill(new Ellipse2D.Double(sx - 8, sy - 22 + bob, 16, 16));
            }
            case EXIT_PORTAL -> {
                Color ringCol = portalUnlocked ? new Color(72, 238, 255) : new Color(220, 68, 86);
                g.setColor(ParticleSystem.withAlpha(ringCol, 0.22 + 0.12 * pulse));
                g.fill(new Ellipse2D.Double(sx - 34, sy - 34, 68, 68));
                g.setColor(ringCol);
                g.setStroke(new BasicStroke(3.2f));
                g.draw(new Ellipse2D.Double(sx - 24, sy - 24, 48, 48));
                if (portalUnlocked) {
                    AffineTransform old = g.getTransform();
                    g.rotate(prop.pulseTime * 2.2, sx, sy);
                    g.setColor(new Color(200, 252, 255, 190));
                    g.drawRect((int) (sx - 13), (int) (sy - 13), 26, 26);
                    g.setTransform(old);
                }
            }
        }
    }

    private void drawPickup(Graphics2D g, Pickup pk, double camX, double camY) {
        double bob = Math.sin(pk.animTime * 5.0) * 2.5;
        double sx = pk.x - camX;
        double sy = pk.y - camY + bob;

        switch (pk.kind) {
            case XP_GEM -> {
                g.setColor(new Color(90, 240, 255, 80));
                g.fill(new Ellipse2D.Double(sx - 9, sy - 9, 18, 18));
                g.setColor(new Color(108, 232, 255));
                Polygon p = new Polygon(
                        new int[]{(int) sx, (int) (sx + 6), (int) sx, (int) (sx - 6)},
                        new int[]{(int) (sy - 7), (int) sy, (int) (sy + 7), (int) sy},
                        4
                );
                g.fillPolygon(p);
            }
            case GOLD_COIN -> {
                g.setColor(new Color(255, 205, 55));
                g.fill(new Ellipse2D.Double(sx - 6, sy - 6, 12, 12));
                g.setColor(new Color(255, 245, 165));
                g.draw(new Ellipse2D.Double(sx - 6, sy - 6, 12, 12));
            }
            case HEALTH_POTION -> {
                g.setColor(new Color(245, 72, 92));
                g.fill(new Ellipse2D.Double(sx - 7, sy - 6, 14, 14));
                g.setColor(Color.WHITE);
                g.fillRect((int) (sx - 1), (int) (sy - 3), 2, 8);
                g.fillRect((int) (sx - 4), (int) sy, 8, 2);
            }
            case SHIELD_ORB -> {
                g.setColor(new Color(88, 178, 255));
                g.fill(new Ellipse2D.Double(sx - 7, sy - 7, 14, 14));
                g.setColor(new Color(205, 238, 255));
                g.draw(new Ellipse2D.Double(sx - 7, sy - 7, 14, 14));
            }
        }
    }

    private void drawEnemy(Graphics2D g, Enemy e, double camX, double camY, double time) {
        double sx = e.x - camX;
        double sy = e.y - camY;
        double r = e.radius;

        // Bodenschatten
        g.setColor(new Color(0, 0, 0, 115));
        g.fill(new Ellipse2D.Double(sx - r * 0.9, sy + r * 0.45, r * 1.8, r * 0.7));

        Color bodyColor = (e.hitFlashTimer > 0.0) ? Color.WHITE : e.type.color;

        switch (e.type) {
            case SLIME, ELDER_SLIME -> {
                double squish = 1.0 + 0.10 * Math.sin(e.animTime * 6.5);
                double w = r * 2.0 * squish;
                double h = r * 1.8 / squish;
                g.setColor(bodyColor);
                g.fill(new Ellipse2D.Double(sx - w / 2.0, sy - h / 2.0, w, h));
                g.setColor(new Color(255, 255, 255, 95));
                g.fill(new Ellipse2D.Double(sx - w * 0.25, sy - h * 0.3, w * 0.25, h * 0.2));
                g.setColor(Color.BLACK);
                g.fillOval((int) (sx - 5), (int) (sy - 3), 3, 4);
                g.fillOval((int) (sx + 2), (int) (sy - 3), 3, 4);
            }
            case SKELETON_ARCHER -> {
                g.setColor(bodyColor);
                g.fill(new Ellipse2D.Double(sx - r * 0.8, sy - r * 0.8, r * 1.6, r * 1.6));
                g.setColor(new Color(65, 58, 72));
                g.draw(new Ellipse2D.Double(sx - r * 0.8, sy - r * 0.8, r * 1.6, r * 1.6));
                // Bogen in Blickrichtung
                AffineTransform old = g.getTransform();
                g.rotate(e.facingAngle, sx, sy);
                g.setColor(new Color(180, 120, 62));
                g.setStroke(new BasicStroke(2.6f));
                g.drawArc((int) (sx - 2), (int) (sy - 12), 16, 24, -75, 150);
                g.setTransform(old);
            }
            case BERSERKER -> {
                g.setColor(bodyColor);
                g.fillRoundRect((int) (sx - r), (int) (sy - r), (int) (r * 2), (int) (r * 2), 10, 10);
                g.setColor(new Color(255, 210, 120));
                // Hörner
                g.fillPolygon(
                        new int[]{(int) (sx - r), (int) (sx - r - 6), (int) (sx - r + 4)},
                        new int[]{(int) (sy - 4), (int) (sy - 14), (int) (sy - 10)},
                        3
                );
                g.fillPolygon(
                        new int[]{(int) (sx + r), (int) (sx + r + 6), (int) (sx + r - 4)},
                        new int[]{(int) (sy - 4), (int) (sy - 14), (int) (sy - 10)},
                        3
                );
            }
            case NECROMANCER -> {
                g.setColor(bodyColor);
                Polygon robe = new Polygon(
                        new int[]{(int) sx, (int) (sx + r), (int) (sx - r)},
                        new int[]{(int) (sy - r - 4), (int) (sy + r), (int) (sy + r)},
                        3
                );
                g.fillPolygon(robe);
                g.setColor(new Color(230, 180, 255));
                g.fillOval((int) (sx - 4), (int) (sy - r + 2), 8, 8);
            }
            case BOSS_MALAKOR -> {
                // Rotierende Chronos-Aura
                AffineTransform old = g.getTransform();
                g.rotate(time * 1.4, sx, sy);
                g.setColor(e.bossPhase == 2 ? new Color(255, 90, 60, 165) : new Color(255, 65, 130, 140));
                g.setStroke(new BasicStroke(3f));
                g.drawRect((int) (sx - r - 6), (int) (sy - r - 6), (int) ((r + 6) * 2), (int) ((r + 6) * 2));
                g.setTransform(old);

                g.setPaint(new GradientPaint((float) (sx - r), (float) (sy - r), bodyColor, (float) (sx + r), (float) (sy + r), new Color(115, 18, 52)));
                g.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                g.setColor(new Color(255, 228, 120));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
                g.fillOval((int) (sx - 8), (int) (sy - 5), 5, 5);
                g.fillOval((int) (sx + 3), (int) (sy - 5), 5, 5);
            }
        }

        // Statuseffekt-Indikatoren
        if (e.hasStatus(StatusEffect.Type.SLOW)) {
            g.setColor(new Color(120, 235, 255, 180));
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(sx - r - 3, sy - r - 3, (r + 3) * 2, (r + 3) * 2));
        }
        if (e.hasStatus(StatusEffect.Type.BURN)) {
            g.setColor(new Color(255, 145, 40));
            g.fillOval((int) (sx - 4), (int) (sy - r - 12), 8, 8);
        }
        if (e.hasStatus(StatusEffect.Type.STUN)) {
            g.setColor(new Color(255, 230, 80));
            g.fillOval((int) (sx - 8 + Math.cos(time * 9) * 6), (int) (sy - r - 10), 5, 5);
            g.fillOval((int) (sx + 4 - Math.cos(time * 9) * 6), (int) (sy - r - 10), 5, 5);
        }

        // Mini-HP-Balken bei Schaden (Nicht-Bosse)
        if (!e.type.isBoss && e.hp < e.maxHp) {
            double bw = Math.max(26, r * 2.1);
            double bx = sx - bw / 2.0;
            double by = sy - r - 9;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect((int) bx - 1, (int) by - 1, (int) bw + 2, 6);
            g.setColor(new Color(235, 68, 78));
            g.fillRect((int) bx, (int) by, (int) (bw * Math.max(0.0, e.hp / e.maxHp)), 4);
        }
    }

    private void drawPlayer(Graphics2D g, Player p, double camX, double camY, double time) {
        double sx = p.x - camX;
        double sy = p.y - camY;
        double r = p.radius;

        // Schatten
        g.setColor(new Color(0, 0, 0, 130));
        g.fill(new Ellipse2D.Double(sx - r, sy + r * 0.45, r * 2, r * 0.75));

        // Aktiver Schild-Ring
        if (p.shield > 0.5) {
            g.setColor(new Color(95, 200, 255, 95));
            g.setStroke(new BasicStroke(2.2f));
            g.draw(new Ellipse2D.Double(sx - r - 5, sy - r - 5, (r + 5) * 2, (r + 5) * 2));
        }

        // Charakterkörper + Waffe rotiert zur Maus
        AffineTransform old = g.getTransform();
        g.rotate(p.facingAngle, sx, sy);

        Color body = (p.hitFlashTimer > 0.0) ? Color.WHITE : p.heroClass.primaryColor;
        g.setColor(body);
        g.fill(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));
        g.setColor(p.heroClass.accentColor);
        g.setStroke(new BasicStroke(2.2f));
        g.draw(new Ellipse2D.Double(sx - r, sy - r, r * 2, r * 2));

        // Klassenspezifische Silhouette / Waffe
        if (p.heroClass == HeroClass.VANGUARD) {
            // Großschwert & Schild
            g.setColor(new Color(210, 232, 255));
            g.fillRoundRect((int) (sx + r - 2), (int) (sy - 3), 20, 6, 3, 3);
            g.setColor(new Color(100, 185, 255));
            g.fillRoundRect((int) (sx + 4), (int) (sy + r - 3), 12, 6, 3, 3);
        } else if (p.heroClass == HeroClass.ARCANIST) {
            // Arkanstab mit leuchtendem Kristall
            g.setColor(new Color(150, 100, 65));
            g.fillRect((int) (sx + 4), (int) (sy - 2), 18, 4);
            g.setColor(new Color(120, 245, 255));
            g.fillOval((int) (sx + 19), (int) (sy - 5), 10, 10);
        } else {
            // Schattenjäger Zwillingsdolche
            g.setColor(new Color(210, 255, 160));
            g.fillPolygon(
                    new int[]{(int) (sx + r), (int) (sx + r + 14), (int) (sx + r)},
                    new int[]{(int) (sy - 7), (int) (sy - 4), (int) (sy - 1)},
                    3
            );
            g.fillPolygon(
                    new int[]{(int) (sx + r), (int) (sx + r + 14), (int) (sx + r)},
                    new int[]{(int) (sy + 1), (int) (sy + 4), (int) (sy + 7)},
                    3
            );
        }
        g.setTransform(old);

        // Unverwundbarkeits-Schimmer
        if (p.isInvulnerable()) {
            g.setColor(new Color(255, 255, 255, 100));
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(sx - r - 2, sy - r - 2, (r + 2) * 2, (r + 2) * 2));
        }

        // Frostkranz-Visualisierung
        if (p.getUpgradeCount("FROST_AURA") > 0) {
            double pulse = 108.0 + Math.sin(time * 4.0) * 5.0;
            g.setColor(new Color(120, 225, 255, 42));
            g.setStroke(new BasicStroke(1.6f));
            g.draw(new Ellipse2D.Double(sx - pulse, sy - pulse, pulse * 2, pulse * 2));
        }
    }

    private void drawProjectile(Graphics2D g, Projectile p, double camX, double camY) {
        double sx = p.x - camX;
        double sy = p.y - camY;
        double angle = Math.atan2(p.vy, p.vx);

        g.setColor(ParticleSystem.withAlpha(p.color, 0.32));
        g.fill(new Ellipse2D.Double(sx - p.radius * 1.8, sy - p.radius * 1.8, p.radius * 3.6, p.radius * 3.6));

        AffineTransform old = g.getTransform();
        g.rotate(angle, sx, sy);
        g.setColor(p.color);
        g.fill(new Ellipse2D.Double(sx - p.radius * 1.4, sy - p.radius * 0.75, p.radius * 2.8, p.radius * 1.5));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(sx - p.radius * 0.5, sy - p.radius * 0.4, p.radius * 1.2, p.radius * 0.8));
        g.setTransform(old);
    }

    private void drawCrosshair(Graphics2D g, double mx, double my, Color accent) {
        g.setColor(ParticleSystem.withAlpha(accent, 0.85));
        g.setStroke(new BasicStroke(1.8f));
        g.draw(new Ellipse2D.Double(mx - 8, my - 8, 16, 16));
        g.draw(new Line2D.Double(mx - 13, my, mx - 5, my));
        g.draw(new Line2D.Double(mx + 5, my, mx + 13, my));
        g.draw(new Line2D.Double(mx, my - 13, mx, my - 5));
        g.draw(new Line2D.Double(mx, my + 5, mx, my + 13));
    }
}
