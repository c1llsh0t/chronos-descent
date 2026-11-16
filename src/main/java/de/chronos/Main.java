package de.chronos;

import de.chronos.core.GameEngine;
import de.chronos.ui.GameFrame;

import javax.swing.SwingUtilities;

/**
 * Programmeinstiegspunkt für CHRONOS: Aethelgard's Descent.
 */
public final class Main {
    private Main() {}

    public static void main(String[] args) {
        if (args.length > 0 && "--smoke-test".equals(args[0])) {
            GameEngine engine = new GameEngine(null, null);
            for (int i = 0; i < 10; i++) {
                engine.stepAndRenderFrame(1.0 / 60.0);
            }
            engine.stop();
            System.out.println("Headless smoke test completed cleanly.");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            GameFrame frame = new GameFrame();
            frame.launch();
        });
    }
}
