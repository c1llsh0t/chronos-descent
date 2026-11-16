package de.chronos.world;

import de.chronos.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A*-Pfadfindung auf dem Dungeon-Tile-Gitter mit Oktil-Heuristik,
 * Corner-Cutting-Schutz und Knotenlimit für konstante Framezeiten.
 */
public final class AStarPathfinder {

    private static final int MAX_EXPANDED_NODES = 1800;
    private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private record Node(int x, int y, double g, double f) {}

    public List<Vector2D> findPath(DungeonMap map, Vector2D startWorld, Vector2D goalWorld) {
        int sx = map.worldToTile(startWorld.x);
        int sy = map.worldToTile(startWorld.y);
        int gx = map.worldToTile(goalWorld.x);
        int gy = map.worldToTile(goalWorld.y);

        if (!map.inBounds(sx, sy) || !map.inBounds(gx, gy) || !map.isWalkable(gx, gy)) {
            return Collections.emptyList();
        }
        if (sx == gx && sy == gy) {
            return List.of(goalWorld);
        }

        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));
        Map<Long, Double> bestG = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> closed = new HashSet<>();

        long startKey = key(sx, sy);
        long goalKey = key(gx, gy);

        bestG.put(startKey, 0.0);
        open.add(new Node(sx, sy, 0.0, octileHeuristic(sx, sy, gx, gy)));

        int expanded = 0;
        while (!open.isEmpty() && expanded < MAX_EXPANDED_NODES) {
            Node curr = open.poll();
            long currKey = key(curr.x, curr.y);
            if (closed.contains(currKey)) {
                continue;
            }
            if (currKey == goalKey) {
                return reconstructPath(map, parent, startKey, goalKey);
            }
            closed.add(currKey);
            expanded++;

            for (int[] d : DIRS) {
                int nx = curr.x + d[0];
                int ny = curr.y + d[1];
                if (!map.inBounds(nx, ny) || !map.isWalkable(nx, ny)) {
                    continue;
                }
                // Corner-Cutting verhindern bei Diagonalbewegung
                if (d[0] != 0 && d[1] != 0) {
                    if (!map.isWalkable(curr.x + d[0], curr.y) || !map.isWalkable(curr.x, curr.y + d[1])) {
                        continue;
                    }
                }
                long nKey = key(nx, ny);
                if (closed.contains(nKey)) {
                    continue;
                }
                double stepCost = (d[0] != 0 && d[1] != 0) ? 1.41421356 : 1.0;
                double tentativeG = curr.g + stepCost;

                if (tentativeG < bestG.getOrDefault(nKey, Double.POSITIVE_INFINITY)) {
                    bestG.put(nKey, tentativeG);
                    parent.put(nKey, currKey);
                    double f = tentativeG + octileHeuristic(nx, ny, gx, gy);
                    open.add(new Node(nx, ny, tentativeG, f));
                }
            }
        }
        return Collections.emptyList();
    }

    private List<Vector2D> reconstructPath(DungeonMap map, Map<Long, Long> parent, long startKey, long goalKey) {
        List<Vector2D> path = new ArrayList<>();
        long curr = goalKey;
        while (curr != startKey) {
            int tx = (int) (curr >> 32);
            int ty = (int) curr;
            path.add(map.tileCenterWorld(tx, ty));
            Long p = parent.get(curr);
            if (p == null) {
                break;
            }
            curr = p;
        }
        Collections.reverse(path);
        return path;
    }

    private static double octileHeuristic(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        return (dx + dy) + (1.41421356 - 2.0) * Math.min(dx, dy);
    }

    private static long key(int x, int y) {
        return (((long) x) << 32) | (y & 0xFFFFFFFFL);
    }
}
