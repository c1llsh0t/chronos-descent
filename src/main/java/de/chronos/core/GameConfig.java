package de.chronos.core;

/**
 * Zentrale Konfiguration und Spielkonstanten für Engine, Weltgenerierung und Balancing.
 */
public final class GameConfig {
    private GameConfig() {
    }

    public static final String GAME_TITLE = "CHRONOS: Aethelgard's Descent";
    public static final String VERSION = "1.0.1";

    // Logische Rendering-Auflösung (skaliert verlustfrei auf jede Fenstergröße)
    public static final int LOGICAL_WIDTH = 1280;
    public static final int LOGICAL_HEIGHT = 720;

    // Fixed-Timestep Engine-Taktung
    public static final int TARGET_FPS = 60;
    public static final double FIXED_DT = 1.0 / TARGET_FPS;
    public static final double MAX_FRAME_TIME = 0.1;

    // Welt- und Dungeon-Metriken
    public static final int TILE_SIZE = 44;
    public static final int MAX_FLOORS = 3;
    public static final int BASE_MAP_COLS = 72;
    public static final int BASE_MAP_ROWS = 54;
    public static final int VISION_RADIUS_TILES = 11;

    // Spieler- und Kampffeinabstimmung
    public static final double DASH_DURATION = 0.18;
    public static final double DASH_COOLDOWN = 1.35;
    public static final double DASH_SPEED_MULTIPLIER = 3.25;
    public static final double PICKUP_MAGNET_RADIUS = 135.0;
    public static final double INTERACT_RADIUS = 66.0;
    public static final double COMBO_DECAY_SECONDS = 4.5;

    // Schwierigkeitsgrade
    public enum Difficulty {
        NORMAL(1.0, 1.0, 1.0),
        HARD(1.30, 1.15, 1.35),
        NIGHTMARE(1.65, 1.30, 1.80);

        public final double enemyHealthMultiplier;
        public final double enemySpeedMultiplier;
        public final double scoreMultiplier;

        Difficulty(double enemyHealthMultiplier, double enemySpeedMultiplier, double scoreMultiplier) {
            this.enemyHealthMultiplier = enemyHealthMultiplier;
            this.enemySpeedMultiplier = enemySpeedMultiplier;
            this.scoreMultiplier = scoreMultiplier;
        }
    }
}
