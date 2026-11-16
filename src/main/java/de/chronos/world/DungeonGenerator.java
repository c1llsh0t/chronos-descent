package de.chronos.world;

import de.chronos.core.GameConfig;
import de.chronos.math.SeededRandom;
import de.chronos.math.Vector2D;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prozeduraler Dungeon-Generator auf Basis von:
 * 1. Binary Space Partitioning (BSP) zur konfliktfreien Raumaufteilung
 * 2. Prims Minimalem Spannbaum (MST) zur Verbindung aller Räume + Zyklus-Kanten
 * 3. Doppel-BFS-Graphdurchmesser zur optimalen Platzierung von Start- und Bossraum
 * 4. Automatischer Wand-, Säulen-, Fackel- und Fallen-Dekoration
 */
public final class DungeonGenerator {

    private static final int MIN_LEAF_SIZE = 15;
    private static final int MAX_LEAF_SIZE = 24;

    private static final class BspNode {
        final int x, y, w, h;
        BspNode left;
        BspNode right;
        Room room;

        BspNode(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        boolean isLeaf() {
            return left == null && right == null;
        }
    }

    private record Edge(int a, int b, double weight) {}

    public DungeonMap generate(int floorNumber, long seed) {
        SeededRandom rng = new SeededRandom(seed ^ (floorNumber * 0x9E3779B97F4A7C15L));

        int cols = GameConfig.BASE_MAP_COLS + (floorNumber - 1) * 8;
        int rows = GameConfig.BASE_MAP_ROWS + (floorNumber - 1) * 6;

        TileType[][] grid = new TileType[rows][cols];
        for (TileType[] row : grid) {
            Arrays.fill(row, TileType.VOID);
        }

        // 1. BSP-Baum aufbauen
        BspNode root = new BspNode(2, 2, cols - 4, rows - 4);
        splitRecursive(root, rng, 0);

        // 2. Räume in Blattknoten erzeugen
        List<Room> rooms = new ArrayList<>();
        collectRoomsFromLeaves(root, rooms, rng);

        // Fallback falls zu wenige Räume entstanden
        if (rooms.size() < 4) {
            rooms.clear();
            rooms.add(new Room(0, 4, 4, 12, 10, Room.RoomType.COMBAT));
            rooms.add(new Room(1, cols - 18, 4, 12, 10, Room.RoomType.COMBAT));
            rooms.add(new Room(2, 4, rows - 16, 12, 10, Room.RoomType.COMBAT));
            rooms.add(new Room(3, cols - 18, rows - 16, 12, 10, Room.RoomType.COMBAT));
        }

        for (Room r : rooms) {
            carveRoom(grid, r);
        }

        // 3. Minimaler Spannbaum (Prim) + Zusatzkanten für taktische Rundwege
        List<Edge> selectedEdges = connectRoomsWithMstAndCycles(rooms, rng);
        for (Edge e : selectedEdges) {
            Room ra = rooms.get(e.a);
            Room rb = rooms.get(e.b);
            ra.neighbors.add(rb.id);
            rb.neighbors.add(ra.id);
            carveCorridor(grid, ra.centerTileX(), ra.centerTileY(), rb.centerTileX(), rb.centerTileY(), rng);
        }

        // 4. Doppel-BFS für Start- und Bossraum am maximalen Graph-Durchmesser
        int farFromZero = findFarthestRoom(rooms, 0);
        int opposite = findFarthestRoom(rooms, farFromZero);

        Room startRoom = rooms.get(farFromZero);
        Room bossRoom = rooms.get(opposite);
        startRoom.type = Room.RoomType.START;
        startRoom.cleared = true;
        startRoom.discovered = true;
        bossRoom.type = Room.RoomType.BOSS;

        // Übrige Raumtypen vergeben
        List<Room> middleRooms = new ArrayList<>();
        for (Room r : rooms) {
            if (r != startRoom && r != bossRoom) {
                middleRooms.add(r);
            }
        }
        if (middleRooms.size() >= 2) {
            Room treasure = middleRooms.remove(rng.nextInt(middleRooms.size()));
            treasure.type = Room.RoomType.TREASURE;
            treasure.cleared = true;
        }
        if (middleRooms.size() >= 2) {
            Room shrine = middleRooms.remove(rng.nextInt(middleRooms.size()));
            shrine.type = Room.RoomType.SHRINE;
            shrine.cleared = true;
        }

        // 5. Wände um begehbare Felder setzen
        buildWallsAroundFloors(grid, cols, rows);

        // 6. Hindernis-Säulen & Stachelfallen nur im sicheren Innenbereich von Kampfräumen
        decorateRooms(grid, rooms, rng);

        DungeonMap map = new DungeonMap(cols, rows, floorNumber, seed, grid, rooms);
        map.setStartRoom(startRoom);
        map.setBossRoom(bossRoom);

        // 7. Fackeln platzieren
        placeTorches(map, rooms, rng);

        return map;
    }

