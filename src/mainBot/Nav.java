package mainBot;

import battlecode.common.*;
import java.util.Random;

public class Nav {

    static final Direction[] dirs = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    static boolean moveTo(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) return false;
        if (!rc.isMovementReady()) return false;

        Direction dir = rc.getLocation().directionTo(target);
        if (dir == Direction.CENTER) return false;

        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }
        Direction left = dir.rotateLeft();
        if (rc.canMove(left)) {
            rc.move(left);
            return true;
        }
        Direction right = dir.rotateRight();
        if (rc.canMove(right)) {
            rc.move(right);
            return true;
        }
        Direction left2 = left.rotateLeft();
        if (rc.canMove(left2)) {
            rc.move(left2);
            return true;
        }
        Direction right2 = right.rotateRight();
        if (rc.canMove(right2)) {
            rc.move(right2);
            return true;
        }
        return false;
    }

    static boolean randomMove(RobotController rc, Random rng) throws GameActionException {
        if (!rc.isMovementReady()) return false;

        Direction dir = dirs[rng.nextInt(8)];
        if (rc.canMove(dir)) {
            rc.move(dir);
            return true;
        }

        for (Direction d : dirs) {
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false; 
    }

    static MapLocation findNearestEmptyRuin(RobotController rc, MapLocation[] ruins) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (MapLocation ruin : ruins) {
            if (rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo occ = rc.senseRobotAtLocation(ruin);
                if (occ.getType().isTowerType()) continue;
            }

            int dist = myLoc.distanceSquaredTo(ruin);
            if (dist < minDist) {
                minDist = dist;
                nearest = ruin;
            }
        }
        return nearest;
    }

    static MapLocation findNearestAllyTower(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.getType().isTowerType()) {
                int dist = myLoc.distanceSquaredTo(ally.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = ally.getLocation();
                }
            }
        }
        return nearest;
    }
}
