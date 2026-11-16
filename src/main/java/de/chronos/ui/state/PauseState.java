package de.chronos.ui.state;

import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;
import de.chronos.core.event.GameEvent;
import de.chronos.entity.Player;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

/**
 * Pause-Overlay mit aktuellen Run-Statistiken und Navigationsaktionen.
 */
public final class PauseState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;
    private final GameSession session;

    private final Rectangle resumeBtn = new Rectangle(470, 320, 340, 46);
    private final Rectangle restartBtn = new Rectangle(470, 382, 340, 46);
    private final Rectangle menuBtn = new Rectangle(470, 444, 340, 46);

    public PauseState(StateManager stateManager, EngineContext ctx, GameSession session) {
        this.stateManager = stateManager;
        this.ctx = ctx;
        this.session = session;
    }

    @Override
    public void update(double dt, InputHandler input) {
        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE) || input.wasKeyPressed(KeyEvent.VK_P)
                || UiHelper.isClicked(resumeBtn, input)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.pop();
        } else if (UiHelper.isClicked(restartBtn, input)) {
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
        } else if (UiHelper.isClicked(menuBtn, input)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.replaceAll(new MenuState(stateManager, ctx));
        }
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        GameState below = stateManager.getBelowTop();
        if (below != null) {
            below.render(g, input);
        }

        g.setColor(new Color(8, 10, 18, 195));
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        int px = 390, py = 140, pw = 500, ph = 390;
        g.setColor(new Color(18, 22, 36, 245));
        g.fillRoundRect(px, py, pw, ph, 16, 16);
        g.setColor(new Color(95, 130, 195));
        g.drawRoundRect(px, py, pw, ph, 16, 16);

        UiHelper.drawCenteredString(g, I18n.t("pause.title"), GameConfig.LOGICAL_WIDTH / 2, 195,
                new Font("SansSerif", Font.BOLD, 30), Color.WHITE);

        Player p = session.getPlayer();
        String telemetry = String.format("%s %d  |  %s %d  |  Kills: %d  |  DMG: %d  |  Seed: %d",
                I18n.t("hud.floor"), session.getCurrentFloor(),
                I18n.t("hud.level"), p.level,
                session.totalKills, (int) Math.round(session.totalDamageDealt), session.seed);
        UiHelper.drawCenteredString(g, telemetry, GameConfig.LOGICAL_WIDTH / 2, 245,
                new Font("SansSerif", Font.PLAIN, 13), new Color(175, 200, 235));

        UiHelper.drawButton(g, resumeBtn, I18n.t("pause.resume"), UiHelper.isHovered(resumeBtn, input), new Color(100, 225, 255));
        UiHelper.drawButton(g, restartBtn, I18n.t("pause.restart"), UiHelper.isHovered(restartBtn, input), new Color(255, 205, 80));
        UiHelper.drawButton(g, menuBtn, I18n.t("pause.menu"), UiHelper.isHovered(menuBtn, input), new Color(255, 105, 115));
    }
}
