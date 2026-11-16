package de.chronos.core.event;

/**
 * Observer-Schnittstelle für Ereigniskonsumenten.
 */
@FunctionalInterface
public interface GameEventListener {
    void onEvent(GameEvent event);
}
