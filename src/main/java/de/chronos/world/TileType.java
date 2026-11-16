package de.chronos.world;

/**
 * Tile-Typen für das prozedural generierte Dungeon-Gitter.
 */
public enum TileType {
    VOID(true, true),
    WALL(true, true),
    FLOOR(false, false),
    CORRIDOR(false, false),
    SPIKE_TRAP(false, false),
    PILLAR(true, true);

    public final boolean solid;
    public final boolean blocksLight;

    TileType(boolean solid, boolean blocksLight) {
        this.solid = solid;
        this.blocksLight = blocksLight;
    }
}
