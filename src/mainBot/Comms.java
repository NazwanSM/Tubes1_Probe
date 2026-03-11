package mainBot;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Comms {

    public static final int MSG_BATTLEFRONT = 1;

    public static int encodeBattlefront(MapLocation loc) {
        return (MSG_BATTLEFRONT << 24) | (loc.x << 12) | loc.y;
    }
    public static int msgType(int m) {
        return (m >> 24) & 0xFF; 
    }
    public static int decodeX(int m) { 
        return (m >> 12) & 0xFFF; 
    }
    public static int decodeY(int m) {
        return m & 0xFFF; 
    }

    public static void reportBattlefront(RobotController rc, MapLocation enemyLoc) throws GameActionException {
        int enc = encodeBattlefront(enemyLoc);
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (int i = allies.length; --i >= 0;) {
            if (allies[i].getType().isTowerType()) {
                MapLocation tl = allies[i].getLocation();
                if (rc.canSendMessage(tl, enc)) {
                    rc.sendMessage(tl, enc);
                    return;
                }
            }
        }
    }

    public static MapLocation relayAndGetBattlefront(RobotController rc) throws GameActionException {
        MapLocation result = null;
        Message[] msgs = rc.readMessages(-1);
        for (int i = msgs.length; --i >= 0;) {
            int d = msgs[i].getBytes();
            if (msgType(d) == MSG_BATTLEFRONT) {
                result = new MapLocation(decodeX(d), decodeY(d));
                if (rc.canBroadcastMessage()) {
                    rc.broadcastMessage(d);
                }
                break;
            }
        }
        return result;
    }

    public static void sendBattlefrontToUnit(RobotController rc,MapLocation unitLoc, MapLocation battlefront) throws GameActionException {
        if (battlefront == null) return;
        int enc = encodeBattlefront(battlefront);
        if (rc.canSendMessage(unitLoc, enc)) {
            rc.sendMessage(unitLoc, enc);
        }
    }

    public static MapLocation readBattlefront(RobotController rc) throws GameActionException {
        Message[] msgs = rc.readMessages(-1);
        for (int i = msgs.length; --i >= 0;) {
            int d = msgs[i].getBytes();
            if (msgType(d) == MSG_BATTLEFRONT) {
                return new MapLocation(decodeX(d), decodeY(d));
            }
        }
        return null;
    }
}
