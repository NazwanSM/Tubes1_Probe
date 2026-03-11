package alternativeBots1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class Tower {

    private final RobotController rc;
    private int turnsAlive = 0;
    private int unitsSpawned = 0;
    private final boolean isStartingTower;

    private MapLocation lastBattlefront = null;

    private static final int OPENING_SOLDIERS = 2;
    private static final int DISINTEGRATE_TURN = 50;

    public Tower(RobotController rc) {
        this.rc = rc;
        this.isStartingTower = (rc.getRoundNum() <= 1);
    }

    public void run() throws GameActionException {
        turnsAlive++;

        MapLocation bf = Comms.relayAndGetBattlefront(rc);
        if (bf != null) lastBattlefront = bf;

        if (!isStartingTower && turnsAlive >= DISINTEGRATE_TURN) {
            rc.disintegrate();
            return;
        }

        attackEnemies();

        spawnUnits();
    }

    private void attackEnemies() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return;

        RobotInfo weakest = null;
        for (int i = enemies.length; --i >= 0;) {
            if (weakest == null || enemies[i].getHealth() < weakest.getHealth())
                weakest = enemies[i];
        }
        if (weakest != null && rc.canAttack(weakest.getLocation())) {
            rc.attack(weakest.getLocation());
        }
    }

    private void spawnUnits() throws GameActionException {
        Direction[] dirs = Direction.allDirections();

        if (isStartingTower && unitsSpawned < OPENING_SOLDIERS) {
            for (Direction d : dirs) {
                MapLocation loc = rc.getLocation().add(d);
                if (rc.canBuildRobot(UnitType.SOLDIER, loc)) {
                    rc.buildRobot(UnitType.SOLDIER, loc);
                    unitsSpawned++;
                    Comms.sendBattlefrontToUnit(rc, loc, lastBattlefront);
                    return;
                }
            }
            return;
        }

        boolean enemyNearby = rc.senseNearbyRobots(-1, rc.getTeam().opponent()).length > 0;
        int chips = rc.getChips();

        UnitType toSpawn;
        if (enemyNearby && unitsSpawned % 3 == 0) {
            toSpawn = UnitType.MOPPER;
        } else if (chips >= 1200 && unitsSpawned % 4 == 0) {
            toSpawn = UnitType.SPLASHER;
        } else {
            toSpawn = UnitType.SOLDIER;
        }

        for (Direction d : dirs) {
            MapLocation loc = rc.getLocation().add(d);
            if (rc.canBuildRobot(toSpawn, loc)) {
                rc.buildRobot(toSpawn, loc);
                unitsSpawned++;
                Comms.sendBattlefrontToUnit(rc, loc, lastBattlefront);
                return;
            }
        }

        if (toSpawn != UnitType.SOLDIER) {
            for (Direction d : dirs) {
                MapLocation loc = rc.getLocation().add(d);
                if (rc.canBuildRobot(UnitType.SOLDIER, loc)) {
                    rc.buildRobot(UnitType.SOLDIER, loc);
                    unitsSpawned++;
                    Comms.sendBattlefrontToUnit(rc, loc, lastBattlefront);
                    return;
                }
            }
        }
    }
}
