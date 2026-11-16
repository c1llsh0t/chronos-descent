package de.chronos.core;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Thread-sichere Eingabeverwaltung für Tastatur und Maus.
 * Rechnet physische Panel-Koordinaten auf die logische 1280x720-Auflösung um
 * und trennt kontinuierliche Abfragen (isKeyDown) von Einzelklicks (wasKeyPressed).
 */
public final class InputHandler implements KeyListener, MouseListener, MouseMotionListener {

    private static final int MAX_KEYS = 512;
    private final boolean[] keysDown = new boolean[MAX_KEYS];
    private final boolean[] keysPressed = new boolean[MAX_KEYS];
    private final boolean[] keysPressedBuffer = new boolean[MAX_KEYS];

    private final boolean[] mouseDown = new boolean[8];
    private final boolean[] mousePressed = new boolean[8];
    private final boolean[] mousePressedBuffer = new boolean[8];

    private final Deque<Character> typedChars = new ArrayDeque<>();

    private volatile double mouseX = GameConfig.LOGICAL_WIDTH / 2.0;
    private volatile double mouseY = GameConfig.LOGICAL_HEIGHT / 2.0;

    // Transformationsparameter des Panels für Letterboxing / Skalierung
    private volatile double renderOffsetX = 0.0;
    private volatile double renderOffsetY = 0.0;
    private volatile double renderScale = 1.0;

    public synchronized void beginFrame() {
        System.arraycopy(keysPressedBuffer, 0, keysPressed, 0, MAX_KEYS);
        java.util.Arrays.fill(keysPressedBuffer, false);

        System.arraycopy(mousePressedBuffer, 0, mousePressed, 0, mousePressed.length);
        java.util.Arrays.fill(mousePressedBuffer, false);
    }

    public void setRenderTransform(double offsetX, double offsetY, double scale) {
        this.renderOffsetX = offsetX;
        this.renderOffsetY = offsetY;
        this.renderScale = Math.max(0.01, scale);
    }

    public synchronized boolean isKeyDown(int keyCode) {
        return keyCode >= 0 && keyCode < MAX_KEYS && keysDown[keyCode];
    }

    public synchronized boolean wasKeyPressed(int keyCode) {
        return keyCode >= 0 && keyCode < MAX_KEYS && keysPressed[keyCode];
    }

    /** True, wenn in diesem Frame irgendeine Taste oder Maustaste neu gedrückt wurde. */
    public synchronized boolean wasAnyInputPressed() {
        for (boolean b : keysPressed) {
            if (b) return true;
        }
        for (boolean b : mousePressed) {
            if (b) return true;
        }
        return false;
    }

    public synchronized boolean isMouseDown(int button) {
        return button >= 0 && button < mouseDown.length && mouseDown[button];
    }

    public synchronized boolean wasMousePressed(int button) {
        return button >= 0 && button < mousePressed.length && mousePressed[button];
    }

    public synchronized Character pollTypedChar() {
        return typedChars.pollFirst();
    }

    public synchronized void clearTypedChars() {
        typedChars.clear();
    }

    public synchronized void clearAllInputs() {
        java.util.Arrays.fill(keysDown, false);
        java.util.Arrays.fill(keysPressed, false);
        java.util.Arrays.fill(keysPressedBuffer, false);
        java.util.Arrays.fill(mouseDown, false);
        java.util.Arrays.fill(mousePressed, false);
        java.util.Arrays.fill(mousePressedBuffer, false);
        typedChars.clear();
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    private void updateMousePos(MouseEvent e) {
        double lx = (e.getX() - renderOffsetX) / renderScale;
        double ly = (e.getY() - renderOffsetY) / renderScale;
        mouseX = Math.max(0.0, Math.min(GameConfig.LOGICAL_WIDTH, lx));
        mouseY = Math.max(0.0, Math.min(GameConfig.LOGICAL_HEIGHT, ly));
    }

    @Override
    public synchronized void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < MAX_KEYS) {
            if (!keysDown[code]) {
                keysPressedBuffer[code] = true;
            }
            keysDown[code] = true;
        }
    }

    @Override
    public synchronized void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code >= 0 && code < MAX_KEYS) {
            keysDown[code] = false;
        }
    }

    @Override
    public synchronized void keyTyped(KeyEvent e) {
        char ch = e.getKeyChar();
        if (ch != KeyEvent.CHAR_UNDEFINED) {
            if (typedChars.size() < 32) {
                typedChars.addLast(ch);
            }
        }
    }

    @Override
    public synchronized void mousePressed(MouseEvent e) {
        updateMousePos(e);
        int btn = e.getButton();
        if (btn >= 0 && btn < mouseDown.length) {
            if (!mouseDown[btn]) {
                mousePressedBuffer[btn] = true;
            }
            mouseDown[btn] = true;
        }
    }

    @Override
    public synchronized void mouseReleased(MouseEvent e) {
        updateMousePos(e);
        int btn = e.getButton();
        if (btn >= 0 && btn < mouseDown.length) {
            mouseDown[btn] = false;
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updateMousePos(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateMousePos(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {}

    @Override
    public void mouseEntered(MouseEvent e) {}

    @Override
    public void mouseExited(MouseEvent e) {}
}
