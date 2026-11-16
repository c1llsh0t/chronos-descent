package de.chronos.ui.state;

import de.chronos.core.InputHandler;

import java.awt.Graphics2D;

/**
 * State-Pattern-Interface für alle Spielbildschirme und Overlays.
 */
public interface GameState {
    default void onEnter() {}
    void update(double dt, InputHandler input);
    void render(Graphics2D g, InputHandler input);
    default void onExit() {}
}
