package de.chronos.combat;

import de.chronos.core.I18n;

import java.util.List;

/**
 * Definition aller freischaltbaren Erfolge (Achievements).
 */
public final class Achievement {
    public final String id;
    private final String titleDe;
    private final String titleEn;
    private final String descDe;
    private final String descEn;

    private Achievement(String id, String titleDe, String titleEn, String descDe, String descEn) {
        this.id = id;
        this.titleDe = titleDe;
        this.titleEn = titleEn;
        this.descDe = descDe;
        this.descEn = descEn;
    }

    public String getTitle() {
        return I18n.getLanguage() == I18n.Language.DE ? titleDe : titleEn;
    }

    public String getDescription() {
        return I18n.getLanguage() == I18n.Language.DE ? descDe : descEn;
    }

    public static final List<Achievement> ALL = List.of(
            new Achievement("FIRST_BLOOD", "Erstes Blut", "First Blood",
                    "Besiege deinen ersten Gegner im Labyrinth.", "Defeat your first enemy in the labyrinth."),
            new Achievement("COMBO_10", "Kampfrausch", "Battle Flow",
                    "Erreiche eine 10er-Kombo.", "Reach a 10x kill combo."),
            new Achievement("LEVEL_5", "Meisteranwärter", "Rising Master",
                    "Erreiche Heldenstufe 5 in einem Run.", "Reach hero level 5 in a single run."),
            new Achievement("TREASURE_HUNTER", "Schatzsucher", "Treasure Hunter",
                    "Öffne 3 Schatztruhen in einem Run.", "Open 3 treasure chests in one run."),
            new Achievement("DEEP_DELVER", "Tiefengänger", "Deep Delver",
                    "Erreiche Ebene 3 des Chronos-Gewölbes.", "Reach Floor 3 of the Chronos Vault."),
            new Achievement("BOSS_SLAYER", "Chronos-Bezwinger", "Warden Slayer",
                    "Besiege Malakor, den Chronos-Wächter.", "Defeat Malakor, Warden of Chronos."),
            new Achievement("SURVIVOR_HARD", "Eiserner Wille", "Iron Will",
                    "Gewinne einen Run auf Schwer oder Alptraum.", "Win a run on Hard or Nightmare difficulty."),
            new Achievement("SPEEDRUNNER", "Blitzlauf", "Chrono Blitz",
                    "Gewinne einen Run in unter 6 Minuten.", "Win a run in under 6 minutes.")
    );

    public static Achievement findById(String id) {
        for (Achievement a : ALL) {
            if (a.id.equals(id)) {
                return a;
            }
        }
        return null;
    }
}