    private void splitRecursive(BspNode node, SeededRandom rng, int depth) {
        if (depth >= 5) {
            return;
        }
        boolean canSplitH = node.h >= MIN_LEAF_SIZE * 2;
        boolean canSplitV = node.w >= MIN_LEAF_SIZE * 2;
        if (!canSplitH && !canSplitV) {
            return;
        }
        if (node.w <= MAX_LEAF_SIZE && node.h <= MAX_LEAF_SIZE && rng.chance(0.25)) {
            return;
        }

        boolean splitHorizontal;
        if (canSplitH && canSplitV) {
            if (node.w > node.h * 1.25) {
                splitHorizontal = false;
            } else if (node.h > node.w * 1.25) {
                splitHorizontal = true;
            } else {
                splitHorizontal = rng.nextBoolean();
            }
        } else {
            splitHorizontal = canSplitH;
        }

        if (splitHorizontal) {
            int splitY = rng.nextInt(MIN_LEAF_SIZE, node.h - MIN_LEAF_SIZE);
            node.left = new BspNode(node.x, node.y, node.w, splitY);
            node.right = new BspNode(node.x, node.y + splitY, node.w, node.h - splitY);
        } else {
            int splitX = rng.nextInt(MIN_LEAF_SIZE, node.w - MIN_LEAF_SIZE);
            node.left = new BspNode(node.x, node.y, splitX, node.h);
            node.right = new BspNode(node.x + splitX, node.y, node.w - splitX, node.h);
        }

        splitRecursive(node.left, rng, depth + 1);
        splitRecursive(node.right, rng, depth + 1);
    }

    private void collectRoomsFromLeaves(BspNode node, List<Room> rooms, SeededRandom rng) {
        if (node == null) {
            return;
        }
        if (node.isLeaf()) {
            int maxW = Math.max(7, node.w - 4);
            int maxH = Math.max(7, node.h - 4);
            int rw = rng.nextInt(Math.min(8, maxW), Math.min(15, maxW));
            int rh = rng.nextInt(Math.min(8, maxH), Math.min(13, maxH));

            int rx = node.x + 2 + rng.nextInt(Math.max(1, node.w - rw - 3));
            int ry = node.y + 2 + rng.nextInt(Math.max(1, node.h - rh - 3));

            Room room = new Room(rooms.size(), rx, ry, rw, rh, Room.RoomType.COMBAT);
            node.room = room;
            rooms.add(room);
        } else {
            collectRoomsFromLeaves(node.left, rooms, rng);
            collectRoomsFromLeaves(node.right, rooms, rng);
        }
    }

    private void carveRoom(TileType[][] grid, Room r) {
        for (int ty = r.tileY; ty < r.tileY + r.height; ty++) {
            for (int tx = r.tileX; tx < r.tileX + r.width; tx++) {
                grid[ty][tx] = TileType.FLOOR;
            }
        }
    }

