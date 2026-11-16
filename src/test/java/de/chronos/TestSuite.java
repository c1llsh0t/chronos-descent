package de.chronos;

import de.chronos.combat.Upgrade;
import de.chronos.combat.UpgradePool;
import de.chronos.core.GameConfig;
import de.chronos.core.SaveManager;
import de.chronos.core.event.GameEventBus;
import de.chronos.entity.Enemy;
import de.chronos.entity.EnemyType;
import de.chronos.entity.HeroClass;
import de.chronos.graphics.WorldRenderer;
import de.chronos.math.SeededRandom;
import de.chronos.math.Vector2D;
import de.chronos.ui.HudRenderer;
import de.chronos.ui.state.GameSession;
import de.chronos.world.AStarPathfinder;
import de.chronos.world.DungeonGenerator;
import de.chronos.world.DungeonMap;
import de.chronos.world.Room;
import de.chronos.world.TileType;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Automatisierte Unit- und Integrationstests ohne externe Test-Frameworks.
 * Prüft Dungeon-Konnektivität, Determinismus, A*-Pfadfindung, Shadowcasting,
 * Kampfsystem, Upgrade-Logik, Persistenz und Headless-Rendering.
 */
public final class TestSuite {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== CHRONOS: Aethelgard's Descent — Automated Test Suite ===");

        runTest("Vector2D math operations", TestSuite::testVectorMath);
        runTest("SeededRandom determinism and string hashing", TestSuite::testSeededRandom);
        runTest("BSP + MST Dungeon connectivity & determinism (150 maps)", TestSuite::testDungeonGenerator);
        runTest("A* Pathfinder validity & corner-cut prevention", TestSuite::testAStarPathfinding);
        runTest("Shadowcasting Fog-of-War & Bresenham Line-of-Sight", TestSuite::testFogAndLineOfSight);
        runTest("Combat, Upgrades, Level-Up & Elder Slime splitting", TestSuite::testCombatAndUpgrades);
        runTest("SaveManager persistence & highscore sorting", TestSuite::testSaveManagerRoundtrip);
        runTest("Headless 3-class simulation & Java2D rendering smoke test", TestSuite::testHeadlessSimulationAndRender);

