package alternativeBots1;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Splasher extends BaseRobot {

    private static final double REFILL_THRESHOLD = 0.20;

    public Splasher(RobotController rc) {
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
        RobotInfo closest = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = enemies.length; --i >= 0;) {
            int d = me.distanceSquaredTo(enemies[i].getLocation());
            if (d < bestDist) { bestDist = d; closest = enemies[i]; }
        }

        MapLocation atkTarget = findBestSplashTarget();
        if (atkTarget != null && rc.canAttack(atkTarget)) {
            rc.attack(atkTarget);
        } else if (closest != null && rc.canAttack(closest.getLocation())) {
            rc.attack(closest.getLocation());
        }

        if (closest != null) {
            nav.setTarget(closest.getLocation());
            nav.move();
        }
    }

    private void executeExplore() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            goals.push(Task.RUSH_ENEMY);
            return;
        }

        MapLocation bf = Comms.readBattlefront(rc);
        if (bf != null) battlefront = bf;

        MapLocation splash = findBestSplashTarget();
        if (splash != null && rc.canAttack(splash)) {
            rc.attack(splash);
        }

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

    private MapLocation findBestSplashTarget() throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int bestScore = 0;

        for (int i = nearby.length; --i >= 0;) {
            MapInfo info = nearby[i];
            if (info.isWall() || info.hasRuin()) continue;
            MapLocation loc = info.getMapLocation();
            if (!rc.canAttack(loc)) continue;

            PaintType p = info.getPaint();
            int score = p.isEnemy() ? 3 : (p.isAlly() ? 0 : 1);
            if (score > bestScore) { bestScore = score; best = loc; }
        }
        return (bestScore > 0) ? best : null;
    }
}
