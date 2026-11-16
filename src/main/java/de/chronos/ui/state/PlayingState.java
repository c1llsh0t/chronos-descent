package de.chronos.ui.state;

import de.chronos.core.InputHandler;
import de.chronos.graphics.WorldRenderer;
import de.chronos.ui.HudRenderer;

import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

/**
 * Aktiver Gameplay-Zustand: verarbeitet Echtzeiteingaben, aktualisiert die Simulation
 * und delegiert Rendering an WorldRenderer und HudRenderer.
 */
public final class PlayingState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;
    private final GameSession session;
    private final WorldRenderer worldRenderer = new WorldRenderer();
    private final HudRenderer hudRenderer = new HudRenderer();

    private double globalTime = 0.0;
    private boolean showFullMap = false;

    public PlayingState(StateManager stateManager, EngineContext ctx, GameSession session) {
        this.stateManager = stateManager;
        this.ctx = ctx;
        this.session = session;
    }

    public GameSession getSession() {
        return session;
    }

    @Override
    public void update(double dt, InputHandler input) {
        globalTime += dt;

        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE) || input.wasKeyPressed(KeyEvent.VK_P)) {
            stateManager.push(new PauseState(stateManager, ctx, session));
            return;
        }
        if (input.wasKeyPressed(KeyEvent.VK_M)) {
            ctx.soundEngine.toggleMute();
            ctx.saveManager.setMuted(ctx.soundEngine.isMuted());
        }
        if (input.wasKeyPressed(KeyEvent.VK_F11)) {
            ctx.toggleFullscreenAction.run();
        }
        if (input.wasKeyPressed(KeyEvent.VK_TAB)) {
            showFullMap = !showFullMap;
        }

        double moveX = 0.0;
        double moveY = 0.0;
        if (input.isKeyDown(KeyEvent.VK_W) || input.isKeyDown(KeyEvent.VK_UP)) moveY -= 1.0;
        if (input.isKeyDown(KeyEvent.VK_S) || input.isKeyDown(KeyEvent.VK_DOWN)) moveY += 1.0;
        if (input.isKeyDown(KeyEvent.VK_A) || input.isKeyDown(KeyEvent.VK_LEFT)) moveX -= 1.0;
        if (input.isKeyDown(KeyEvent.VK_D) || input.isKeyDown(KeyEvent.VK_RIGHT)) moveX += 1.0;

        boolean attacking = input.isMouseDown(MouseEvent.BUTTON1) || input.isKeyDown(KeyEvent.VK_J);
        boolean dashPressed = input.wasKeyPressed(KeyEvent.VK_SPACE) || input.wasKeyPressed(KeyEvent.VK_SHIFT);
        boolean qPressed = input.wasKeyPressed(KeyEvent.VK_Q) || input.wasMousePressed(MouseEvent.BUTTON3);
        boolean ePressed = input.wasKeyPressed(KeyEvent.VK_E);
        boolean interactPressed = input.wasKeyPressed(KeyEvent.VK_F);

        session.update(
                dt,
                moveX, moveY,
                input.getMouseX(), input.getMouseY(),
                attacking, dashPressed, qPressed, ePressed, interactPressed
        );

        if (session.runFinished) {
            stateManager.replaceAll(new RunEndState(stateManager, ctx, session));
            return;
        }

        if (session.getPlayer().pendingLevelUps > 0) {
            session.getPlayer().pendingLevelUps--;
            stateManager.push(new UpgradeDraftState(stateManager, ctx, session, false));
        } else if (session.pendingAltarUpgrade) {
            session.pendingAltarUpgrade = false;
            stateManager.push(new UpgradeDraftState(stateManager, ctx, session, true));
        }
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        worldRenderer.render(g, session, input.getMouseX(), input.getMouseY(), globalTime);
        hudRenderer.render(g, session, showFullMap, ctx.soundEngine.isMuted());
    }
}