    private List<Edge> connectRoomsWithMstAndCycles(List<Room> rooms, SeededRandom rng) {
        int n = rooms.size();
        Set<Integer> visited = new HashSet<>();
        visited.add(0);

        List<Edge> mstEdges = new ArrayList<>();
        Set<Long> usedPairs = new HashSet<>();

        while (visited.size() < n) {
            Edge best = null;
            for (int u : visited) {
                for (int v = 0; v < n; v++) {
                    if (visited.contains(v)) {
                        continue;
                    }
                    double dist = roomDistance(rooms.get(u), rooms.get(v));
                    if (best == null || dist < best.weight) {
                        best = new Edge(u, v, dist);
                    }
                }
            }
            if (best == null) {
                break;
            }
            mstEdges.add(best);
            visited.add(best.b);
            usedPairs.add(pairKey(best.a, best.b));
        }

        // Zusätzliche kurze Kanten einfügen für taktische Rundwege
        List<Edge> candidates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!usedPairs.contains(pairKey(i, j))) {
                    candidates.add(new Edge(i, j, roomDistance(rooms.get(i), rooms.get(j))));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(e -> e.weight));
        int extraTarget = Math.max(1, n / 4);
        int added = 0;
        for (Edge cand : candidates) {
            if (added >= extraTarget) {
                break;
            }
            if (rng.chance(0.75)) {
                mstEdges.add(cand);
                added++;
            }
        }
        return mstEdges;
    }

    private static long pairKey(int a, int b) {
        int min = Math.min(a, b);
        int max = Math.max(a, b);
        return (((long) min) << 32) | (max & 0xFFFFFFFFL);
    }

    private static double roomDistance(Room a, Room b) {
        double dx = a.centerTileX() - b.centerTileX();
        double dy = a.centerTileY() - b.centerTileY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void carveCorridor(TileType[][] grid, int x0, int y0, int x1, int y1, SeededRandom rng) {
        boolean horizFirst = rng.nextBoolean();
        if (horizFirst) {
            carveHorizontal(grid, x0, x1, y0);
            carveVertical(grid, y0, y1, x1);
        } else {
            carveVertical(grid, y0, y1, x0);
            carveHorizontal(grid, x0, x1, y1);
        }
    }

    private void carveHorizontal(TileType[][] grid, int x0, int x1, int y) {
        int start = Math.min(x0, x1);
        int end = Math.max(x0, x1);
        for (int x = start; x <= end; x++) {
            for (int dy = -1; dy <= 1; dy++) {
                int ny = y + dy;
                if (ny > 0 && ny < grid.length - 1 && x > 0 && x < grid[0].length - 1) {
                    if (grid[ny][x] == TileType.VOID) {
                        grid[ny][x] = TileType.CORRIDOR;
                    }
                }
            }
        }
    }

    private void carveVertical(TileType[][] grid, int y0, int y1, int x) {
        int start = Math.min(y0, y1);
        int end = Math.max(y0, y1);
        for (int y = start; y <= end; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                if (y > 0 && y < grid.length - 1 && nx > 0 && nx < grid[0].length - 1) {
                    if (grid[y][nx] == TileType.VOID) {
                        grid[y][nx] = TileType.CORRIDOR;
                    }
                }
            }
        }
    }

    private int findFarthestRoom(List<Room> rooms, int startIdx) {
        Map<Integer, Integer> dist = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        dist.put(startIdx, 0);
        queue.add(startIdx);

        int farthest = startIdx;
        int maxDist = 0;

        while (!queue.isEmpty()) {
            int curr = queue.poll();
            int d = dist.get(curr);
            if (d > maxDist) {
                maxDist = d;
                farthest = curr;
            }
            for (int nxt : rooms.get(curr).neighbors) {
                if (!dist.containsKey(nxt)) {
                    dist.put(nxt, d + 1);
                    queue.add(nxt);
                }
            }
        }
        return farthest;
    }

