package de.chronos.ui;

import de.chronos.combat.Upgrade;
import de.chronos.combat.UpgradePool;
import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.entity.Enemy;
import de.chronos.entity.InteractiveProp;
import de.chronos.entity.Player;
import de.chronos.ui.state.GameSession;
import de.chronos.world.DungeonMap;
import de.chronos.world.Room;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Map;

/**
 * Zeichnet das vollständige HUD: Lebens-/Schild-/XP-Balken, Cooldown-Leiste,
 * Live-Minimap, große Karten-Überlagerung [TAB], Boss-Balken, Relikt-Übersicht und Erfolgsmeldungen.
 */
public final class HudRenderer {

    private static final Color PANEL_BG = new Color(12, 14, 24, 210);
    private static final Color PANEL_BORDER = new Color(75, 92, 130, 180);

    public void render(Graphics2D g, GameSession session, boolean showFullMap, boolean muted) {
        Player p = session.getPlayer();

        drawTopLeftStats(g, session, p);
        drawTopCenterFloorAndBoss(g, session);
        drawTopRightMinimap(g, session);
        drawSkillDock(g, p, muted);
        drawRelicSidebar(g, p);
        drawInteractPrompt(g, session);
        drawAchievementToast(g, session);

        if (showFullMap) {
            drawFullMapOverlay(g, session);
        }
    }

    private void drawTopLeftStats(Graphics2D g, GameSession session, Player p) {
        g.setColor(PANEL_BG);
        g.fillRoundRect(16, 16, 286, 128, 12, 12);
        g.setColor(PANEL_BORDER);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(16, 16, 286, 128, 12, 12);

        // Klassenname & Level
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(p.heroClass.accentColor);
        g.drawString(p.heroClass.getDisplayName(), 28, 38);

        g.setColor(new Color(255, 218, 92));
        String lvStr = I18n.t("hud.level") + " " + p.level;
        g.drawString(lvStr, 232, 38);

        // HP-Balken
        drawBar(g, 28, 48, 260, 18, p.hp / Math.max(1.0, p.maxHp),
                new Color(55, 22, 28), new Color(228, 62, 78),
                String.format("HP  %d / %d", (int) Math.ceil(p.hp), (int) Math.ceil(p.maxHp)));

        // Schild-Balken
        if (p.maxShield > 0 || p.shield > 0) {
            double denom = Math.max(1.0, Math.max(p.maxShield, p.shield));
            drawBar(g, 28, 70, 260, 12, p.shield / denom,
                    new Color(20, 35, 56), new Color(78, 185, 255),
                    String.format("SHD %d", (int) Math.ceil(p.shield)));
        }

        // XP-Balken
        int xpY = (p.maxShield > 0 || p.shield > 0) ? 87 : 74;
        drawBar(g, 28, xpY, 260, 10, (double) p.xp / Math.max(1, p.xpToNext),
                new Color(22, 28, 42), new Color(92, 232, 255),
                "XP " + p.xp + " / " + p.xpToNext);

        // Gold, Score, Kombo
        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(new Color(255, 212, 70));
        g.drawString(I18n.t("hud.gold") + ": " + p.gold, 28, 128);

        g.setColor(new Color(215, 225, 245));
        g.drawString(I18n.t("hud.score") + ": " + p.score, 128, 128);

        if (p.combo >= 2) {
            g.setColor(new Color(255, 135, 72));
            g.drawString(p.combo + "x", 262, 128);
        }
    }

    private void drawBar(Graphics2D g, int x, int y, int w, int h, double ratio, Color bg, Color fg, String text) {
        double clamped = Math.max(0.0, Math.min(1.0, ratio));
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 6, 6);
        if (clamped > 0.0) {
            g.setColor(fg);
            g.fillRoundRect(x, y, (int) Math.round(w * clamped), h, 6, 6);
        }
        g.setColor(new Color(255, 255, 255, 45));
        g.drawRoundRect(x, y, w, h, 6, 6);

