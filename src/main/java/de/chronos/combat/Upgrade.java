package de.chronos.combat;

import de.chronos.core.I18n;

import java.awt.Color;

/**
 * Repräsentiert ein stapelbares Roguelite-Upgrade / Relikt für den Spieler.
 */
public final class Upgrade {

    public enum Rarity {
        COMMON(new Color(176, 196, 222), 55),
        RARE(new Color(86, 180, 255), 30),
        EPIC(new Color(188, 108, 255), 12),
        LEGENDARY(new Color(255, 196, 62), 5);

        public final Color color;
        public final int weight;

        Rarity(Color color, int weight) {
            this.color = color;
            this.weight = weight;
        }
    }

    public final String id;
    public final Rarity rarity;
    private final String nameDe;
    private final String nameEn;
    private final String descDe;
    private final String descEn;

    public Upgrade(String id, Rarity rarity, String nameDe, String nameEn, String descDe, String descEn) {
        this.id = id;
        this.rarity = rarity;
        this.nameDe = nameDe;
        this.nameEn = nameEn;
        this.descDe = descDe;
        this.descEn = descEn;
    }

    public String getName() {
        return I18n.getLanguage() == I18n.Language.DE ? nameDe : nameEn;
    }

    public String getDescription() {
        return I18n.getLanguage() == I18n.Language.DE ? descDe : descEn;
    }
}
