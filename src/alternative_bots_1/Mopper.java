package alternative_bots_1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Mopper extends BaseRobot {

    private static final double REFILL_THRESHOLD = 0.20;

    public Mopper(RobotController rc) {
        super(rc, Task.EXPLORE);
    }

    @Override
    protected void executeTurn() throws GameActionException {
        checkPaintLevel();

        switch (goals.currentTask()) {
            case REFILL:      executeRefill();     break;
            case RUSH_ENEMY:  executeRushEnemy();  break;
            case EXPLORE:
            default:          executeExplore();    break;
        }

        reportEnemies();
    }

    private void checkPaintLevel() {
        int cap = rc.getType().paintCapacity;
        int cur = rc.getPaint();
        if (cur < (int)(cap * REFILL_THRESHOLD) && !goals.contains(Task.REFILL)) {
            goals.push(Task.REFILL);
        }
    }

    private void executeRushEnemy() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) { goals.pop(); return; }

        MapLocation me = rc.getLocation();

        RobotInfo closestMobile = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = enemies.length; --i >= 0;) {
            if (!enemies[i].getType().isTowerType()) {
                int d = me.distanceSquaredTo(enemies[i].getLocation());
                if (d < bestDist) { bestDist = d; closestMobile = enemies[i]; }
            }
        }

        if (closestMobile != null) {
            Direction dir = me.directionTo(closestMobile.getLocation());
            if (rc.canMopSwing(dir)) {
                rc.mopSwing(dir);
            } else if (rc.canAttack(closestMobile.getLocation())) {
                rc.attack(closestMobile.getLocation());
            }
            nav.setTarget(closestMobile.getLocation());
            nav.move();
        } else {
            goals.pop();
        }
    }

    private void executeExplore() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            goals.push(Task.RUSH_ENEMY);
            return;
        }

        boolean mopped = tryMopNearbyEnemyPaint();

        MapLocation bf = Comms.readBattlefront(rc);
        if (bf != null) battlefront = bf;

        if (battlefront != null) {
            nav.setTarget(battlefront);
            if (!nav.move()) {
                if (rc.getLocation().distanceSquaredTo(battlefront) <= 4)
                    battlefront = null;
                tryRandomMove();
            }
        } else {
            explore();
        }
    }

    private boolean tryMopNearbyEnemyPaint() throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int i = nearby.length; --i >= 0;) {
            if (nearby[i].getPaint().isEnemy()) {
                MapLocation loc = nearby[i].getMapLocation();
                int d = me.distanceSquaredTo(loc);
                if (d < bestDist && rc.canAttack(loc)) {
                    bestDist = d;
                    best = loc;
                }
            }
        }
        if (best != null) {
            rc.attack(best);
            return true;
        }
        return false;
    }
}
