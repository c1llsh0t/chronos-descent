package de.chronos.ui.state;

import de.chronos.core.GameConfig;
import de.chronos.core.I18n;
import de.chronos.core.InputHandler;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;

/**
 * Intro-Bildschirm beim Programmstart mit Entwickler- und Herausgeber-Credits.
 * Blendet weich ein und aus und lässt sich per Tastendruck oder Klick überspringen.
 */
public final class IntroState implements GameState {

    public static final String DEVELOPER = "K Mujkanovic";
    public static final String PUBLISHER = "c1llsh0t";

    private static final double FADE_IN = 0.8;
    private static final double HOLD = 2.6;
    private static final double FADE_OUT = 0.7;
    private static final double TOTAL = FADE_IN + HOLD + FADE_OUT;
    /** Kurze Sperre, damit ein noch gedrückter Startklick das Intro nicht sofort überspringt. */
    private static final double SKIP_LOCK = 0.25;

    private final StateManager stateManager;
    private double time = 0.0;
    private boolean finished = false;

    public IntroState(StateManager stateManager) {
        this.stateManager = stateManager;
    }

    @Override
    public void update(double dt, InputHandler input) {
        if (finished) {
            return;
        }
        time += dt;
        boolean skip = time > SKIP_LOCK && input.wasAnyInputPressed();
        if (skip && time < FADE_IN + HOLD) {
            // Sofort in die Ausblendphase springen statt hart abzuschneiden
            time = FADE_IN + HOLD;
        }
        if (time >= TOTAL) {
            finished = true;
            input.clearAllInputs();
            stateManager.pop();
        }
    }

    private double alpha() {
        if (time < FADE_IN) {
            return smooth(time / FADE_IN);
        }
        if (time < FADE_IN + HOLD) {
            return 1.0;
        }
        return smooth(Math.max(0.0, 1.0 - (time - FADE_IN - HOLD) / FADE_OUT));
    }

    private static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    public void render(Graphics2D g, InputHandler input) {
        int w = GameConfig.LOGICAL_WIDTH;
        int h = GameConfig.LOGICAL_HEIGHT;
        int cx = w / 2;

        g.setPaint(new GradientPaint(0, 0, new Color(6, 7, 14), 0, h, new Color(14, 9, 24)));
        g.fillRect(0, 0, w, h);

        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha()));

        // Leuchtender Ring als Logo-Andeutung
        double pulse = 0.5 + 0.5 * Math.sin(time * 2.4);
        int ringR = 64;
        g.setColor(new Color(85, 200, 255, 40 + (int) (40 * pulse)));
        g.fillOval(cx - ringR - 18, 190 - ringR - 18, (ringR + 18) * 2, (ringR + 18) * 2);
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(120, 220, 255));
        g.drawOval(cx - ringR, 190 - ringR, ringR * 2, ringR * 2);
        double hand = time * 1.6 - Math.PI / 2;
        g.drawLine(cx, 190, cx + (int) (Math.cos(hand) * ringR * 0.75), 190 + (int) (Math.sin(hand) * ringR * 0.75));
        g.fillOval(cx - 5, 185, 10, 10);

        UiHelper.drawCenteredString(g, "CHRONOS", cx, 330,
                new Font("SansSerif", Font.BOLD, 56), new Color(235, 242, 255));
        UiHelper.drawCenteredString(g, "AETHELGARD'S DESCENT", cx, 366,
                new Font("SansSerif", Font.BOLD, 18), new Color(126, 204, 255));

        g.setColor(new Color(90, 110, 150));
        g.setStroke(new BasicStroke(1f));
        g.drawLine(cx - 160, 410, cx + 160, 410);

        UiHelper.drawCenteredString(g, I18n.t("intro.developed_by"), cx, 455,
                new Font("SansSerif", Font.PLAIN, 16), new Color(160, 175, 205));
        UiHelper.drawCenteredString(g, DEVELOPER, cx, 487,
                new Font("SansSerif", Font.BOLD, 26), Color.WHITE);

        UiHelper.drawCenteredString(g, I18n.t("intro.published_by"), cx, 540,
                new Font("SansSerif", Font.PLAIN, 16), new Color(160, 175, 205));
        UiHelper.drawCenteredString(g, PUBLISHER, cx, 572,
                new Font("SansSerif", Font.BOLD, 26), new Color(255, 214, 92));

        UiHelper.drawCenteredString(g, I18n.t("intro.skip"), cx, 670,
                new Font("SansSerif", Font.PLAIN, 12), new Color(110, 122, 150));

        g.setComposite(old);
    }
}
