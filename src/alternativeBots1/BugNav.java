package alternativeBots1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class BugNav {

    private final RobotController rc;
    private MapLocation target;

    private boolean walling = false;
    private boolean goRight = true;
    private Direction wallDir = null;
    private int wallTurns = 0;
    private int bestDist = Integer.MAX_VALUE;
    private static final int MAX_WALL = 24;

    public BugNav(RobotController rc) { this.rc = rc; }
    
    public void setTarget(MapLocation t) {
        if (t == null) return;
        if (target == null || !target.equals(t)) {
            target = t;
            resetWall();
        }
    }

    public MapLocation getTarget() { return target; }

    private void resetWall() {
        walling = false;
        wallDir = null;
        wallTurns = 0;
        bestDist = Integer.MAX_VALUE;
    }

    public boolean move() throws GameActionException {
        if (target == null || !rc.isMovementReady()) return false;
        MapLocation me = rc.getLocation();
        if (me.equals(target)) { resetWall(); return false; }

        Direction greedy = me.directionTo(target);

        if (!walling) {
            if (rc.canMove(greedy)) { rc.move(greedy); return true; }
            Direction l = greedy.rotateLeft();
            Direction r = greedy.rotateRight();
            if (rc.canMove(l)) { rc.move(l); return true; }
            if (rc.canMove(r)) { rc.move(r); return true; }

            walling = true;
            wallDir = greedy;
            wallTurns = 0;
            bestDist = me.distanceSquaredTo(target);
        }

        wallTurns++;
        if (wallTurns > MAX_WALL) { resetWall(); return false; }

        if (rc.canMove(greedy)) {
            int nd = me.add(greedy).distanceSquaredTo(target);
            if (nd < bestDist) { rc.move(greedy); resetWall(); return true; }
        }

        Direction d = wallDir;
        for (int i = 0; i < 8; i++) {
            d = goRight ? d.rotateLeft() : d.rotateRight();
            if (rc.canMove(d)) {
                rc.move(d);
                wallDir = goRight ? d.rotateRight().rotateRight()
                                  : d.rotateLeft().rotateLeft();
                int dist = rc.getLocation().distanceSquaredTo(target);
                if (dist < bestDist) bestDist = dist;
                return true;
            }
        }
        return false; 
    }
}