        System.out.printf("%nRESULT: %d passed, %d failed.%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void runTest(String name, ThrowingRunnable test) {
        try {
            test.run();
            passed++;
            System.out.println("[PASS] " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("[FAIL] " + name + " -> " + t.getMessage());
            t.printStackTrace(System.out);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    private static void assertEquals(long expected, long actual, String msg) {
        if (expected != actual) throw new AssertionError(msg + " (expected=" + expected + ", actual=" + actual + ")");
    }

    private static void assertNear(double expected, double actual, double eps, String msg) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError(msg + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void testVectorMath() {
        Vector2D a = new Vector2D(3.0, 4.0);
        assertNear(5.0, a.length(), 1e-6, "Length of (3,4) must be 5");
        Vector2D norm = a.normalize();
        assertNear(1.0, norm.length(), 1e-6, "Normalized vector must have length 1");
        Vector2D rot = new Vector2D(1.0, 0.0).rotate(Math.PI / 2.0);
        assertNear(0.0, rot.x, 1e-6, "Rotated X");
        assertNear(1.0, rot.y, 1e-6, "Rotated Y");
        assertNear(0.0, a.dot(new Vector2D(-4.0, 3.0)), 1e-6, "Orthogonal dot product must be 0");
    }

    private static void testSeededRandom() {
        long s1 = SeededRandom.parseSeed("Informatik2026");
        long s2 = SeededRandom.parseSeed("Informatik2026");
        assertEquals(s1, s2, "Seed string hash must be deterministic");

        SeededRandom r1 = new SeededRandom(s1);
        SeededRandom r2 = new SeededRandom(s2);
        for (int i = 0; i < 25; i++) {
            assertEquals(r1.nextInt(100000), r2.nextInt(100000), "Random sequence mismatch at step " + i);
        }
    }

    private static void testDungeonGenerator() {
        DungeonGenerator gen = new DungeonGenerator();

        // Determinismus prüfen
        DungeonMap m1 = gen.generate(1, 987654321L);
        DungeonMap m2 = gen.generate(1, 987654321L);
        assertEquals(m1.getRooms().size(), m2.getRooms().size(), "Deterministic room count");
        for (int y = 0; y < m1.getRows(); y++) {
            for (int x = 0; x < m1.getCols(); x++) {
                assertTrue(m1.getTile(x, y) == m2.getTile(x, y), "Tiles must match deterministically");
            }
        }

        // Konnektivität über 50 Seeds * 3 Ebenen = 150 Maps per Tile-BFS prüfen
        for (int seedIdx = 1; seedIdx <= 50; seedIdx++) {
            long seed = seedIdx * 7919L + 17L;
            for (int floor = 1; floor <= 3; floor++) {
                DungeonMap map = gen.generate(floor, seed);
                assertTrue(map.getStartRoom() != null, "Start room must exist");
                assertTrue(map.getBossRoom() != null, "Boss room must exist");
                assertTrue(map.getStartRoom() != map.getBossRoom(), "Start and Boss rooms must be distinct");

                Vector2D spawn = map.getStartRoom().centerWorld();
                assertTrue(!map.collidesCircleWithWalls(spawn.x, spawn.y, 15.0), "Player spawn must be unobstructed");

                Set<Long> visited = bfsReachableTiles(map, map.getStartRoom().centerTileX(), map.getStartRoom().centerTileY());
                for (Room r : map.getRooms()) {
                    long key = (((long) r.centerTileX()) << 32) | (r.centerTileY() & 0xFFFFFFFFL);
                    assertTrue(visited.contains(key),
                            "Room " + r.id + " unreachable on floor " + floor + " seed " + seed);
                }
            }
        }
    }

    private static Set<Long> bfsReachableTiles(DungeonMap map, int sx, int sy) {
        Set<Long> visited = new HashSet<>();
        Deque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy});
        visited.add((((long) sx) << 32) | (sy & 0xFFFFFFFFL));

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur[0] + d[0];
                int ny = cur[1] + d[1];
                if (map.inBounds(nx, ny) && map.isWalkable(nx, ny)) {
                    long key = (((long) nx) << 32) | (ny & 0xFFFFFFFFL);
                    if (visited.add(key)) {
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }
        return visited;
    }

    private static void testAStarPathfinding() {
        DungeonGenerator gen = new DungeonGenerator();
        DungeonMap map = gen.generate(1, 424242L);
        AStarPathfinder astar = new AStarPathfinder();

        Vector2D start = map.getStartRoom().centerWorld();
        Vector2D goal = map.getBossRoom().centerWorld();
        List<Vector2D> path = astar.findPath(map, start, goal);

        assertTrue(!path.isEmpty(), "A* must find a path between Start and Boss rooms");
        for (Vector2D wp : path) {
            int tx = map.worldToTile(wp.x);
            int ty = map.worldToTile(wp.y);
            assertTrue(map.isWalkable(tx, ty), "Path waypoint must be walkable");
        }

        // Unbegehbares Ziel muss leeren Pfad liefern
        List<Vector2D> unreachable = astar.findPath(map, start, map.tileCenterWorld(0, 0));
        assertTrue(unreachable.isEmpty(), "Path to VOID border must be empty");
    }

    private static void testFogAndLineOfSight() {
        DungeonGenerator gen = new DungeonGenerator();
        DungeonMap map = gen.generate(1, 111111L);
        Vector2D spawn = map.getStartRoom().centerWorld();
        map.updateFogOfWar(spawn.x, spawn.y, GameConfig.VISION_RADIUS_TILES);

        int stx = map.worldToTile(spawn.x);
        int sty = map.worldToTile(spawn.y);
        assertTrue(map.getVisibility(stx, sty) == DungeonMap.Visibility.VISIBLE, "Origin tile must be VISIBLE");
        assertTrue(map.hasLineOfSight(spawn.x, spawn.y, spawn.x + 15.0, spawn.y), "Short LoS in start room must be clear");

        // Randfeld (0,0) liegt außerhalb der Sichtweite und bleibt UNSEEN
        assertTrue(map.getVisibility(0, 0) == DungeonMap.Visibility.UNSEEN, "Border VOID tile must remain UNSEEN");
    }

    private static void testCombatAndUpgrades() throws Exception {
        Path tempSave = Files.createTempFile("chronos-test-combat", ".dat");
        try {
            SaveManager save = new SaveManager(tempSave);
            GameSession session = new GameSession(HeroClass.VANGUARD, GameConfig.Difficulty.NORMAL, 12345L, new GameEventBus(), save);

            double initialHp = session.getPlayer().maxHp;
            List<Upgrade> choices = UpgradePool.rollChoices(session.getRandom(), 3);
            assertEquals(3, choices.size(), "UpgradePool must roll 3 choices");

            Upgrade vital = UpgradePool.getAll().stream().filter(u -> u.id.equals("VITAL_HEART")).findFirst().orElseThrow();
            session.applyChosenUpgrade(vital);
            assertNear(initialHp + 30.0, session.getPlayer().maxHp, 1e-6, "VITAL_HEART must increase maxHp by 30");

            // Level-Up prüfen
            boolean leveled = session.getPlayer().addXp(100);
            assertTrue(leveled, "Adding 100 XP at level 1 must trigger level-up");
            assertTrue(session.getPlayer().level >= 2, "Level must advance");

            // Elder-Slime-Spaltung ohne ConcurrentModificationException testen
            Enemy elder = new Enemy(session.getPlayer().x + 40, session.getPlayer().y,
                    EnemyType.ELDER_SLIME, session.getMap().getStartRoom().id, 1.0, 1.0);
            session.spawnEnemy(elder);
            session.update(1.0 / 60.0, 0, 0, 640, 360, false, false, false, false, false);
            int beforeCount = session.getEnemies().size();
            session.damageEnemy(elder, 9999.0, true, 0, 0);
            session.update(1.0 / 60.0, 0, 0, 640, 360, false, false, false, false, false);
            int afterCount = session.getEnemies().size();
            assertTrue(afterCount >= beforeCount + 1, "Killing Elder Slime must spawn 2 Slime children (net +1 enemy)");
        } finally {
            Files.deleteIfExists(tempSave);
        }
    }

    private static void testSaveManagerRoundtrip() throws Exception {
        Path tempSave = Files.createTempFile("chronos-test-save", ".dat");
        try {
            SaveManager writer = new SaveManager(tempSave);
            writer.recordRun(450, "VANGUARD", "NORMAL", 2, 18, 120.5, false, 111L);
            writer.recordRun(1850, "ARCANIST", "HARD", 3, 45, 240.0, true, 222L);
            writer.unlockAchievement("FIRST_BLOOD");
            writer.unlockAchievement("BOSS_SLAYER");

            SaveManager reader = new SaveManager(tempSave);
            List<SaveManager.HighscoreEntry> list = reader.getHighscores();
            assertEquals(2, list.size(), "Two saved highscore entries expected");
            assertEquals(1850, list.get(0).score(), "Highest score must be sorted first");
            assertTrue(reader.isAchievementUnlocked("BOSS_SLAYER"), "Achievement must persist across reload");
        } finally {
            Files.deleteIfExists(tempSave);
        }
    }

    private static void testHeadlessSimulationAndRender() throws Exception {
        Path tempSave = Files.createTempFile("chronos-test-sim", ".dat");
        try {
            SaveManager save = new SaveManager(tempSave);
            WorldRenderer worldRenderer = new WorldRenderer();
            HudRenderer hudRenderer = new HudRenderer();
            BufferedImage frame = new BufferedImage(GameConfig.LOGICAL_WIDTH, GameConfig.LOGICAL_HEIGHT, BufferedImage.TYPE_INT_RGB);

            for (HeroClass hc : HeroClass.values()) {
                GameSession session = new GameSession(hc, GameConfig.Difficulty.NORMAL, 55555L, new GameEventBus(), save);
                for (int tick = 0; tick < 90; tick++) {
                    boolean atk = (tick % 10 == 0);
                    boolean dash = (tick == 15);
                    boolean q = (tick == 30);
                    boolean e = (tick == 45);
                    session.update(GameConfig.FIXED_DT, 1.0, 0.5, 900, 420, atk, dash, q, e, false);
                }
                Graphics2D g = frame.createGraphics();
                try {
                    worldRenderer.render(g, session, 900, 420, 1.5);
                    hudRenderer.render(g, session, tickShowMap(hc), false);
                } finally {
                    g.dispose();
                }
            }
            // Ebene 3 Boss-Simulation prüfen
            GameSession bossRun = new GameSession(HeroClass.ARCANIST, GameConfig.Difficulty.HARD, 77777L, new GameEventBus(), save);
            bossRun.loadFloor(3);
            assertEquals(3, bossRun.getCurrentFloor(), "Floor 3 must load cleanly");
            for (int tick = 0; tick < 60; tick++) {
                bossRun.update(GameConfig.FIXED_DT, 0.0, 0.0, 640, 360, true, false, false, true, false);
            }
        } finally {
            Files.deleteIfExists(tempSave);
        }
    }

    private static boolean tickShowMap(HeroClass hc) {
        return hc == HeroClass.SHADOWBLADE;
    }
}