        if (h >= 12 && text != null) {
            g.setFont(new Font("SansSerif", Font.BOLD, h >= 16 ? 11 : 10));
            g.setColor(Color.WHITE);
            g.drawString(text, x + 8, y + h - 3);
        }
    }

    private void drawTopCenterFloorAndBoss(Graphics2D g, GameSession session) {
        String floorLabel = String.format("%s %d / %d  •  %s: %d  •  %02d:%02d",
                I18n.t("hud.floor"), session.getCurrentFloor(), GameConfig.MAX_FLOORS,
                I18n.t("hud.seed"), session.seed,
                (int) (session.runDurationSeconds / 60),
                (int) (session.runDurationSeconds % 60));

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        FontMetrics fm = g.getFontMetrics();
        int boxW = fm.stringWidth(floorLabel) + 32;
        int boxX = (GameConfig.LOGICAL_WIDTH - boxW) / 2;

        g.setColor(PANEL_BG);
        g.fillRoundRect(boxX, 14, boxW, 30, 10, 10);
        g.setColor(PANEL_BORDER);
        g.drawRoundRect(boxX, 14, boxW, 30, 10, 10);
        g.setColor(new Color(225, 232, 248));
        g.drawString(floorLabel, boxX + 16, 34);

        // Boss-Lebensbalken
        Enemy boss = session.getActiveBoss();
        if (boss != null) {
            int bw = 460;
            int bx = (GameConfig.LOGICAL_WIDTH - bw) / 2;
            int by = 54;
            g.setColor(PANEL_BG);
            g.fillRoundRect(bx - 12, by - 4, bw + 24, 46, 10, 10);
            g.setColor(new Color(255, 72, 105, 190));
            g.drawRoundRect(bx - 12, by - 4, bw + 24, 46, 10, 10);

            String bTitle = I18n.t("hud.boss_name") + (boss.bossPhase == 2 ? "  [" + I18n.t("hud.boss_phase2") + "]" : "");
            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(new Color(255, 190, 200));
            g.drawString(bTitle, bx, by + 13);

            drawBar(g, bx, by + 18, bw, 16, boss.hp / boss.maxHp,
                    new Color(45, 14, 22), new Color(238, 48, 86),
                    String.format("%d / %d", (int) Math.ceil(boss.hp), (int) Math.ceil(boss.maxHp)));
        }
    }

    private void drawTopRightMinimap(Graphics2D g, GameSession session) {
        int mapW = 196;
        int mapH = 140;
        int mx = GameConfig.LOGICAL_WIDTH - mapW - 16;
        int my = 16;

        g.setColor(PANEL_BG);
        g.fillRoundRect(mx, my, mapW, mapH, 12, 12);
        g.setColor(PANEL_BORDER);
        g.drawRoundRect(mx, my, mapW, mapH, 12, 12);

        renderMapCells(g, session, mx + 10, my + 10, mapW - 20, mapH - 28, false);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(165, 178, 204));
        g.drawString("[TAB] Map   •   [ESC] Pause", mx + 14, my + mapH - 7);
    }

    private void drawFullMapOverlay(Graphics2D g, GameSession session) {
        int mw = 740;
        int mh = 520;
        int mx = (GameConfig.LOGICAL_WIDTH - mw) / 2;
        int my = (GameConfig.LOGICAL_HEIGHT - mh) / 2;

        g.setColor(new Color(8, 10, 18, 235));
        g.fillRoundRect(mx, my, mw, mh, 16, 16);
        g.setColor(new Color(110, 145, 210));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(mx, my, mw, mh, 16, 16);

        renderMapCells(g, session, mx + 24, my + 24, mw - 48, mh - 64, true);

        g.setFont(new Font("SansSerif", Font.BOLD, 13));
        g.setColor(new Color(200, 212, 235));
        g.drawString("Blue: Start   |   Gold: Treasure   |   Green: Shrine   |   Red: Boss / Portal", mx + 30, my + mh - 16);
    }

    private void renderMapCells(Graphics2D g, GameSession session, int x, int y, int w, int h, boolean detailed) {
        DungeonMap map = session.getMap();
        double cellW = (double) w / map.getCols();
        double cellH = (double) h / map.getRows();
        double scale = Math.min(cellW, cellH);
        int ox = x + (int) ((w - map.getCols() * scale) / 2.0);
        int oy = y + (int) ((h - map.getRows() * scale) / 2.0);

        for (int r = 0; r < map.getRows(); r++) {
            for (int c = 0; c < map.getCols(); c++) {
                DungeonMap.Visibility vis = map.getVisibility(c, r);
                if (vis == DungeonMap.Visibility.UNSEEN) continue;
                if (map.isSolid(c, r)) continue;
                g.setColor(vis == DungeonMap.Visibility.VISIBLE
                        ? new Color(112, 132, 175)
                        : new Color(62, 72, 98));
                int px = ox + (int) (c * scale);
                int py = oy + (int) (r * scale);
                g.fillRect(px, py, Math.max(2, (int) Math.ceil(scale)), Math.max(2, (int) Math.ceil(scale)));
            }
        }

        // Raum-Marker für entdeckte Räume
        for (Room room : map.getRooms()) {
            if (!room.discovered) continue;
            int rx = ox + (int) (room.centerTileX() * scale);
            int ry = oy + (int) (room.centerTileY() * scale);
            Color marker = switch (room.type) {
                case START -> new Color(80, 170, 255);
                case TREASURE -> new Color(255, 204, 65);
                case SHRINE -> new Color(85, 235, 145);
                case BOSS -> new Color(255, 72, 96);
                default -> null;
            };
            if (marker != null) {
                int s = detailed ? 9 : 5;
                g.setColor(marker);
                g.fillOval(rx - s / 2, ry - s / 2, s, s);
            }
        }

        // Spielerpunkt
        Player p = session.getPlayer();
        int px = ox + (int) ((p.x / GameConfig.TILE_SIZE) * scale);
        int py = oy + (int) ((p.y / GameConfig.TILE_SIZE) * scale);
        int ps = detailed ? 9 : 5;
        g.setColor(Color.WHITE);
        g.fillOval(px - ps / 2, py - ps / 2, ps, ps);
    }

    private void drawSkillDock(Graphics2D g, Player p, boolean muted) {
        int slotSize = 54;
        int gap = 12;
        int totalW = slotSize * 4 + gap * 3;
        int startX = (GameConfig.LOGICAL_WIDTH - totalW) / 2;
        int y = GameConfig.LOGICAL_HEIGHT - slotSize - 18;

        drawSkillSlot(g, startX, y, slotSize, "LMB", "ATK", p.attackTimer, p.getEffectiveAttackCooldown(), p.heroClass.accentColor);
        drawSkillSlot(g, startX + (slotSize + gap), y, slotSize, "SPC", "DASH", p.dashCooldownTimer, p.getDashCooldownMax(), new Color(110, 225, 255));
        drawSkillSlot(g, startX + (slotSize + gap) * 2, y, slotSize, "Q", "SKILL", p.skillQTimer, p.heroClass.skillQCooldown * p.getCooldownScale(), p.heroClass.primaryColor);
        drawSkillSlot(g, startX + (slotSize + gap) * 3, y, slotSize, "E", "ULT", p.skillETimer, p.heroClass.skillECooldown * p.getCooldownScale(), new Color(255, 195, 75));

        if (muted) {
            g.setFont(new Font("SansSerif", Font.BOLD, 12));
            g.setColor(new Color(255, 120, 120));
            g.drawString(I18n.t("hud.muted"), GameConfig.LOGICAL_WIDTH - 120, GameConfig.LOGICAL_HEIGHT - 22);
        }
    }

    private void drawSkillSlot(Graphics2D g, int x, int y, int size, String key, String label, double cd, double maxCd, Color accent) {
        g.setColor(PANEL_BG);
        g.fillRoundRect(x, y, size, size, 10, 10);

        if (cd > 0.01 && maxCd > 0.01) {
            double ratio = Math.min(1.0, cd / maxCd);
            int fillH = (int) Math.round(size * ratio);
            g.setColor(new Color(15, 18, 28, 210));
            g.fillRoundRect(x, y + size - fillH, size, fillH, 8, 8);
            g.setColor(new Color(90, 100, 125));
        } else {
            g.setColor(accent);
        }

        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, size, size, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(195, 208, 232));
        g.drawString(key, x + 6, y + 14);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(cd > 0.01 ? new Color(255, 180, 110) : Color.WHITE);
        String centerTxt = cd > 0.01 ? String.format("%.1f", cd) : label;
        FontMetrics fm = g.getFontMetrics();
        g.drawString(centerTxt, x + (size - fm.stringWidth(centerTxt)) / 2, y + 36);
    }

    private void drawRelicSidebar(Graphics2D g, Player p) {
        Map<String, Integer> stacks = p.getUpgradeStacks();
        if (stacks.isEmpty()) return;

        int x = 16;
        int y = 160;
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(175, 192, 220));
        g.drawString(I18n.t("hud.upgrades") + ":", x + 2, y - 4);

        int row = 0;
        for (Map.Entry<String, Integer> entry : stacks.entrySet()) {
            if (row >= 9) break;
            Upgrade upg = findUpgrade(entry.getKey());
            if (upg == null) continue;
            int iy = y + row * 26;
            g.setColor(PANEL_BG);
            g.fillRoundRect(x, iy, 170, 22, 6, 6);
            g.setColor(upg.rarity.color);
            g.drawRoundRect(x, iy, 170, 22, 6, 6);
            g.drawString(upg.getName() + " x" + entry.getValue(), x + 8, iy + 15);
            row++;
        }
    }

    private Upgrade findUpgrade(String id) {
        for (Upgrade u : UpgradePool.getAll()) {
            if (u.id.equals(id)) return u;
        }
        return null;
    }

    private void drawInteractPrompt(Graphics2D g, GameSession session) {
        InteractiveProp prop = session.getNearestInteractableProp();
        if (prop == null) return;

        String msg = switch (prop.type) {
            case CHEST -> prop.used ? null : I18n.t("hud.interact_chest");
            case HEALING_SHRINE -> prop.used ? null : I18n.t("hud.interact_shrine");
            case RELIC_ALTAR -> prop.used ? null : I18n.t("hud.interact_altar");
            case EXIT_PORTAL -> session.portalUnlocked ? I18n.t("hud.interact_portal") : I18n.t("hud.portal_locked");
        };
        if (msg == null) return;

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(msg) + 28;
        int x = (GameConfig.LOGICAL_WIDTH - w) / 2;
        int y = GameConfig.LOGICAL_HEIGHT - 110;

        g.setColor(new Color(14, 18, 30, 230));
        g.fillRoundRect(x, y, w, 30, 10, 10);
        g.setColor(new Color(100, 235, 255));
        g.drawRoundRect(x, y, w, 30, 10, 10);
        g.setColor(Color.WHITE);
        g.drawString(msg, x + 14, y + 20);
    }

    private void drawAchievementToast(Graphics2D g, GameSession session) {
        GameSession.AchievementToast toast = session.getCurrentToast();
        if (toast == null) return;

        int w = 310;
        int h = 56;
        int x = (GameConfig.LOGICAL_WIDTH - w) / 2;
        int y = 108;

        g.setColor(new Color(18, 22, 36, 240));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(new Color(255, 205, 65));
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 12, 12);

        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        g.setColor(new Color(255, 205, 65));
        g.drawString(I18n.t("hud.achievement"), x + 14, y + 18);

        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.WHITE);
        g.drawString(toast.achievement.getTitle(), x + 14, y + 36);

        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g.setColor(new Color(190, 200, 220));
        g.drawString(toast.achievement.getDescription(), x + 14, y + 50);
    }
}
