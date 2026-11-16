package de.chronos.core.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zentraler Event-Bus (Observer Pattern), über den Subsysteme lose gekoppelt kommunizieren.
 */
public final class GameEventBus {
    private final List<GameEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(GameEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void unsubscribe(GameEventListener listener) {
        listeners.remove(listener);
    }

    public void publish(GameEvent event) {
        if (event == null) {
            return;
        }
        for (GameEventListener listener : listeners) {
            listener.onEvent(event);
        }
    }
}
