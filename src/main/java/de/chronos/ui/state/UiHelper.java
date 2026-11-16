package de.chronos.ui.state;

import de.chronos.core.InputHandler;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Wiederverwendbare UI-Zeichenhilfen für konsistente Schaltflächen, Karten und Textumbruch.
 */
public final class UiHelper {
    private UiHelper() {}

    public static boolean isHovered(Rectangle rect, InputHandler input) {
        return rect.contains(input.getMouseX(), input.getMouseY());
    }

    public static boolean isClicked(Rectangle rect, InputHandler input) {
        return isHovered(rect, input) && input.wasMousePressed(MouseEvent.BUTTON1);
    }

    public static void drawButton(Graphics2D g, Rectangle rect, String text, boolean hovered, Color accent) {
        g.setColor(hovered ? new Color(36, 44, 72) : new Color(20, 24, 40));
        g.fillRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);
        g.setColor(hovered ? accent : new Color(75, 92, 130));
        g.setStroke(new BasicStroke(hovered ? 2.2f : 1.5f));
        g.drawRoundRect(rect.x, rect.y, rect.width, rect.height, 10, 10);

        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        FontMetrics fm = g.getFontMetrics();
        int tx = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int ty = rect.y + (rect.height + fm.getAscent() - fm.getDescent()) / 2;
        g.setColor(hovered ? Color.WHITE : new Color(218, 226, 245));
        g.drawString(text, tx, ty);
    }

    public static void drawCenteredString(Graphics2D g, String text, int centerX, int y, Font font, Color color) {
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(color);
        g.drawString(text, centerX - fm.stringWidth(text) / 2, y);
    }

    public static List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;
        String[] words = text.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (cur.isEmpty()) {
                cur.append(w);
            } else if (fm.stringWidth(cur + " " + w) <= maxWidth) {
                cur.append(' ').append(w);
            } else {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            }
        }
        if (!cur.isEmpty()) {
            lines.add(cur.toString());
        }
        return lines;
    }
}
