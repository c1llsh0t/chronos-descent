package de.chronos.world;

import de.chronos.core.GameConfig;
import de.chronos.math.Vector2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Repräsentiert einen Dungeon-Raum innerhalb des BSP-Baums.
 */
public final class Room {
    public enum RoomType {
        START,
        COMBAT,
        TREASURE,
        SHRINE,
        BOSS
    }

    public final int id;
    public final int tileX;
    public final int tileY;
    public final int width;
    public final int height;
    public RoomType type;
    public boolean discovered = false;
    public boolean cleared = false;
    public final List<Integer> neighbors = new ArrayList<>();

    public Room(int id, int tileX, int tileY, int width, int height, RoomType type) {
        this.id = id;
        this.tileX = tileX;
        this.tileY = tileY;
        this.width = width;
        this.height = height;
        this.type = type;
    }

    public int centerTileX() {
        return tileX + width / 2;
    }

    public int centerTileY() {
        return tileY + height / 2;
    }

    public Vector2D centerWorld() {
        return new Vector2D(
                (centerTileX() + 0.5) * GameConfig.TILE_SIZE,
                (centerTileY() + 0.5) * GameConfig.TILE_SIZE
        );
    }

    public boolean containsTile(int tx, int ty) {
        return tx >= tileX && tx < tileX + width && ty >= tileY && ty < tileY + height;
    }

    public boolean containsWorld(double wx, double wy) {
        int tx = (int) Math.floor(wx / GameConfig.TILE_SIZE);
        int ty = (int) Math.floor(wy / GameConfig.TILE_SIZE);
        return containsTile(tx, ty);
    }
}
