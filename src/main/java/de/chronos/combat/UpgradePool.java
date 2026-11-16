package de.chronos.combat;

import de.chronos.math.SeededRandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Katalog und gewichteter Zufalls-Generator für Roguelite-Upgrades.
 */
public final class UpgradePool {

    private static final List<Upgrade> ALL_UPGRADES = List.of(
            new Upgrade("POWER_STRIKE", Upgrade.Rarity.COMMON,
                    "Klingenstärke", "Sharpened Edge",
                    "+20% Grundschaden auf alle Angriffe.", "+20% base damage on all attacks."),
            new Upgrade("SWIFT_STEP", Upgrade.Rarity.COMMON,
                    "Windstiefel", "Zephyr Boots",
                    "+12% Bewegungstempo & -15% Dash-Abklingzeit.", "+12% move speed & -15% dash cooldown."),
            new Upgrade("VITAL_HEART", Upgrade.Rarity.COMMON,
                    "Titanenherz", "Titanic Heart",
                    "+30 Maximale HP und sofort +30 HP Heilung.", "+30 Max HP and heal +30 HP immediately."),
            new Upgrade("RAPID_HANDS", Upgrade.Rarity.COMMON,
                    "Flinke Reflexe", "Rapid Reflexes",
                    "+18% Angriffsgeschwindigkeit.", "+18% attack speed."),
            new Upgrade("AEGIS_WARD", Upgrade.Rarity.COMMON,
                    "Ägis-Barriere", "Aegis Barrier",
                    "+25 Maximaler Schild & schnellere Regeneration.", "+25 Max Shield & faster shield recharge."),
            new Upgrade("CRIT_FOCUS", Upgrade.Rarity.RARE,
                    "Scharfer Fokus", "Keen Precision",
                    "+12% Kritische Trefferchance & +25% Krit-Schaden.", "+12% Crit Chance & +25% Crit Damage."),
            new Upgrade("VAMPIRIC", Upgrade.Rarity.RARE,
                    "Blutpakt", "Vampiric Pact",
                    "Heilt +4 HP bei jedem besiegten Gegner.", "Heal +4 HP on every enemy kill."),
            new Upgrade("CHRONOS_HASTE", Upgrade.Rarity.RARE,
                    "Chronos-Echo", "Chronos Echo",
                    "-22% Abklingzeit für [Q] und [E] Fähigkeiten.", "-22% cooldown for [Q] and [E] skills."),
            new Upgrade("MULTISHOT", Upgrade.Rarity.EPIC,
                    "Doppelschlag", "Twin Echo",
                    "+1 zusätzliches Projektil bzw. +25% Nahkampf-Reichweite.", "+1 extra projectile or +25% melee reach."),
            new Upgrade("CHAIN_LIGHTNING", Upgrade.Rarity.EPIC,
                    "Kettenblitz", "Chain Lightning",
                    "Treffer springen mit Blitzschaden auf nahe Gegner über.", "Hits arc lightning damage to nearby foes."),
            new Upgrade("FROST_AURA", Upgrade.Rarity.RARE,
                    "Frostkranz", "Frostbite Aura",
                    "Verlangsamt und verletzt Gegner in unmittelbarer Nähe.", "Slows and damages enemies close to you."),
            new Upgrade("RICOCHET", Upgrade.Rarity.RARE,
                    "Querschläger", "Ricochet Rune",
                    "Projektile prallen von Wänden ab und durchschlagen +1 Ziel.", "Projectiles bounce off walls and pierce +1 foe."),
            new Upgrade("EXPLOSIVE_IMPACT", Upgrade.Rarity.EPIC,
                    "Arkane Detonation", "Arcane Detonation",
                    "Kritische Treffer erzeugen eine Explosionswelle.", "Critical hits trigger an AoE blast wave."),
            new Upgrade("EXECUTIONER", Upgrade.Rarity.RARE,
                    "Henkersmal", "Executioner's Mark",
                    "+40% Schaden gegen Gegner unter 40% HP.", "+40% damage against enemies below 40% HP."),
            new Upgrade("CHRONO_OVERDRIVE", Upgrade.Rarity.LEGENDARY,
                    "Zeitüberladung", "Chrono Overdrive",
                    "+30% Schaden, +20% Tempo und +15% Krit-Chance!", "+30% damage, +20% speed and +15% crit chance!"),
            new Upgrade("PHOENIX_SOUL", Upgrade.Rarity.LEGENDARY,
                    "Phönixfunke", "Phoenix Spark",
                    "Entzündet alle Angriffe, +40 Max HP & volle Heilung.", "Ignites all attacks, +40 Max HP & full heal.")
    );

    private UpgradePool() {
    }

    public static List<Upgrade> getAll() {
        return ALL_UPGRADES;
    }

    public static List<Upgrade> rollChoices(SeededRandom rng, int count) {
        List<Upgrade> pool = new ArrayList<>(ALL_UPGRADES);
        List<Upgrade> picked = new ArrayList<>();
        while (picked.size() < count && !pool.isEmpty()) {
            int totalWeight = 0;
            for (Upgrade u : pool) {
                totalWeight += u.rarity.weight;
            }
            int roll = rng.nextInt(totalWeight);
            int acc = 0;
            int chosenIdx = 0;
            for (int i = 0; i < pool.size(); i++) {
                acc += pool.get(i).rarity.weight;
                if (roll < acc) {
                    chosenIdx = i;
                    break;
                }
            }
            picked.add(pool.remove(chosenIdx));
        }
        return Collections.unmodifiableList(picked);
    }
}
