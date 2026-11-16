package de.chronos.core;

/**
 * Zentrale Konfiguration und Spielkonstanten für Engine, Weltgenerierung und Balancing.
 */
public final class GameConfig {
    private GameConfig() {
    }

    public static final String GAME_TITLE = "CHRONOS: Aethelgard's Descent";
    public static final String VERSION = "1.2.0";

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

    // Balancing: Spieler & Ebenen
    public static final double FLOOR_HP_SCALING = 0.55;      // +55% Gegner-Leben pro Ebene
    public static final double FLOOR_DAMAGE_SCALING = 0.30;  // +30% Gegner-Schaden pro Ebene
    public static final double PLAYER_HIT_INVULN = 0.30;
    public static final double LEVEL_UP_HEAL = 5.0;
    public static final double SHIELD_REGEN = 2.0;
    public static final double SPIKE_DAMAGE = 20.0;
    public static final double POTION_DROP_CHANCE = 0.07;

    // Schwierigkeitsgrade: Leben, Tempo, Punkte, Gegner-Schaden, Angriffstempo, Zusatzgegner pro Raum
    public enum Difficulty {
        NORMAL(1.5, 1.12, 1.0, 1.8, 1.35, 2),
        HARD(2.0, 1.25, 1.6, 2.4, 1.6, 3),
        NIGHTMARE(2.7, 1.4, 2.5, 3.2, 1.9, 4);

        public final double enemyHealthMultiplier;
        public final double enemySpeedMultiplier;
        public final double scoreMultiplier;
        public final double enemyDamageMultiplier;
        public final double enemyAttackRate;
        public final int extraEnemiesPerRoom;

        Difficulty(double enemyHealthMultiplier, double enemySpeedMultiplier, double scoreMultiplier,
                   double enemyDamageMultiplier, double enemyAttackRate, int extraEnemiesPerRoom) {
            this.enemyHealthMultiplier = enemyHealthMultiplier;
            this.enemySpeedMultiplier = enemySpeedMultiplier;
            this.scoreMultiplier = scoreMultiplier;
            this.enemyDamageMultiplier = enemyDamageMultiplier;
            this.enemyAttackRate = enemyAttackRate;
            this.extraEnemiesPerRoom = extraEnemiesPerRoom;
        }
    }
}
