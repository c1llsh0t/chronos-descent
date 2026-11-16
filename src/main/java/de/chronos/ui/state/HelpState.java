package de.chronos.ui.state;

import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;
import de.chronos.core.event.GameEvent;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;

/**
 * Informationsbildschirm zu Steuerung, Spielmechaniken und Informatik-Architektur.
 */
public final class HelpState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;
    private final Rectangle backBtn = new Rectangle(510, 642, 260, 44);

    public HelpState(StateManager stateManager, EngineContext ctx) {
        this.stateManager = stateManager;
        this.ctx = ctx;
    }

    @Override
    public void update(double dt, InputHandler input) {
        if (input.wasKeyPressed(KeyEvent.VK_ESCAPE) || UiHelper.isClicked(backBtn, input)) {
            ctx.eventBus.publish(new GameEvent.UiClick());
            stateManager.pop();
        }
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        g.setPaint(new GradientPaint(0, 0, new Color(11, 13, 24), 0, GameConfig.LOGICAL_HEIGHT, new Color(18, 16, 32)));
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        UiHelper.drawCenteredString(g, I18n.t("help.title"), GameConfig.LOGICAL_WIDTH / 2, 58,
                new Font("SansSerif", Font.BOLD, 26), new Color(120, 218, 255));

        boolean de = I18n.getLanguage() == I18n.Language.DE;

        // Linker Block: Steuerung
        drawPanel(g, 60, 95, 560, 520, de ? "Steuerung & Spielablauf" : "Controls & Gameplay Loop", de ? new String[]{
                "W / A / S / D oder Pfeiltasten : Bewegung",
                "Maus bewegen                    : Zielen",
                "Linksklick oder [J]             : Primärangriff (gedrückt halten)",
                "Leertaste oder Shift            : Dash (Unverwundbarkeit & Trail)",
                "[Q] oder Rechtsklick            : Klassen-Fähigkeit 1",
                "[E]                             : Klassen-Ultimate",
                "[F]                             : Interagieren (Truhe, Schrein, Portal)",
                "[TAB]                           : Große Dungeon-Karte umschalten",
                "[ESC] / [P]                     : Pause-Menü",
                "[M]                             : Audio stummschalten / aktivieren",
                "[L]                             : Sprache Deutsch / Englisch",
                "[F11]                           : Vollbild umschalten",
                "",
                "Ziel: Kämpfe dich durch 3 prozedurale Ebenen,",
                "sammle XP für Roguelite-Upgrades und besiege",
                "auf Ebene 3 den Chronos-Wächter Malakor!"
        } : new String[]{
                "W / A / S / D or Arrow Keys     : Move",
                "Mouse Move                      : Aim",
                "Left Click or [J]               : Primary Attack (hold to repeat)",
                "Space or Shift                  : Dash (Invulnerability frames)",
                "[Q] or Right Click              : Class Skill 1",
                "[E]                             : Class Ultimate",
                "[F]                             : Interact (Chest, Shrine, Portal)",
                "[TAB]                           : Toggle Full Map Overlay",
                "[ESC] / [P]                     : Pause Menu",
                "[M]                             : Mute / Unmute Audio",
                "[L]                             : Toggle German / English",
                "[F11]                           : Toggle Fullscreen",
                "",
                "Goal: Descend through 3 procedural floors,",
                "draft Roguelite upgrades on level-up, and defeat",
                "Malakor, Warden of Chronos on Floor 3!"
        });

        // Rechter Block: Informatik-Algorithmen & Architektur
        drawPanel(g, 660, 95, 560, 520, de ? "Informatik-Konzepte & Architektur" : "CS Algorithms & Architecture", de ? new String[]{
                "1. BSP-Tree & Prim's MST:",
                "   Rekursive Raumteilung + Minimaler Spannbaum + Rundwege.",
                "2. Doppel-BFS Graphdurchmesser:",
                "   Start- und Bossraum stets am maximalen Abstand.",
                "3. A*-Pfadfindung (PriorityQueue + Oktil-Heuristik):",
                "   Intelligente Gegnernavigation ohne Corner-Cutting.",
                "4. Recursive Shadowcasting & Bresenham LoS:",
                "   Echtzeit-Fog-of-War über 8 Oktanten & Sichtprüfung.",
                "5. Entwurfsmuster (Design Patterns):",
                "   • State Pattern (Stack-basierte Zustandsmaschine)",
                "   • Strategy Pattern (Klassen- & Gegner-KI-Verhalten)",
                "   • Observer Pattern (Typisierter GameEventBus)",
                "   • Factory / Pool (DungeonGenerator & UpgradePool)",
                "6. Prozeduraler Audio-Synthesizer:",
                "   Reine Java-PCM-Synthese ohne externe Audiodateien.",
                "7. Deterministische Seeds & Persistenz + Unit-Tests."
        } : new String[]{
                "1. BSP-Tree & Prim's MST:",
                "   Recursive space partitioning + MST + tactical loops.",
                "2. Double-BFS Graph Diameter:",
                "   Places Start and Boss rooms at maximum graph distance.",
                "3. A* Pathfinding (PriorityQueue + Octile Heuristic):",
                "   Smart obstacle navigation with corner-cut prevention.",
                "4. Recursive Shadowcasting & Bresenham LoS:",
                "   Real-time 8-octant Fog-of-War & line-of-sight.",
                "5. Software Design Patterns:",
                "   • State Pattern (Stack-based game state machine)",
                "   • Strategy Pattern (Modular AI & hero classes)",
                "   • Observer Pattern (Typed sealed GameEventBus)",
                "   • Factory / Pool (DungeonGenerator & UpgradePool)",
                "6. Procedural PCM Audio Synthesizer:",
                "   Pure javax.sound.sampled synthesis, zero audio assets.",
                "7. Deterministic Seeded Runs, Save Persistence & Tests."
        });

        UiHelper.drawButton(g, backBtn, I18n.t("help.back"), UiHelper.isHovered(backBtn, input), new Color(115, 215, 255));
    }

    private void drawPanel(Graphics2D g, int x, int y, int w, int h, String title, String[] lines) {
        g.setColor(new Color(18, 22, 36, 230));
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(new Color(80, 102, 148));
        g.drawRoundRect(x, y, w, h, 14, 14);

        g.setFont(new Font("SansSerif", Font.BOLD, 17));
        g.setColor(new Color(255, 214, 92));
        g.drawString(title, x + 22, y + 34);

        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g.setColor(new Color(208, 218, 240));
        int ly = y + 66;
        for (String line : lines) {
            g.drawString(line, x + 22, ly);
            ly += 26;
        }
    }
}
