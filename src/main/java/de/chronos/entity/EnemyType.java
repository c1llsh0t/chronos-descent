package de.chronos.entity;

import java.awt.Color;

/**
 * Gegnertypen inkl. Basiswerten, XP/Score-Belohnung und visueller Farbgebung.
 */
public enum EnemyType {
    SLIME(36, 108, 12, 14, 14, 15, new Color(86, 224, 118), false),
    ELDER_SLIME(76, 86, 17, 20, 24, 35, new Color(46, 185, 140), false),
    SKELETON_ARCHER(44, 105, 14, 14, 22, 28, new Color(224, 218, 195), false),
    BERSERKER(88, 96, 22, 18, 30, 42, new Color(235, 88, 68), false),
    NECROMANCER(68, 84, 16, 16, 36, 55, new Color(178, 84, 255), false),
    BOSS_MALAKOR(1100, 102, 24, 28, 150, 500, new Color(255, 58, 98), true);

    public final double baseHp;
    public final double baseSpeed;
    public final double contactDamage;
    public final double radius;
    public final int xpReward;
    public final int scoreReward;
    public final Color color;
    public final boolean isBoss;

    EnemyType(
            double baseHp,
            double baseSpeed,
            double contactDamage,
            double radius,
            int xpReward,
            int scoreReward,
            Color color,
            boolean isBoss
    ) {
        this.baseHp = baseHp;
        this.baseSpeed = baseSpeed;
        this.contactDamage = contactDamage;
        this.radius = radius;
        this.xpReward = xpReward;
        this.scoreReward = scoreReward;
        this.color = color;
        this.isBoss = isBoss;
    }
}
