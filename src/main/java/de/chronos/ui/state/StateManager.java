package de.chronos.ui.state;

import de.chronos.core.InputHandler;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Stapelbasierte Zustandsmaschine (State Machine), die Overlays über laufenden Spielen unterstützt.
 */
public final class StateManager {
    private final List<GameState> stack = new ArrayList<>();

    public void push(GameState state) {
        stack.add(state);
        state.onEnter();
    }

    public void pop() {
        if (!stack.isEmpty()) {
            GameState top = stack.remove(stack.size() - 1);
            top.onExit();
        }
    }

    public void replaceAll(GameState state) {
        while (!stack.isEmpty()) {
            GameState top = stack.remove(stack.size() - 1);
            top.onExit();
        }
        push(state);
    }

    public GameState peek() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    public GameState getBelowTop() {
        return stack.size() >= 2 ? stack.get(stack.size() - 2) : null;
    }

    public void update(double dt, InputHandler input) {
        GameState top = peek();
        if (top != null) {
            top.update(dt, input);
        }
    }

    public void render(Graphics2D g, InputHandler input) {
        GameState top = peek();
        if (top != null) {
            top.render(g, input);
        }
    }
}