    private void buildWallsAroundFloors(TileType[][] grid, int cols, int rows) {
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (grid[y][x] != TileType.VOID) {
                    continue;
                }
                boolean nearFloor = false;
                for (int dy = -1; dy <= 1 && !nearFloor; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx >= 0 && nx < cols && ny >= 0 && ny < rows) {
                            TileType t = grid[ny][nx];
                            if (t == TileType.FLOOR || t == TileType.CORRIDOR) {
                                nearFloor = true;
                                break;
                            }
                        }
                    }
                }
                if (nearFloor) {
                    grid[y][x] = TileType.WALL;
                }
            }
        }
    }

    private void decorateRooms(TileType[][] grid, List<Room> rooms, SeededRandom rng) {
        for (Room r : rooms) {
            if (r.type == Room.RoomType.COMBAT && r.width >= 10 && r.height >= 9) {
                // Symmetrische Säulen für Deckung im Fernkampf
                if (rng.chance(0.65)) {
                    int px1 = r.tileX + 2;
                    int px2 = r.tileX + r.width - 3;
                    int py1 = r.tileY + 2;
                    int py2 = r.tileY + r.height - 3;
                    if (isInnerFloorSafe(r, px1, py1)) grid[py1][px1] = TileType.PILLAR;
                    if (isInnerFloorSafe(r, px2, py1)) grid[py1][px2] = TileType.PILLAR;
                    if (isInnerFloorSafe(r, px1, py2)) grid[py2][px1] = TileType.PILLAR;
                    if (isInnerFloorSafe(r, px2, py2)) grid[py2][px2] = TileType.PILLAR;
                }
                // Stachelfallen im Zentrum versetzt platzieren
                if (rng.chance(0.55)) {
                    int traps = rng.nextInt(1, 3);
                    for (int i = 0; i < traps; i++) {
                        int tx = rng.nextInt(r.tileX + 3, r.tileX + r.width - 4);
                        int ty = rng.nextInt(r.tileY + 3, r.tileY + r.height - 4);
                        if (grid[ty][tx] == TileType.FLOOR && (tx != r.centerTileX() || ty != r.centerTileY())) {
                            grid[ty][tx] = TileType.SPIKE_TRAP;
                        }
                    }
                }
            } else if (r.type == Room.RoomType.BOSS && r.width >= 11 && r.height >= 10) {
                // Arena-Säulen als Schutz gegen Boss-Geschosse
                grid[r.tileY + 2][r.tileX + 2] = TileType.PILLAR;
                grid[r.tileY + 2][r.tileX + r.width - 3] = TileType.PILLAR;
                grid[r.tileY + r.height - 3][r.tileX + 2] = TileType.PILLAR;
                grid[r.tileY + r.height - 3][r.tileX + r.width - 3] = TileType.PILLAR;
            }
        }
    }

    private boolean isInnerFloorSafe(Room r, int tx, int ty) {
        return tx > r.tileX + 1 && tx < r.tileX + r.width - 2
                && ty > r.tileY + 1 && ty < r.tileY + r.height - 2
                && (tx != r.centerTileX() || ty != r.centerTileY());
    }

    private void placeTorches(DungeonMap map, List<Room> rooms, SeededRandom rng) {
        for (Room r : rooms) {
            int[][] corners = {
                    {r.tileX + 1, r.tileY + 1},
                    {r.tileX + r.width - 2, r.tileY + 1},
                    {r.tileX + 1, r.tileY + r.height - 2},
                    {r.tileX + r.width - 2, r.tileY + r.height - 2}
            };
            for (int[] c : corners) {
                if (map.isWalkable(c[0], c[1]) && rng.chance(0.75)) {
                    map.addTorch(map.tileCenterWorld(c[0], c[1]));
                }
            }
        }
    }
}
