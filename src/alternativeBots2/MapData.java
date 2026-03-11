package alternativeBots2;

import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;

public class MapData {
    private final RobotController rc;
    private final int[][] data;
    private final int mapWidth;
    private final int mapHeight;

    // Bit masks
    public static final int TILE_TYPE_MASK    = 0x3;      
    public static final int TOWER_TYPE_MASK   = 0x1C;      
    public static final int TEAM_MASK         = 0x20;      
    public static final int PAINT_MASK        = 0xC0;      
    public static final int VISITED_MASK      = 0x100;    
    public static final int CONTESTED_MASK    = 0x400000;  

    // Tile types
    public static final int TILE_UNKNOWN = 0;
    public static final int TILE_EMPTY   = 1;
    public static final int TILE_RUIN    = 2;
    public static final int TILE_WALL    = 3;

    // Paint values 
    public static final int PAINT_NONE           = 0;
    public static final int PAINT_ALLY_PRIMARY   = 1 << 6;
    public static final int PAINT_ALLY_SECONDARY = 2 << 6;
    public static final int PAINT_ENEMY          = 3 << 6;

    private static final int PERSISTENT_MASK = VISITED_MASK | CONTESTED_MASK;

    public MapData(RobotController rc) {
        this.rc = rc;
        this.mapWidth = rc.getMapWidth();
        this.mapHeight = rc.getMapHeight();
        this.data = new int[mapWidth][mapHeight];
    }

    public int get(int x, int y) {
        if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) return 0;
        return data[x][y];
    }

    public int get(MapLocation loc) {
        return get(loc.x, loc.y);
    }

    public int getTileType(MapLocation loc) {
        return get(loc) & TILE_TYPE_MASK;
    }

    public int getPaintStatus(MapLocation loc) {
        return get(loc) & PAINT_MASK;
    }

    public boolean isVisited(MapLocation loc) {
        if (loc.x < 0 || loc.x >= mapWidth || loc.y < 0 || loc.y >= mapHeight) return true;
        return (data[loc.x][loc.y] & VISITED_MASK) != 0;
    }

    public boolean isContested(MapLocation loc) {
        return (get(loc) & CONTESTED_MASK) != 0;
    }

    public void setContested(MapLocation loc) {
        if (loc.x >= 0 && loc.x < mapWidth && loc.y >= 0 && loc.y < mapHeight) {
            data[loc.x][loc.y] |= CONTESTED_MASK;
        }
    }

    public void setVisited(MapLocation loc) {
        if (loc.x >= 0 && loc.x < mapWidth && loc.y >= 0 && loc.y < mapHeight) {
            data[loc.x][loc.y] |= VISITED_MASK;
        }
    }

    private int encodeTile(MapInfo info) {
        int val = 0;

        if (info.hasRuin()) {
            val |= TILE_RUIN;
        } else if (info.isWall()) {
            val |= TILE_WALL;
        } else {
            val |= TILE_EMPTY;
        }

        PaintType paint = info.getPaint();
        if (paint == PaintType.ALLY_PRIMARY) {
            val |= PAINT_ALLY_PRIMARY;
        } else if (paint == PaintType.ALLY_SECONDARY) {
            val |= PAINT_ALLY_SECONDARY;
        } else if (paint.isEnemy()) {
            val |= PAINT_ENEMY;
        }

        return val;
    }

    public void updateAllVisible() {
        try {
            MapInfo[] visible = rc.senseNearbyMapInfos();
            MapLocation myLoc = rc.getLocation();
            for (MapInfo info : visible) {
                MapLocation loc = info.getMapLocation();
                int prev = data[loc.x][loc.y];
                data[loc.x][loc.y] = encodeTile(info) | (prev & PERSISTENT_MASK);
            }
            data[myLoc.x][myLoc.y] |= VISITED_MASK;
        } catch (Exception e) {
        }
    }

    public void updateNewlyVisible() {
        try {
            MapInfo[] visible = rc.senseNearbyMapInfos();
            MapLocation myLoc = rc.getLocation();
            for (MapInfo info : visible) {
                MapLocation loc = info.getMapLocation();
                int prev = data[loc.x][loc.y];
                data[loc.x][loc.y] = encodeTile(info) | (prev & PERSISTENT_MASK);
            }
            data[myLoc.x][myLoc.y] |= VISITED_MASK;
        } catch (Exception e) {
        }
    }

    public int getMapWidth()  { return mapWidth; }
    public int getMapHeight() { return mapHeight; }
}
