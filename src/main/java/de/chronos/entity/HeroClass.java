package de.chronos.entity;

import de.chronos.core.I18n;

import java.awt.Color;

/**
 * Spielbare Heldenklassen mit unterschiedlichen Attributen und Fähigkeiten.
 */
public enum HeroClass {
    VANGUARD(
            135.0, 210.0, 26.0, 0.34, 0.14, 30.0,
            4.5, 11.0,
            new Color(72, 168, 255), new Color(190, 232, 255)
    ),
    ARCANIST(
            95.0, 222.0, 21.0, 0.30, 0.16, 10.0,
            4.0, 12.0,
            new Color(174, 96, 255), new Color(102, 240, 255)
    ),
    SHADOWBLADE(
            105.0, 252.0, 16.0, 0.19, 0.28, 0.0,
            3.5, 10.0,
            new Color(58, 228, 168), new Color(210, 255, 120)
    );

    public final double baseHp;
    public final double baseSpeed;
    public final double baseDamage;
    public final double attackCooldown;
    public final double baseCritChance;
    public final double baseMaxShield;
    public final double skillQCooldown;
    public final double skillECooldown;
    public final Color primaryColor;
    public final Color accentColor;

    HeroClass(
            double baseHp,
            double baseSpeed,
            double baseDamage,
            double attackCooldown,
            double baseCritChance,
            double baseMaxShield,
            double skillQCooldown,
            double skillECooldown,
            Color primaryColor,
            Color accentColor
    ) {
        this.baseHp = baseHp;
        this.baseSpeed = baseSpeed;
        this.baseDamage = baseDamage;
        this.attackCooldown = attackCooldown;
        this.baseCritChance = baseCritChance;
        this.baseMaxShield = baseMaxShield;
        this.skillQCooldown = skillQCooldown;
        this.skillECooldown = skillECooldown;
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
    }

    public String getDisplayName() {
        return I18n.t("class." + name() + ".name");
    }

    public String getRole() {
        return I18n.t("class." + name() + ".role");
    }

    public String getDescription() {
        return I18n.t("class." + name() + ".desc");
    }
}
