package de.chronos.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Zweisprachiges Lokalisierungssystem (Deutsch / Englisch), umschaltbar zur Laufzeit.
 */
public final class I18n {
    public enum Language {
        DE("Deutsch"),
        EN("English");

        public final String label;

        Language(String label) {
            this.label = label;
        }
    }

    private static Language current = Language.DE;
    private static final Map<String, String> DE_MAP = new HashMap<>();
    private static final Map<String, String> EN_MAP = new HashMap<>();

    static {
        // Menü & Allgemein
        reg("subtitle", "Prozeduraler Action-Roguelite Dungeon Crawler", "Procedural Action-Roguelite Dungeon Crawler");
        reg("menu.start", "Abenteuer starten", "Start Descent");
        reg("menu.highscores", "Bestenliste & Erfolge", "Highscores & Achievements");
        reg("menu.help", "Steuerung & Technik", "Controls & Tech Info");
        reg("menu.quit", "Beenden", "Quit");
        reg("menu.select_class", "Heldenklasse wählen", "Choose Hero Class");
        reg("menu.difficulty", "Schwierigkeit", "Difficulty");
        reg("menu.seed", "Dungeon-Seed (leer = zufällig)", "Dungeon Seed (blank = random)");
        reg("menu.seed_hint", "Tippen für eigenen Seed...", "Type custom seed...");
        reg("menu.language", "Sprache [L]: DE", "Language [L]: EN");
        reg("menu.hint", "ENTER / Klick: Starten   |   1-3: Klasse   |   L: Sprache   |   M: Ton",
                "ENTER / Click: Start   |   1-3: Class   |   L: Language   |   M: Audio");

        // Schwierigkeit
        reg("diff.NORMAL", "Normal", "Normal");
        reg("diff.HARD", "Schwer", "Hard");
        reg("diff.NIGHTMARE", "Alptraum", "Nightmare");

        // Heldenklassen
        reg("class.VANGUARD.name", "Klingenwächter", "Vanguard");
        reg("class.VANGUARD.role", "Nahkampf-Tank & Schildbrecher", "Melee Bruiser & Shieldbreaker");
        reg("class.VANGUARD.desc", "Breiter Schwertbogen, Schild-Dash blockt Geschosse, Wirbelnova [Q], Unbezwingbare Ägis [E].",
                "Wide blade cleave, shield dash blocks projectiles, Whirlwind Nova [Q], Immortal Aegis [E].");

        reg("class.ARCANIST.name", "Arkanmagier", "Arcanist");
        reg("class.ARCANIST.role", "Elementarmagie & Flächenschaden", "Arcane Burst & Area Control");
        reg("class.ARCANIST.desc", "Zielsuchende Arkanbolzen, Arkan-Dash, Frostnova [Q], Meteorsturm [E].",
                "Homing arcane bolts, arcane dash, Frost Nova [Q], Meteor Storm [E].");

        reg("class.SHADOWBLADE.name", "Schattenjäger", "Shadowblade");
        reg("class.SHADOWBLADE.role", "Hohes Tempo & Kritische Treffer", "High Mobility & Critical Strikes");
        reg("class.SHADOWBLADE.desc", "Giftige Wurfdolche, explosiver Schatten-Dash, Klingenfächer [Q], Schattenrausch [E].",
                "Poisoned throwing daggers, exploding shadow dash, Fan of Knives [Q], Shadow Frenzy [E].");

        // HUD & Gameplay
        reg("hud.floor", "Ebene", "Floor");
        reg("hud.level", "Stufe", "Lv.");
        reg("hud.gold", "Gold", "Gold");
        reg("hud.score", "Punkte", "Score");
        reg("hud.combo", "Kombo", "Combo");
        reg("hud.enemies_left", "Gegner übrig", "Enemies left");
        reg("hud.boss_name", "Malakor, der Chronos-Wächter", "Malakor, Warden of Chronos");
        reg("hud.boss_phase2", "PHASE II: CHRONOS-ÜBERLADUNG", "PHASE II: CHRONOS OVERLOAD");
        reg("hud.interact_chest", "[F] Schatztruhe öffnen", "[F] Open Treasure Chest");
        reg("hud.interact_shrine", "[F] Heilschrein nutzen (25 Gold -> +45 HP)", "[F] Use Healing Shrine (25 Gold -> +45 HP)");
        reg("hud.interact_altar", "[F] Relikt-Altar aktivieren (40 Gold -> Upgrade)", "[F] Activate Relic Altar (40 Gold -> Upgrade)");
        reg("hud.interact_portal", "[F] Portal zur nächsten Ebene betreten", "[F] Enter Portal to Next Floor");
        reg("hud.not_enough_gold", "Nicht genug Gold (%d benötigt, %d vorhanden)", "Not enough gold (%d needed, you have %d)");
        reg("intro.developed_by", "Entwickelt durch", "Developed by");
        reg("intro.published_by", "Veröffentlicht durch", "Published by");
        reg("intro.skip", "Beliebige Taste oder Klick zum Überspringen", "Press any key or click to skip");
        reg("hud.portal_locked", "Besiege den Wächter/alle Gegner im Zielraum!", "Defeat the Guardian / Room enemies first!");
        reg("hud.muted", "TON AUS [M]", "MUTED [M]");
        reg("hud.seed", "Seed", "Seed");
        reg("hud.upgrades", "Relikte", "Relics");
        reg("hud.achievement", "ERFOLG FREIGESCHALTET", "ACHIEVEMENT UNLOCKED");

        // Level-Up Draft
        reg("draft.title", "STUFENAUFSTIEG! WÄHLE EINE VERBESSERUNG", "LEVEL UP! CHOOSE AN UPGRADE");
        reg("draft.altar_title", "URALTER ALTAR: WÄHLE EIN RELIKT", "ANCIENT ALTAR: CHOOSE A RELIC");
        reg("draft.hint", "Klicke auf eine Karte oder drücke [1], [2], [3]", "Click a card or press [1], [2], [3]");

        // Pause
        reg("pause.title", "SPIEL PAUSIERT", "GAME PAUSED");
        reg("pause.resume", "Fortsetzen [ESC]", "Resume [ESC]");
        reg("pause.restart", "Neuer Run (gleiche Klasse)", "New Run (Same Class)");
        reg("pause.menu", "Zurück zum Hauptmenü", "Return to Main Menu");
        reg("pause.stats", "Aktuelle Run-Statistik", "Current Run Telemetry");

        // Run End
        reg("end.victory", "SIEG! DER CHRONOS-WÄCHTER IST GEFALLEN!", "VICTORY! THE WARDEN OF CHRONOS FELL!");
        reg("end.gameover", "NIEDERLAGE IM LABYRINTH", "FALLEN IN THE LABYRINTH");
        reg("end.play_again", "Erneut spielen", "Play Again");
        reg("end.menu", "Hauptmenü", "Main Menu");
        reg("end.duration", "Zeit", "Duration");
        reg("end.kills", "Besiegte Gegner", "Enemies Slain");
        reg("end.damage", "Verursachter Schaden", "Damage Dealt");
        reg("end.max_combo", "Höchste Kombo", "Peak Combo");
        reg("end.floor_reached", "Erreichte Ebene", "Floor Reached");

        // Highscores & Achievements
        reg("hs.title", "BESTENLISTE & ERFOLGE", "HALL OF FAME & ACHIEVEMENTS");
        reg("hs.empty", "Noch keine abgeschlossenen Runs gespeichert.", "No completed runs recorded yet.");
        reg("hs.achievements", "Erfolge", "Achievements");
        reg("hs.back", "Zurück [ESC]", "Back [ESC]");

        // Hilfe
        reg("help.title", "STEUERUNG, KAMPFSYSTEM & INFORMATIK-ARCHITEKTUR", "CONTROLS, COMBAT & CS ARCHITECTURE");
        reg("help.back", "Zurück [ESC]", "Back [ESC]");

        // Raritäten
        reg("rarity.COMMON", "Gewöhnlich", "Common");
        reg("rarity.RARE", "Selten", "Rare");
        reg("rarity.EPIC", "Episch", "Epic");
        reg("rarity.LEGENDARY", "Legendär", "Legendary");
    }

    private static void reg(String key, String de, String en) {
        DE_MAP.put(key, de);
        EN_MAP.put(key, en);
    }

    public static Language getLanguage() {
        return current;
    }

    public static void setLanguage(Language lang) {
        if (lang != null) {
            current = lang;
        }
    }

    public static void toggleLanguage() {
        current = (current == Language.DE) ? Language.EN : Language.DE;
    }

    public static String t(String key) {
        Map<String, String> map = (current == Language.DE) ? DE_MAP : EN_MAP;
        return map.getOrDefault(key, key);
    }
}
