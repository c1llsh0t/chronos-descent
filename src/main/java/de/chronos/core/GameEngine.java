package de.chronos.core;

import de.chronos.audio.SoundEngine;
import de.chronos.core.event.GameEventBus;
import de.chronos.ui.state.EngineContext;
import de.chronos.ui.state.GameState;
import de.chronos.ui.state.IntroState;
import de.chronos.ui.state.MenuState;
import de.chronos.ui.state.PlayingState;
import de.chronos.ui.state.StateManager;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Kern-Engine mit Fixed-Timestep Game Loop (60 FPS), Double-Buffering
 * und Synchronisation zwischen Update-Thread und Swing-EDT.
 */
public final class GameEngine implements Runnable {

    private final InputHandler input;
    private final GameEventBus eventBus;
    private final SaveManager saveManager;
    private final SoundEngine soundEngine;
    private final StateManager stateManager;

    private final BufferedImage backBuffer;
    private final BufferedImage frontBuffer;
    private final Object bufferLock = new Object();

    private volatile boolean running = false;
    private Thread loopThread;
    private Runnable repaintCallback;

    public GameEngine(Runnable exitAction, Runnable toggleFullscreenAction) {
        this.input = new InputHandler();
        this.eventBus = new GameEventBus();
        this.saveManager = new SaveManager();
        I18n.setLanguage(saveManager.getLanguage());
        this.soundEngine = new SoundEngine(saveManager.isMuted());
        this.eventBus.subscribe(this.soundEngine);

        this.stateManager = new StateManager();
        EngineContext ctx = new EngineContext(
                eventBus,
                saveManager,
                soundEngine,
                exitAction != null ? exitAction : this::stop,
                toggleFullscreenAction != null ? toggleFullscreenAction : () -> {}
        );
        this.stateManager.push(new MenuState(stateManager, ctx));
        // Intro liegt über dem Menü und entfernt sich nach Ablauf (oder Tastendruck) selbst
        this.stateManager.push(new IntroState(stateManager));

        this.backBuffer = new BufferedImage(GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        this.frontBuffer = new BufferedImage(GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    public InputHandler getInput() {
        return input;
    }

    public StateManager getStateManager() {
        return stateManager;
    }

    public GameEventBus getEventBus() {
        return eventBus;
    }

    public SaveManager getSaveManager() {
        return saveManager;
    }

    public SoundEngine getSoundEngine() {
        return soundEngine;
    }

    public void setRepaintCallback(Runnable repaintCallback) {
        this.repaintCallback = repaintCallback;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        loopThread = new Thread(this, "Chronos-GameLoop");
        loopThread.start();
    }

    public void stop() {
        running = false;
        soundEngine.shutdown();
    }

    /**
     * Führt einen einzelnen deterministischen Frame-Schritt aus (auch für Headless-Tests).
     */
    public void stepAndRenderFrame(double dt) {
        input.beginFrame();
        stateManager.update(dt, input);

        Graphics2D g = backBuffer.createGraphics();
        try {
            renderCurrentFrame(g);
        } finally {
            g.dispose();
        }

        synchronized (bufferLock) {
            Graphics2D fg = frontBuffer.createGraphics();
            try {
                fg.drawImage(backBuffer, 0, 0, null);
            } finally {
                fg.dispose();
            }
        }
    }

    public void drawFrontBuffer(Graphics2D target, int dx, int dy, int dw, int dh) {
        synchronized (bufferLock) {
            target.drawImage(frontBuffer, dx, dy, dw, dh, null);
        }
    }

    public BufferedImage getFrontBufferSnapshot() {
        synchronized (bufferLock) {
            BufferedImage copy = new BufferedImage(GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = copy.createGraphics();
            g.drawImage(frontBuffer, 0, 0, null);
            g.dispose();
            return copy;
        }
    }

    @Override
    public void run() {
        long lastTime = System.nanoTime();
        double accumulator = 0.0;

        while (running) {
            long now = System.nanoTime();
            double frameTime = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;
            if (frameTime > GameConfig.MAX_FRAME_TIME) {
                frameTime = GameConfig.MAX_FRAME_TIME;
            }
            accumulator += frameTime;

            boolean updated = false;
            while (accumulator >= GameConfig.FIXED_DT) {
                input.beginFrame();
                stateManager.update(GameConfig.FIXED_DT, input);
                accumulator -= GameConfig.FIXED_DT;
                updated = true;
            }

            if (updated) {
                Graphics2D g = backBuffer.createGraphics();
                try {
                    renderCurrentFrame(g);
                } finally {
                    g.dispose();
                }

                synchronized (bufferLock) {
                    Graphics2D fg = frontBuffer.createGraphics();
                    try {
                        fg.drawImage(backBuffer, 0, 0, null);
                    } finally {
                        fg.dispose();
                    }
                }

                if (repaintCallback != null) {
                    repaintCallback.run();
                }
            }

            try {
                Thread.sleep(2);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void renderCurrentFrame(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        stateManager.render(g, input);

        // Eigenen UI-Mauszeiger zeichnen, wenn kein aktives Gameplay-Fadenkreuz aktiv ist
        GameState top = stateManager.peek();
        if (!(top instanceof PlayingState) && !(top instanceof IntroState)) {
            int mx = (int) Math.round(input.getMouseX());
            int my = (int) Math.round(input.getMouseY());
            Polygon pointer = new Polygon(
                    new int[]{mx, mx + 14, mx + 6, mx + 3},
                    new int[]{my, my + 10, my + 11, my + 17},
                    4
            );
            g.setColor(new Color(115, 225, 255));
            g.fillPolygon(pointer);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1.4f));
            g.drawPolygon(pointer);
        }
    }
}
