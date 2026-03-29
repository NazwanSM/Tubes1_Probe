package alternative_bots_1;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;

public class MapData {

    public static final int TILE_UNKNOWN   = 0;
    public static final int TILE_PASSABLE  = 1;
    public static final int TILE_WALL      = 2;
    public static final int TILE_RUIN      = 3;
    public static final int TILE_TYPE_MASK = 0x3;
    public static final int ALLY_PAINT     = 0x4;
    public static final int ENEMY_PAINT    = 0x8;
    public static final int EXPLORED_BIT   = 0x10;

    private final int[] data;
    private final int mapWidth;

    public MapData(int w, int h) {
        this.mapWidth = w;
        this.data = new int[w * h];
    }

    private int idx(int x, int y) { return y * mapWidth + x; }

    public int  getTileType(int x, int y) { return data[idx(x,y)] & TILE_TYPE_MASK; }
    public boolean isWall(int x, int y)      { return getTileType(x,y) == TILE_WALL; }
    public boolean isRuin(int x, int y)      { return getTileType(x,y) == TILE_RUIN; }
    public boolean hasAllyPaint(int x, int y)  { return (data[idx(x,y)] & ALLY_PAINT)  != 0; }
    public boolean hasEnemyPaint(int x, int y) { return (data[idx(x,y)] & ENEMY_PAINT) != 0; }
    public boolean isExplored(int x, int y)    { return (data[idx(x,y)] & EXPLORED_BIT)!= 0; }

    public void updateNewlyVisible(RobotController rc) throws GameActionException {
        MapInfo[] vis = rc.senseNearbyMapInfos();
        for (int i = vis.length; --i >= 0;) {
            MapInfo info = vis[i];
            MapLocation loc = info.getMapLocation();
            int id = idx(loc.x, loc.y);

            int val = EXPLORED_BIT;
            if      (info.isWall())  val |= TILE_WALL;
            else if (info.hasRuin()) val |= TILE_RUIN;
            else                     val |= TILE_PASSABLE;

            PaintType p = info.getPaint();
            if      (p.isAlly())  val |= ALLY_PAINT;
            else if (p.isEnemy()) val |= ENEMY_PAINT;

            data[id] = val;
        }
    }
}
