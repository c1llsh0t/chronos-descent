package de.chronos.ui.state;

import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;
import de.chronos.core.event.GameEvent;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

/**
 * Endbildschirm für Sieg oder Niederlage mit detaillierter Run-Auswertung.
 */
public final class RunEndState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;
    private final GameSession session;

    private final Rectangle retryBtn = new Rectangle(380, 540, 240, 48);
    private final Rectangle menuBtn = new Rectangle(660, 540, 240, 48);

    public RunEndState(StateManager stateManager, EngineContext ctx, GameSession session) {
        this.stateManager = stateManager;
        this.ctx = ctx;
        this.session = session;
    }

    @Override
    public void update(double dt, InputHandler input) {
        if (UiHelper.isClicked(retryBtn, input) || input.wasKeyPressed(KeyEvent.VK_ENTER)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            GameSession fresh = new GameSession(
                    session.getPlayer().heroClass,
                    session.difficulty,
                    System.nanoTime(),
                    ctx.eventBus,
                    ctx.saveManager
            );
            stateManager.replaceAll(new MenuState(stateManager, ctx));
            stateManager.push(new PlayingState(stateManager, ctx, fresh));
        } else if (UiHelper.isClicked(menuBtn, input) || input.wasKeyPressed(KeyEvent.VK_ESCAPE)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.replaceAll(new MenuState(stateManager, ctx));
        }
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        Color top = session.victory ? new Color(14, 28, 42) : new Color(32, 12, 20);
        g.setPaint(new GradientPaint(0, 0, top, 0, GameConfig.LOGICAL_HEIGHT, new Color(10, 11, 18)));
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        UiHelper.drawCenteredString(
                g,
                session.victory ? I18n.t("end.victory") : I18n.t("end.gameover"),
                GameConfig.LOGICAL_WIDTH / 2, 120,
                new Font("SansSerif", Font.BOLD, 34),
                session.victory ? new Color(255, 218, 88) : new Color(255, 92, 102)
        );

        int bx = 340, by = 175, bw = 600, bh = 325;
        g.setColor(new Color(18, 22, 36, 235));
        g.fillRoundRect(bx, by, bw, bh, 16, 16);
        g.setColor(session.victory ? new Color(255, 208, 82) : new Color(220, 75, 90));
        g.drawRoundRect(bx, by, bw, bh, 16, 16);

        int rowY = by + 50;
        drawStatRow(g, bx + 42, rowY, bw - 84, I18n.t("hud.score"), String.valueOf(session.getPlayer().score), new Color(255, 220, 95));
        drawStatRow(g, bx + 42, rowY + 40, bw - 84, I18n.t("end.floor_reached"), session.getCurrentFloor() + " / " + GameConfig.MAX_FLOORS, Color.WHITE);
        drawStatRow(g, bx + 42, rowY + 80, bw - 84, I18n.t("end.kills"), String.valueOf(session.totalKills), Color.WHITE);
        drawStatRow(g, bx + 42, rowY + 120, bw - 84, I18n.t("end.damage"), String.valueOf((int) Math.round(session.totalDamageDealt)), Color.WHITE);
        drawStatRow(g, bx + 42, rowY + 160, bw - 84, I18n.t("end.max_combo"), session.getPlayer().maxCombo + "x", new Color(255, 155, 80));
        drawStatRow(g, bx + 42, rowY + 200, bw - 84, I18n.t("end.duration"),
                String.format("%02d:%02d", (int) (session.runDurationSeconds / 60), (int) (session.runDurationSeconds % 60)), Color.WHITE);
        drawStatRow(g, bx + 42, rowY + 240, bw - 84, I18n.t("hud.seed"), String.valueOf(session.seed), new Color(135, 215, 255));

        UiHelper.drawButton(g, retryBtn, I18n.t("end.play_again"), UiHelper.isHovered(retryBtn, input), new Color(100, 225, 255));
        UiHelper.drawButton(g, menuBtn, I18n.t("end.menu"), UiHelper.isHovered(menuBtn, input), new Color(200, 212, 235));
    }

    private void drawStatRow(Graphics2D g, int x, int y, int w, String label, String value, Color valColor) {
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.setColor(new Color(180, 195, 220));
        g.drawString(label, x, y);

        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(valColor);
        int vw = g.getFontMetrics().stringWidth(value);
        g.drawString(value, x + w - vw, y);
    }
}
