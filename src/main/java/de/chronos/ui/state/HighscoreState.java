package de.chronos.ui.state;

import de.chronos.combat.Achievement;
import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;
import de.chronos.core.SaveManager;
import de.chronos.core.event.GameEvent;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Bestenliste (Top 10 Runs) und Übersicht aller freigeschalteten Erfolge.
 */
public final class HighscoreState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;
    private final Rectangle backBtn = new Rectangle(510, 642, 260, 44);

    public HighscoreState(StateManager stateManager, EngineContext ctx) {
        this.stateManager = stateManager;
        this.ctx = ctx;
    }

    @Override
    public void update(double dt, InputHandler input) {
        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE) || UiHelper.isClicked(backBtn, input)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.pop();
        }
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        g.setPaint(new GradientPaint(0, 0, new Color(11, 13, 24), 0, GameConfig.LOGICAL_HEIGHT, new Color(18, 16, 32)));
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        UiHelper.drawCenteredString(g, I18n.t("hs.title"), GameConfig.LOGICAL_WIDTH / 2, 62,
                new Font("SansSerif", Font.BOLD, 30), new Color(255, 214, 92));

        // Linkes Panel: Highscores
        int lx = 48, ly = 96, lw = 720, lh = 515;
        g.setColor(new Color(18, 22, 36, 230));
        g.fillRoundRect(lx, ly, lw, lh, 14, 14);
        g.setColor(new Color(80, 102, 148));
        g.drawRoundRect(lx, ly, lw, lh, 14, 14);

        List<SaveManager.HighscoreEntry> scores = ctx.saveManager.getHighscores();
        g.setFont(new Font("Monospaced", Font.BOLD, 13));
        g.setColor(new Color(145, 200, 255));
        g.drawString(String.format("%-3s %-7s %-12s %-10s %-5s %-6s %-6s %-6s",
                "#", "SCORE", "CLASS", "DIFF", "FLR", "KILLS", "TIME", "RES"), lx + 20, ly + 34);

        if (scores.isEmpty()) {
            UiHelper.drawCenteredString(g, I18n.t("hs.empty"), lx + lw / 2, ly + 250,
                    new Font("SansSerif", Font.PLAIN, 16), new Color(155, 168, 195));
        } else {
            g.setFont(new Font("Monospaced", Font.PLAIN, 13));
            for (int i = 0; i < scores.size(); i++) {
                SaveManager.HighscoreEntry e = scores.get(i);
                int ry = ly + 68 + i * 42;
                g.setColor(i == 0 ? new Color(255, 220, 105) : Color.WHITE);
                String tStr = String.format("%02d:%02d", (int) (e.durationSeconds() / 60), (int) (e.durationSeconds() % 60));
                String res = e.victory() ? "WIN" : "RIP";
                String line = String.format("%-3d %-7d %-12s %-10s %-5d %-6d %-6s %-6s",
                        i + 1, e.score(), e.heroClass(), e.difficulty(), e.floorReached(), e.kills(), tStr, res);
                g.drawString(line, lx + 20, ry);
            }
        }

        // Rechtes Panel: Erfolge
        int rx = 796, ry = 96, rw = 436, rh = 515;
        g.setColor(new Color(18, 22, 36, 230));
        g.fillRoundRect(rx, ry, rw, rh, 14, 14);
        g.setColor(new Color(80, 102, 148));
        g.drawRoundRect(rx, ry, rw, rh, 14, 14);

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(new Color(255, 214, 92));
        g.drawString(I18n.t("hs.achievements") + " (" + ctx.saveManager.getUnlockedAchievements().size()
                + "/" + Achievement.ALL.size() + ")", rx + 20, ry + 32);

        int ay = ry + 52;
        for (Achievement a : Achievement.ALL) {
            boolean unlocked = ctx.saveManager.isAchievementUnlocked(a.id);
            g.setColor(unlocked ? new Color(36, 46, 70) : new Color(18, 20, 30));
            g.fillRoundRect(rx + 16, ay, rw - 32, 48, 8, 8);
            g.setColor(unlocked ? new Color(255, 205, 72) : new Color(56, 64, 88));
            g.drawRoundRect(rx + 16, ay, rw - 32, 48, 8, 8);

            g.setFont(new Font("SansSerif", Font.BOLD, 13));
            g.setColor(unlocked ? new Color(255, 225, 115) : new Color(120, 130, 155));
            g.drawString((unlocked ? "[✓] " : "[ ] ") + a.getTitle(), rx + 28, ay + 20);

            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            g.setColor(unlocked ? new Color(205, 216, 236) : new Color(95, 104, 126));
            g.drawString(a.getDescription(), rx + 28, ay + 38);
            ay += 56;
        }

        UiHelper.drawButton(g, backBtn, I18n.t("hs.back"), UiHelper.isHovered(backBtn, input), new Color(115, 215, 255));
    }
}
