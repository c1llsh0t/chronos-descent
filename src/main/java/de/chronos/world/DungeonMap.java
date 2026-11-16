package de.chronos.world;

import de.chronos.core.GameConfig;
import de.chronos.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hält das 2D-Tile-Gitter, Räume, Sichtfeld (Fog-of-War), Kollisionsauflösung
 * sowie Line-of-Sight-Berechnungen (Bresenham + Recursive Shadowcasting).
 */
public final class DungeonMap {

    public enum Visibility {
        UNSEEN,
        EXPLORED,
        VISIBLE
    }

    private final int cols;
    private final int rows;
    private final int floorNumber;
    private final long seed;

    private final TileType[][] tiles;
    private final Visibility[][] visibility;
    private final double[][] lightLevel;
    private final List<Room> rooms;
    private final List<Vector2D> torches = new ArrayList<>();

    private Room startRoom;
    private Room bossRoom;

    public DungeonMap(int cols, int rows, int floorNumber, long seed, TileType[][] tiles, List<Room> rooms) {
        this.cols = cols;
        this.rows = rows;
        this.floorNumber = floorNumber;
        this.seed = seed;
        this.tiles = tiles;
        this.rooms = new ArrayList<>(rooms);
        this.visibility = new Visibility[rows][cols];
        this.lightLevel = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                visibility[r][c] = Visibility.UNSEEN;
            }
        }
    }

    public int getCols() { return cols; }
    public int getRows() { return rows; }
    public int getFloorNumber() { return floorNumber; }
    public long getSeed() { return seed; }
    public List<Room> getRooms() { return Collections.unmodifiableList(rooms); }
    public List<Vector2D> getTorches() { return Collections.unmodifiableList(torches); }
    public void addTorch(Vector2D pos) { torches.add(pos); }

    public Room getStartRoom() { return startRoom; }
    public void setStartRoom(Room startRoom) { this.startRoom = startRoom; }
    public Room getBossRoom() { return bossRoom; }
    public void setBossRoom(Room bossRoom) { this.bossRoom = bossRoom; }

    public boolean inBounds(int tx, int ty) {
        return tx >= 0 && tx < cols && ty >= 0 && ty < rows;
    }

    public TileType getTile(int tx, int ty) {
        if (!inBounds(tx, ty)) {
            return TileType.VOID;
        }
        return tiles[ty][tx];
    }

    public boolean isSolid(int tx, int ty) {
        return getTile(tx, ty).solid;
    }

    public boolean isWalkable(int tx, int ty) {
        return !isSolid(tx, ty);
    }

    public Visibility getVisibility(int tx, int ty) {
        if (!inBounds(tx, ty)) {
            return Visibility.UNSEEN;
        }
        return visibility[ty][tx];
    }

    public double getLightLevel(int tx, int ty) {
        if (!inBounds(tx, ty)) {
            return 0.0;
        }
        return lightLevel[ty][tx];
    }

    public int worldToTile(double worldCoord) {
        return (int) Math.floor(worldCoord / GameConfig.TILE_SIZE);
    }

    public Vector2D tileCenterWorld(int tx, int ty) {
        return new Vector2D(
                (tx + 0.5) * GameConfig.TILE_SIZE,
                (ty + 0.5) * GameConfig.TILE_SIZE
        );
    }

    public Room getRoomAt(double wx, double wy) {
        int tx = worldToTile(wx);
        int ty = worldToTile(wy);
        for (Room room : rooms) {
            if (room.containsTile(tx, ty)) {
                return room;
            }
        }
        return null;
    }

    /**
     * Löst kreisförmige Entitätsbewegung gegen solide Wände auf.
     * Getrennte X-/Y-Auflösung garantiert sauberes Entlanggleiten an Kanten ohne Hängenbleiben.
     */
    public Vector2D moveWithCollision(double x, double y, double dx, double dy, double radius) {
        double nextX = x + dx;
        if (canMoveTo(x, y, nextX, y, radius)) {
            x = nextX;
        }
        double nextY = y + dy;
        if (canMoveTo(x, y, x, nextY, radius)) {
            y = nextY;
        }
        return new Vector2D(x, y);
    }

    /**
     * Normalfall: Ziel darf nicht in einer Wand liegen. Steckt ein Objekt bereits in einer Wand
     * (z.B. ungünstiger Spawn), sind Schritte erlaubt, die die Überlappung verringern –
     * so bleibt nichts dauerhaft hängen.
     */
    private boolean canMoveTo(double fromX, double fromY, double toX, double toY, double radius) {
        if (!collidesCircleWithWalls(toX, toY, radius)) {
            return true;
        }
        double before = wallPenetration(fromX, fromY, radius);
        return before > 0.0 && wallPenetration(toX, toY, radius) < before;
    }

    private double wallPenetration(double cx, double cy, double radius) {
        double worst = 0.0;
        int minTx = worldToTile(cx - radius);
        int maxTx = worldToTile(cx + radius);
        int minTy = worldToTile(cy - radius);
        int maxTy = worldToTile(cy + radius);
        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                if (isSolid(tx, ty)) {
                    double tileLeft = tx * GameConfig.TILE_SIZE;
                    double tileTop = ty * GameConfig.TILE_SIZE;
                    double closestX = Math.max(tileLeft, Math.min(cx, tileLeft + GameConfig.TILE_SIZE));
                    double closestY = Math.max(tileTop, Math.min(cy, tileTop + GameConfig.TILE_SIZE));
                    double dist = Math.hypot(cx - closestX, cy - closestY);
                    worst = Math.max(worst, radius - dist);
                }
            }
        }
        return worst;
    }

    public boolean collidesCircleWithWalls(double cx, double cy, double radius) {
        int minTx = worldToTile(cx - radius);
        int maxTx = worldToTile(cx + radius);
        int minTy = worldToTile(cy - radius);
        int maxTy = worldToTile(cy + radius);

        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                if (isSolid(tx, ty)) {
                    double tileLeft = tx * GameConfig.TILE_SIZE;
                    double tileRight = tileLeft + GameConfig.TILE_SIZE;
                    double tileTop = ty * GameConfig.TILE_SIZE;
                    double tileBottom = tileTop + GameConfig.TILE_SIZE;

                    double closestX = Math.max(tileLeft, Math.min(cx, tileRight));
                    double closestY = Math.max(tileTop, Math.min(cy, tileBottom));

                    double distX = cx - closestX;
                    double distY = cy - closestY;
                    if (distX * distX + distY * distY < radius * radius) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Bresenham-Linienalgorithmus zur schnellen Prüfung freier Sichtlinie (Line of Sight).
     */
    public boolean hasLineOfSight(double x0, double y0, double x1, double y1) {
        int tx0 = worldToTile(x0);
        int ty0 = worldToTile(y0);
        int tx1 = worldToTile(x1);
        int ty1 = worldToTile(y1);
        return hasTileLineOfSight(tx0, ty0, tx1, ty1);
    }

    public boolean hasTileLineOfSight(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        int cx = x0;
        int cy = y0;

        while (true) {
            if (!inBounds(cx, cy)) {
                return false;
            }
            if ((cx != x0 || cy != y0) && (cx != x1 || cy != y1) && tiles[cy][cx].blocksLight) {
                return false;
            }
            if (cx == x1 && cy == y1) {
                return true;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cy += sy;
            }
        }
    }

    /**
     * Aktualisiert den Fog-of-War per Recursive Shadowcasting über 8 Oktanten
     * sowie Lichtabfall mit der Entfernung.
     */
    public void updateFogOfWar(double playerX, double playerY, int radiusTiles) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (visibility[r][c] == Visibility.VISIBLE) {
                    visibility[r][c] = Visibility.EXPLORED;
                }
                lightLevel[r][c] = (visibility[r][c] == Visibility.EXPLORED) ? 0.25 : 0.0;
            }
        }

        int ox = worldToTile(playerX);
        int oy = worldToTile(playerY);
        if (inBounds(ox, oy)) {
            visibility[oy][ox] = Visibility.VISIBLE;
            lightLevel[oy][ox] = 1.0;
        }

        for (int octant = 0; octant < 8; octant++) {
            castLight(ox, oy, radiusTiles, 1, 1.0, 0.0, octant);
        }

        // Räume als entdeckt markieren, sobald deren Zentrum oder Inneres sichtbar wird
        for (Room room : rooms) {
            if (!room.discovered) {
                int cx = room.centerTileX();
                int cy = room.centerTileY();
                if (inBounds(cx, cy) && visibility[cy][cx] == Visibility.VISIBLE) {
                    room.discovered = true;
                }
            }
        }
    }

    private void castLight(int ox, int oy, int radius, int row, double startSlope, double endSlope, int octant) {
        if (startSlope < endSlope) {
            return;
        }
        double nextStartSlope = startSlope;
        for (int i = row; i <= radius; i++) {
            boolean blocked = false;
            for (int dx = -i, dy = -i; dx <= 0; dx++) {
                double lSlope = (dx - 0.5) / (dy + 0.5);
                double rSlope = (dx + 0.5) / (dy - 0.5);

                if (startSlope < rSlope) {
                    continue;
                }
                if (endSlope > lSlope) {
                    break;
                }

                int[] mapped = transformOctant(dx, dy, octant);
                int ax = ox + mapped[0];
                int ay = oy + mapped[1];

                double distSq = dx * dx + dy * dy;
                if (inBounds(ax, ay) && distSq <= radius * radius) {
                    visibility[ay][ax] = Visibility.VISIBLE;
                    double falloff = 1.0 - Math.sqrt(distSq) / (radius + 0.5);
                    lightLevel[ay][ax] = Math.max(lightLevel[ay][ax], 0.35 + 0.65 * falloff);
                }

                boolean isOpaque = !inBounds(ax, ay) || tiles[ay][ax].blocksLight;
                if (blocked) {
                    if (isOpaque) {
                        nextStartSlope = rSlope;
                    } else {
                        blocked = false;
                        startSlope = nextStartSlope;
                    }
                } else if (isOpaque && i < radius) {
                    blocked = true;
                    castLight(ox, oy, radius, i + 1, startSlope, lSlope, octant);
                    nextStartSlope = rSlope;
                }
            }
            if (blocked) {
                break;
            }
        }
    }

    private static int[] transformOctant(int dx, int dy, int octant) {
        return switch (octant) {
            case 0 -> new int[]{dx, dy};
            case 1 -> new int[]{dy, dx};
            case 2 -> new int[]{-dy, dx};
            case 3 -> new int[]{-dx, dy};
            case 4 -> new int[]{-dx, -dy};
            case 5 -> new int[]{-dy, -dx};
            case 6 -> new int[]{dy, -dx};
            case 7 -> new int[]{dx, -dy};
            default -> new int[]{dx, dy};
        };
    }
}
