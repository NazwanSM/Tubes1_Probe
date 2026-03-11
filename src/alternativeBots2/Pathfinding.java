package alternativeBots2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Pathfinding {
    private final RobotController rc;
    private final MapData mapData;

    private boolean bugging = false;
    private MapLocation bugTarget = null;
    private Direction bugDir = null;
    private int bugTurnCount = 0;
    private boolean bugTurningLeft = true;
    private int bugStartDist = Integer.MAX_VALUE;

    public Pathfinding(RobotController rc, MapData mapData) {
        this.rc = rc;
        this.mapData = mapData;
    }

    public boolean moveTo(MapLocation target) throws GameActionException {
        if (target == null) return false;
        MapLocation myLoc = rc.getLocation();

        if (myLoc.equals(target)) return false;

        if (!target.equals(bugTarget)) {
            bugging = false;
            bugTarget = target;
            bugTurnCount = 0;
        }

        if (!bugging) {
            Direction dir = myLoc.directionTo(target);
            if (rc.canMove(dir)) {
                rc.move(dir);
                return true;
            }
            Direction left = dir.rotateLeft();
            Direction right = dir.rotateRight();
            if (rc.canMove(left)) {
                rc.move(left);
                return true;
            }
            if (rc.canMove(right)) {
                rc.move(right);
                return true;
            }

            MapLocation blocked = myLoc.add(dir);
            if (rc.canSenseLocation(blocked) && rc.senseMapInfo(blocked).isPassable()) {
                Direction farLeft = dir.rotateLeft().rotateLeft();
                Direction farRight = dir.rotateRight().rotateRight();
                if (rc.canMove(farLeft)) { rc.move(farLeft); return true; }
                if (rc.canMove(farRight)) { rc.move(farRight); return true; }
                return false; 
            }

            bugging = true;
            bugDir = dir;
            bugStartDist = myLoc.distanceSquaredTo(target);
            bugTurnCount = 0;
            bugTurningLeft = (rc.getID() % 2 == 0);
        }

        if (bugging) {
            bugTurnCount++;

            int curDist = myLoc.distanceSquaredTo(target);
            if (bugTurnCount > 2 && curDist < bugStartDist) {
                Direction dir = myLoc.directionTo(target);
                if (rc.canMove(dir)) {
                    bugging = false;
                    rc.move(dir);
                    return true;
                }
                Direction left = dir.rotateLeft();
                Direction right = dir.rotateRight();
                if (rc.canMove(left)) {
                    bugging = false;
                    rc.move(left);
                    return true;
                }
                if (rc.canMove(right)) {
                    bugging = false;
                    rc.move(right);
                    return true;
                }
            }

            if (bugTurnCount > 40) {
                bugging = false;
                return false;
            }

            Direction moveDir = bugDir;
            for (int i = 0; i < 8; i++) {
                if (rc.canMove(moveDir)) {
                    rc.move(moveDir);
                    bugDir = bugTurningLeft ? moveDir.rotateRight().rotateRight()
                                            : moveDir.rotateLeft().rotateLeft();
                    return true;
                }
                moveDir = bugTurningLeft ? moveDir.rotateLeft() : moveDir.rotateRight();
            }
        }

        return false;
    }

    public void reset() {
        bugging = false;
        bugTarget = null;
        bugTurnCount = 0;
    }
}
