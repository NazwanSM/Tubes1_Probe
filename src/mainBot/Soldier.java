package mainBot;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Soldier extends BaseRobot {

    private MapLocation targetRuin = null;

    private static final double BUILD_REFILL_THRESHOLD = 0.25;

    public Soldier(RobotController rc) {
        super(rc, Task.EXPLORE);
    }

    @Override
    protected void executeTurn() throws GameActionException {

        if (goals.currentTask() == Task.BUILD_RUIN) {
            int cap  = rc.getType().paintCapacity;
            int cur  = rc.getPaint();
            if (cur < (int)(cap * BUILD_REFILL_THRESHOLD) && !goals.contains(Task.REFILL)) {
                goals.push(Task.REFILL);
            }
        }

        if (goals.currentTask() != Task.REFILL) {
            scanAndUpdateGoals();
        }

        switch (goals.currentTask()) {
            case BUILD_RUIN:  executeBuildRuin();  break;
            case RUSH_ENEMY:  executeRushEnemy();  break;
            case REFILL:      executeRefill();     break;
            case EXPLORE:
            default:          executeExplore();    break;
        }

        paintUnderfoot();

        reportEnemies();
    }

    private void scanAndUpdateGoals() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapLocation ruin = findNearestRuin();

        if (ruin != null) {
            boolean contested = false;
            for (int i = enemies.length; --i >= 0;) {
                if (enemies[i].getLocation().distanceSquaredTo(ruin) <= 20) {
                    contested = true;
                    break;
                }
            }
            if (contested) {
                goals.push(Task.RUSH_ENEMY);
            } else {
                targetRuin = ruin;
                goals.push(Task.BUILD_RUIN);
            }
        } else if (enemies.length > 0) {
            goals.push(Task.RUSH_ENEMY);
        }
    }

    private void executeBuildRuin() throws GameActionException {
        if (targetRuin == null) { goals.pop(); return; }

        if (rc.canSenseLocation(targetRuin)) {
            RobotInfo occ = rc.senseRobotAtLocation(targetRuin);
            if (occ != null) {
                targetRuin = null;
                goals.pop();
                return;
            }
        }

        nav.setTarget(targetRuin);
        nav.move();

        if (rc.canMarkTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetRuin)) {
            rc.markTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetRuin);
        }

        MapLocation me = rc.getLocation();
        if (me.distanceSquaredTo(targetRuin) <= 8) {
            MapInfo[] tiles = rc.senseNearbyMapInfos(targetRuin, 8);
            for (int i = tiles.length; --i >= 0;) {
                MapInfo t = tiles[i];
                PaintType mark  = t.getMark();
                PaintType paint = t.getPaint();
                if (mark != PaintType.EMPTY && mark != paint) {
                    boolean secondary = (mark == PaintType.ALLY_SECONDARY);
                    if (rc.canAttack(t.getMapLocation())) {
                        rc.attack(t.getMapLocation(), secondary);
                        break;
                    }
                }
            }
        }

        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetRuin)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetRuin);
            targetRuin = null;
            goals.pop();
        }
    }

    private void executeRushEnemy() throws GameActionException {
        RobotInfo target = pickBestTarget();
        if (target == null) { goals.pop(); return; }

        MapLocation tLoc = target.getLocation();

        if (rc.canAttack(tLoc)) {
            rc.attack(tLoc);
        }

        nav.setTarget(tLoc);
        nav.move();

        RobotInfo target2 = pickBestTarget();
        if (target2 != null && rc.canAttack(target2.getLocation())) {
            rc.attack(target2.getLocation());
        }
    }

    private void executeExplore() throws GameActionException {
        MapLocation bf = Comms.readBattlefront(rc);
        if (bf != null) battlefront = bf;

        if (battlefront != null) {
            nav.setTarget(battlefront);
            if (!nav.move()) {
                if (rc.getLocation().distanceSquaredTo(battlefront) <= 4) {
                    battlefront = null;
                }
                tryRandomMove();
            }
        } else {
            explore();
        }
    }
}
