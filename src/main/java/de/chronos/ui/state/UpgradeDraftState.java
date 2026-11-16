package de.chronos.ui.state;

import de.chronos.combat.Upgrade;
import de.chronos.combat.UpgradePool;
import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;
import de.chronos.core.event.GameEvent;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Overlay-Zustand bei Stufenaufstieg oder Altar-Aktivierung:
 * präsentiert 3 zufällig gewichtete Roguelite-Upgrades zur Auswahl.
 */
public final class UpgradeDraftState implements GameState {

    private final StateManager stateManager;
    private final EngineContext ctx;
    private final GameSession session;
    private final boolean fromAltar;
    private final List<Upgrade> choices;
    private final Rectangle[] cards = {
            new Rectangle(170, 210, 280, 320),
            new Rectangle(500, 210, 280, 320),
            new Rectangle(830, 210, 280, 320)
    };

    public UpgradeDraftState(StateManager stateManager, EngineContext ctx, GameSession session, boolean fromAltar) {
        this.stateManager = stateManager;
        this.ctx = ctx;
        this.session = session;
        this.fromAltar = fromAltar;
        this.choices = UpgradePool.rollChoices(session.getRandom(), 3);
    }

    @Override
    public void update(double dt, InputHandler input) {
        if (input.wasKeyPressed(KeyEvent.VK_1)) choose(0);
        else if (input.wasKeyPressed(KeyEvent.VK_2)) choose(1);
        else if (input.wasKeyPressed(KeyEvent.VK_3)) choose(2);

        for (int i = 0; i < choices.size(); i++) {
            if (UiHelper.isClicked(cards[i], input)) {
                choose(i);
                return;
            }
        }
    }

    private void choose(int index) {
        if (index < 0 || index >= choices.size()) return;
        Upgrade chosen = choices.get(index);
        session.applyChosenUpgrade(chosen);
        ctx.eventBus.publish(new GameEvent.UiClick());
        stateManager.pop();
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        GameState below = stateManager.getBelowTop();
        if (below != null) {
            below.render(g, input);
        }

        g.setColor(new Color(6, 8, 16, 185));
        g.fillRect(0, 0, GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT);

        UiHelper.drawCenteredString(g,
                fromAltar ? I18n.t("draft.altar_title") : I18n.t("draft.title"),
                GameConfig.LOGICAL_WIDTH / 2, 130,
                new Font("SansSerif", Font.BOLD, 28), new Color(255, 224, 110));
        UiHelper.drawCenteredString(g, I18n.t("draft.hint"),
                GameConfig.LOGICAL_WIDTH / 2, 165,
                new Font("SansSerif", Font.PLAIN, 15), new Color(195, 210, 235));

        for (int i = 0; i < choices.size(); i++) {
            Upgrade u = choices.get(i);
            Rectangle r = cards[i];
            boolean hovered = UiHelper.isHovered(r, input);

            g.setColor(hovered ? new Color(34, 42, 68) : new Color(20, 24, 40));
            g.fillRoundRect(r.x, r.y, r.width, r.height, 16, 16);

            g.setColor(u.rarity.color);
            g.setStroke(new BasicStroke(hovered ? 3.2f : 2.0f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, 16, 16);

            // Raritäts-Banner
            g.setColor(new Color(u.rarity.color.getRed(), u.rarity.color.getGreen(), u.rarity.color.getBlue(), 40));
            g.fillRoundRect(r.x + 16, r.y + 18, r.width - 32, 28, 8, 8);
            UiHelper.drawCenteredString(g, I18n.t("rarity." + u.rarity.name()).toUpperCase(),
                    r.x + r.width / 2, r.y + 37, new Font("SansSerif", Font.BOLD, 12), u.rarity.color);

            // Titel
            UiHelper.drawCenteredString(g, u.getName(), r.x + r.width / 2, r.y + 92,
                    new Font("SansSerif", Font.BOLD, 20), Color.WHITE);

            // Beschreibung umgebrochen
            g.setFont(new Font("SansSerif", Font.PLAIN, 14));
            g.setColor(new Color(205, 216, 238));
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = UiHelper.wrapText(u.getDescription(), fm, r.width - 40);
            int ly = r.y + 145;
            for (String line : lines) {
                UiHelper.drawCenteredString(g, line, r.x + r.width / 2, ly, g.getFont(), g.getColor());
                ly += 22;
            }

            // Aktuelle Stapelanzahl & Shortcut
            int curCount = session.getPlayer().getUpgradeCount(u.id);
            if (curCount > 0) {
                UiHelper.drawCenteredString(g, "Stack: " + curCount + " -> " + (curCount + 1),
                        r.x + r.width / 2, r.y + r.height - 52,
                        new Font("SansSerif", Font.BOLD, 13), new Color(120, 230, 175));
            }
            UiHelper.drawCenteredString(g, "[" + (i + 1) + "]", r.x + r.width / 2, r.y + r.height - 20,
                    new Font("SansSerif", Font.BOLD, 16), u.rarity.color);
        }
    }
}
