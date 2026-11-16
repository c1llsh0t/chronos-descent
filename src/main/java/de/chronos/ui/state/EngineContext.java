package de.chronos.ui.state;

import de.chronos.audio.SoundEngine;
import de.chronos.core.SaveManager;
import de.chronos.core.event.GameEventBus;

/**
 * Gemeinsamer Kontext für alle GameStates (Dependency Container).
 */
public final class EngineContext {
    public final GameEventBus eventBus;
    public final SaveManager saveManager;
    public final SoundEngine soundEngine;
    public final Runnable exitAction;
    public final Runnable toggleFullscreenAction;

    public EngineContext(
            GameEventBus eventBus,
            SaveManager saveManager,
            SoundEngine soundEngine,
            Runnable exitAction,
            Runnable toggleFullscreenAction
    ) {
        this.eventBus = eventBus;
        this.saveManager = saveManager;
        this.soundEngine = soundEngine;
        this.exitAction = exitAction;
        this.toggleFullscreenAction = toggleFullscreenAction;
    }
}
