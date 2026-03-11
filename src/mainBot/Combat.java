package mainBot;
import battlecode.common.*;

public class Combat {

    static void towerAttack(RobotController rc) throws GameActionException {
        if (!rc.isActionReady()) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        RobotInfo bestTarget = null;
        int lowestHP = Integer.MAX_VALUE;

        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.getLocation())) {
                if (enemy.getHealth() < lowestHP) {
                    lowestHP = enemy.getHealth();
                    bestTarget = enemy;
                }
            }
        }

        if (bestTarget != null) {
            rc.attack(bestTarget.getLocation());
        }
    }
}
