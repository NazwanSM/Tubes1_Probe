package alternativeBots2;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Communication {
    private final RobotController rc;
    public static final int MSG_BATTLEFRONT    = 0;
    public static final int MSG_RUIN_CLAIMED   = 1;
    public static final int MSG_SYMMETRY_FOUND = 2;
    public static final int MSG_ENEMY_TOWER    = 3;
    public static final int MSG_NEED_MOPPER    = 4;
    public static final int MSG_RUIN_FOUND     = 5;

    public Communication(RobotController rc) {
        this.rc = rc;
    }

    // Encoding
    public static int encode(int x, int y, int msgType) {
        return (x & 0x3F) | ((y & 0x3F) << 6) | ((msgType & 0x7) << 12);
    }

    public static int encode(int x, int y, int msgType, int payload) {
        return (x & 0x3F) | ((y & 0x3F) << 6) | ((msgType & 0x7) << 12) | ((payload & 0x1FFFF) << 15);
    }

    public static int encodeLocation(MapLocation loc, int msgType) {
        return encode(loc.x, loc.y, msgType);
    }

    // Decoding
    public static int decodeX(int msg)       { return msg & 0x3F; }
    public static int decodeY(int msg)       { return (msg >> 6) & 0x3F; }
    public static int decodeType(int msg)    { return (msg >> 12) & 0x7; }
    public static int decodePayload(int msg) { return (msg >> 15) & 0x1FFFF; }

    public static MapLocation decodeLocation(int msg) {
        return new MapLocation(decodeX(msg), decodeY(msg));
    }

    public void broadcastToNearbyTowers(int msg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType() && rc.canSendMessage(ally.location, msg)) {
                rc.sendMessage(ally.location, msg);
            }
        }
    }

    public void broadcastToNearbyRobots(int msg) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type.isRobotType() && rc.canSendMessage(ally.location, msg)) {
                rc.sendMessage(ally.location, msg);
            }
        }
    }

    public void sendTo(MapLocation loc, int msg) throws GameActionException {
        if (rc.canSendMessage(loc, msg)) {
            rc.sendMessage(loc, msg);
        }
    }

    public void towerBroadcast(int msg) throws GameActionException {
        if (rc.canBroadcastMessage()) {
            rc.broadcastMessage(msg);
        }
    }

    public static MapLocation getRotational(int x, int y, int mapWidth, int mapHeight) {
        return new MapLocation(mapWidth - 1 - x, mapHeight - 1 - y);
    }

    public static MapLocation getReflectX(int x, int y, int mapWidth) {
        return new MapLocation(mapWidth - 1 - x, y);
    }

    public static MapLocation getReflectY(int x, int y, int mapHeight) {
        return new MapLocation(x, mapHeight - 1 - y);
    }
}
