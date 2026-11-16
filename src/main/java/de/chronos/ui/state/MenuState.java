package de.chronos.ui.state;

import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;
import de.chronos.core.event.GameEvent;
import de.chronos.entity.HeroClass;
import de.chronos.math.SeededRandom;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Hauptmenü mit animiertem Hintergrund, Klassenwahl, Schwierigkeit, Seed-Eingabe und Navigation.
 */
public final class MenuState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;

    private HeroClass selectedClass = HeroClass.VANGUARD;
    private GameConfig.Difficulty selectedDifficulty = GameConfig.Difficulty.NORMAL;
    private StringBuilder seedText = new StringBuilder();
    private boolean seedFocused = false;
    private double animTime = 0.0;

    private final Rectangle[] classCards = {
            new Rectangle(120, 210, 320, 175),
            new Rectangle(480, 210, 320, 175),
            new Rectangle(840, 210, 320, 175)
    };
    private final Rectangle[] diffButtons = {
            new Rectangle(120, 425, 160, 40),
            new Rectangle(292, 425, 160, 40),
            new Rectangle(464, 425, 160, 40)
    };
    private final Rectangle seedBox = new Rectangle(690, 425, 470, 40);

    private final Rectangle startBtn = new Rectangle(460, 505, 360, 50);
    private final Rectangle highscoreBtn = new Rectangle(240, 575, 250, 42);
    private final Rectangle helpBtn = new Rectangle(515, 575, 250, 42);
    private final Rectangle langBtn = new Rectangle(790, 575, 140, 42);
    private final Rectangle quitBtn = new Rectangle(945, 575, 120, 42);

    public MenuState(StateManager stateManager, EngineContext ctx) {
        this.stateManager = stateManager;
        this.ctx = ctx;
    }

    @Override
    public void update(double dt, InputHandler input) {
        animTime += dt;

        if (input.wasMousePressed(MouseEvent.BUTTON1)) {
            seedFocused = seedBox.contains(input.getMouseX(), input.getMouseY());
        }

        if (seedFocused) {
            Character ch;
            while ((ch = input.pollTypedChar()) != null) {
                if (ch == '\b') {
                    if (!seedText.isEmpty()) seedText.deleteCharAt(seedText.length() - 1);
                } else if (ch == '\n' || ch == '\r') {
                    seedFocused = false;
                } else if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_') {
                    if (seedText.length() < 20) seedText.append(ch);
                }
            }
            if (input.wasKeyPressed(KeyEvent.VK_ESCAPE)) {
                seedFocused = false;
            }
            return;
        }
        input.clearTypedChars();

        if (input.wasKeyPressed(KeyEvent.VK_1)) { selectedClass = HeroClass.VANGUARD; ctx.eventBus.publish(new GameEvent.UiClick()); }
        if (input.wasKeyPressed(KeyEvent.VK_2)) { selectedClass = HeroClass.ARCANIST; ctx.eventBus.publish(new GameEvent.UiClick()); }
        if (input.wasKeyPressed(KeyEvent.VK_3)) { selectedClass = HeroClass.SHADOWBLADE; ctx.eventBus.publish(new GameEvent.UiClick()); }
        if (input.wasKeyPressed(KeyEvent.VK_L)) { toggleLanguage(); }
        if (input.wasKeyPressed(KeyEvent.VK_M)) { toggleMute(); }
        if (input.wasKeyPressed(KeyEvent.VK_F11)) { ctx.toggleFullscreenAction.run(); }

        HeroClass[] classes = HeroClass.values();
        for (int i = 0; i < classes.length; i++) {
            if (UiHelper.isClicked(classCards[i], input)) {
                selectedClass = classes[i];
                ctx.eventBus.publish(new GameEvent.UiClick());
            }
        }

        GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            if (UiHelper.isClicked(diffButtons[i], input)) {
                selectedDifficulty = diffs[i];
                ctx.eventBus.publish(new GameEvent.UiClick());
            }
        }

        if (UiHelper.isClicked(startBtn, input) || input.wasKeyPressed(KeyEvent.VK_ENTER)) {
            startNewRun();
        } else if (UiHelper.isClicked(highscoreBtn, input)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.push(new HighscoreState(stateManager, ctx));
        } else if (UiHelper.isClicked(helpBtn, input)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.push(new HelpState(stateManager, ctx));
        } else if (UiHelper.isClicked(langBtn, input)) {
            toggleLanguage();
        } else if (UiHelper.isClicked(quitBtn, input)) {
            ctx.exitAction.run();
        }
    }

    private void toggleLanguage() {
        I18n.toggleLanguage();
        ctx.saveManager.setLanguage(I18n.getLanguage());
        ctx.eventBus.publish(new GameEvent.UiClick());
    }

    private void toggleMute() {
        ctx.soundEngine.toggleMute();
        ctx.saveManager.setMuted(ctx.soundEngine.isMuted());
    }

    private void startNewRun() {
        long seed = SeededRandom.parseSeed(seedText.toString());
        ctx.eventBus.publish(new GameEvent.UiClick());
        GameSession session = new GameSession(selectedClass, selectedDifficulty, seed, ctx.eventBus, ctx.saveManager);
        stateManager.push(new PlayingState(stateManager, ctx, session));
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        g.setPaint(new GradientPaint(0, 0, new Color(11, 13, 24), 0, GameConfig.LOGICAL_HEIGHT, new Color(20, 14, 34)));
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        // Animierte Hintergrund-Funken
        for (int i = 0; i < 36; i++) {
            double px = ((i * 197.3 + animTime * (12 + (i % 5) * 6)) % (GameConfig.LOGICAL_WIDTH + 40)) - 20;
            double py = ((i * 113.7 - animTime * (16 + (i % 4) * 5)) % GameConfig.LOGICAL_HEIGHT + GameConfig.LOGICAL_HEIGHT) % GameConfig.LOGICAL_HEIGHT;
            int alpha = 35 + (i % 4) * 20;
            g.setColor(new Color(120, 175, 255, alpha));
            g.fillOval((int) px, (int) py, 4, 4);
        }

        // Titel
        UiHelper.drawCenteredString(g, "CHRONOS", GameConfig.LOGICAL_WIDTH / 2, 78,
                new Font("SansSerif", Font.BOLD, 54), new Color(235, 242, 255));
        UiHelper.drawCenteredString(g, "AETHELGARD'S DESCENT  •  " + I18n.t("subtitle"),
                GameConfig.LOGICAL_WIDTH / 2, 112,
                new Font("SansSerif", Font.BOLD, 16), new Color(126, 204, 255));

        // Klassenwahl-Header
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        g.setColor(new Color(195, 208, 232));
        g.drawString(I18n.t("menu.select_class"), 120, 192);

        HeroClass[] classes = HeroClass.values();
        for (int i = 0; i < classes.length; i++) {
            drawClassCard(g, classCards[i], classes[i], i + 1, classes[i] == selectedClass, UiHelper.isHovered(classCards[i], input));
        }

        // Schwierigkeit
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(195, 208, 232));
        g.drawString(I18n.t("menu.difficulty"), 120, 412);

        GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            boolean active = diffs[i] == selectedDifficulty;
            Rectangle r = diffButtons[i];
            boolean hov = UiHelper.isHovered(r, input);
            g.setColor(active ? new Color(48, 72, 118) : (hov ? new Color(32, 38, 60) : new Color(20, 24, 38)));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            g.setColor(active ? new Color(110, 215, 255) : new Color(75, 92, 128));
            g.setStroke(new BasicStroke(active ? 2.2f : 1.3f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
            UiHelper.drawCenteredString(g, I18n.t("diff." + diffs[i].name()), r.x + r.width / 2, r.y + 25,
                    new Font("SansSerif", Font.BOLD, 14), active ? Color.WHITE : new Color(188, 200, 224));
        }

        // Seed-Feld
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(new Color(195, 208, 232));
        g.drawString(I18n.t("menu.seed"), seedBox.x, 412);

        g.setColor(seedFocused ? new Color(32, 42, 68) : new Color(20, 24, 38));
        g.fillRoundRect(seedBox.x, seedBox.y, seedBox.width, seedBox.height, 8, 8);
        g.setColor(seedFocused ? new Color(110, 225, 255) : new Color(75, 92, 128));
        g.setStroke(new BasicStroke(seedFocused ? 2.2f : 1.3f));
        g.drawRoundRect(seedBox.x, seedBox.y, seedBox.width, seedBox.height, 8, 8);

        g.setFont(new Font("Monospaced", Font.BOLD, 15));
        if (seedText.isEmpty()) {
            g.setColor(new Color(118, 130, 158));
            g.drawString(I18n.t("menu.seed_hint"), seedBox.x + 14, seedBox.y + 25);
        } else {
            g.setColor(Color.WHITE);
            String cursor = (seedFocused && ((int) (animTime * 2) % 2 == 0)) ? "_" : "";
            g.drawString(seedText + cursor, seedBox.x + 14, seedBox.y + 25);
        }

        // Haupt-Buttons
        UiHelper.drawButton(g, startBtn, I18n.t("menu.start"), UiHelper.isHovered(startBtn, input), selectedClass.accentColor);
        UiHelper.drawButton(g, highscoreBtn, I18n.t("menu.highscores"), UiHelper.isHovered(highscoreBtn, input), new Color(255, 206, 78));
        UiHelper.drawButton(g, helpBtn, I18n.t("menu.help"), UiHelper.isHovered(helpBtn, input), new Color(120, 210, 255));
        UiHelper.drawButton(g, langBtn, I18n.t("menu.language"), UiHelper.isHovered(langBtn, input), new Color(145, 235, 180));
        UiHelper.drawButton(g, quitBtn, I18n.t("menu.quit"), UiHelper.isHovered(quitBtn, input), new Color(255, 105, 115));

        UiHelper.drawCenteredString(g, I18n.t("menu.hint"), GameConfig.LOGICAL_WIDTH / 2, 665,
                new Font("SansSerif", Font.PLAIN, 13), new Color(148, 162, 192));
    }

    private void drawClassCard(Graphics2D g, Rectangle r, HeroClass hc, int keyNum, boolean selected, boolean hovered) {
        g.setColor(selected ? new Color(28, 36, 62) : (hovered ? new Color(24, 28, 48) : new Color(16, 20, 34)));
        g.fillRoundRect(r.x, r.y, r.width, r.height, 12, 12);

        g.setColor(selected ? hc.accentColor : new Color(68, 84, 120));
        g.setStroke(new BasicStroke(selected ? 2.6f : 1.4f));
        g.drawRoundRect(r.x, r.y, r.width, r.height, 12, 12);

        // Icon-Kreis
        g.setColor(hc.primaryColor);
        g.fillOval(r.x + 16, r.y + 18, 28, 28);
        g.setColor(hc.accentColor);
        g.drawOval(r.x + 16, r.y + 18, 28, 28);

        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(Color.WHITE);
        g.drawString("[" + keyNum + "] " + hc.getDisplayName(), r.x + 54, r.y + 34);

        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(hc.accentColor);
        g.drawString(hc.getRole(), r.x + 16, r.y + 62);

        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(new Color(195, 206, 228));
        FontMetrics fm = g.getFontMetrics();
        List<String> lines = UiHelper.wrapText(hc.getDescription(), fm, r.width - 32);
        int ly = r.y + 84;
        for (String line : lines) {
            g.drawString(line, r.x + 16, ly);
            ly += 17;
        }

        // Basis-Stats am Kartenende
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(new Color(165, 220, 255));
        String stats = String.format("HP:%d  ATK:%d  SPD:%d  CRIT:%d%%",
                (int) hc.baseHp, (int) hc.baseDamage, (int) hc.baseSpeed, (int) (hc.baseCritChance * 100));
        g.drawString(stats, r.x + 16, r.y + r.height - 14);
    }
}
