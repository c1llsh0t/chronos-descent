package de.chronos.ui;

import de.chronos.core.GameConfig;
import de.chronos.core.GameEngine;
import de.chronos.core.InputHandler;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * Hauptfenster (Swing JFrame) mit seitenverhältnistreuer Skalierung,
 * unsichtbarem System-Cursor im Canvas und F11-Vollbildmodus.
 */
public final class GameFrame extends JFrame {

    private final GameEngine engine;
    private final GameCanvas canvas;
    private boolean fullscreen = false;

    public GameFrame() {
        super(GameConfig.GAME_TITLE + "  v" + GameConfig.VERSION);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        this.engine = new GameEngine(
                () -> SwingUtilities.invokeLater(this::closeGame),
                () -> SwingUtilities.invokeLater(this::toggleFullscreen)
        );
        this.canvas = new GameCanvas(engine);
        engine.setRepaintCallback(canvas::repaint);

        setContentPane(canvas);
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(800, 480));

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                engine.stop();
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                engine.getInput().clearAllInputs();
            }
        });
    }

    public void launch() {
        setVisible(true);
        canvas.requestFocusInWindow();
        engine.start();
    }

    private void closeGame() {
        engine.stop();
        dispose();
        System.exit(0);
    }

    private void toggleFullscreen() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        fullscreen = !fullscreen;
        dispose();
        setUndecorated(fullscreen);
        if (fullscreen && gd.isFullScreenSupported()) {
            gd.setFullScreenWindow(this);
        } else {
            gd.setFullScreenWindow(null);
            setSize(GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);
            setLocationRelativeTo(null);
        }
        setVisible(true);
        canvas.requestFocusInWindow();
    }

    private static final class GameCanvas extends JPanel {
        private final GameEngine engine;

        GameCanvas(GameEngine engine) {
            this.engine = engine;
            setPreferredSize(new Dimension(GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT));
            setBackground(Color.BLACK);
            setFocusable(true);
            setFocusTraversalKeysEnabled(false); // Erlaubt [TAB] für Karten-Overlay

            InputHandler input = engine.getInput();
            addKeyListener(input);
            addMouseListener(input);
            addMouseMotionListener(input);

            // Dezenter Punkt-Cursor, da ein eigenes Fadenkreuz gezeichnet wird
            BufferedImage cursorImg = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            Cursor blank = Toolkit.getDefaultToolkit().createCustomCursor(cursorImg, new Point(0, 0), "hudCursor");
            setCursor(blank);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            int pw = getWidth();
            int ph = getHeight();
            double scaleX = (double) pw / GameConfig.LOGICAL_WIDTH;
            double scaleY = (double) ph / GameConfig.LOGICAL_HEIGHT;
            double scale = Math.min(scaleX, scaleY);

            int drawW = (int) Math.round(GameConfig.LOGICAL_WIDTH * scale);
            int drawH = (int) Math.round(GameConfig.LOGICAL_HEIGHT * scale);
            int offsetX = (pw - drawW) / 2;
            int offsetY = (ph - drawH) / 2;

            engine.getInput().setRenderTransform(offsetX, offsetY, scale);
            engine.drawFrontBuffer(g2, offsetX, offsetY, drawW, drawH);
        }
    }
}
