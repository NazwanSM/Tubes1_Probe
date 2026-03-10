package alternativeBots1;

import battlecode.common.*;

public class Heuristics {

    static final int ACT_NOTHING = 0;
    static final int ACT_MOVE = 1;
    static final int ACT_PAINT = 2;
    static final int ACT_MARK_TOWER = 3;
    static final int ACT_COMPLETE_TOWER = 4;
    static final int ACT_MOP = 5;
    static final int ACT_MOP_SWING = 6;
    static final int ACT_TRANSFER_PAINT = 7;
    static final int ACT_COMPLETE_RESOURCE = 8;
    static final int ACT_MARK_RESOURCE = 9;
    static final int ACT_WITHDRAW_PAINT = 10;
    static final int ACT_UPGRADE_TOWER = 11;

    // skor tower building
    static final int SCORE_COMPLETE_MONEY_TOWER = 10000;
    static final int SCORE_COMPLETE_PAINT_TOWER = 8000;
    static final int SCORE_COMPLETE_DEFENSE_TOWER = 6000;
    static final int SCORE_PAINT_PATTERN = 3000;
    static final int SCORE_MARK_MONEY_TOWER = 2500;
    static final int SCORE_MARK_PAINT_TOWER = 2000;

    // skor movement
    static final int SCORE_PAINT_SELF = 100;
    static final int SCORE_EXPLORE = 30;
    static final int SCORE_MOVE_TO_RUIN_BONUS = 200;
    static final int SCORE_MOVE_TO_CENTER = 40;

    // penalty
    static final int PENALTY_ENEMY_PAINT = -500;
    static final int PENALTY_LOW_PAINT_MOVE = -3;

    // tower spawn scores
    static final int SCORE_SPAWN_SOLDIER_EARLY = 800;
    static final int SCORE_SPAWN_SOLDIER_LATE = 200;
    static final int SCORE_SPAWN_SPLASHER_EARLY = 100;
    static final int SCORE_SPAWN_SPLASHER_LATE = 900;
    static final int SCORE_SPAWN_MOPPER = 350;

    // splasher
    static final int SCORE_RUSH_ENEMY = 500;
    static final int SCORE_SPLASH_ENEMY_TILE = 80;
    static final int SCORE_SPLASH_EMPTY_TILE = 40;
    static final int PENALTY_SPLASH_ALLY = -20;
    static final int SCORE_SPLASH_NEAR_ENEMY_TOWER = 500;

    // mopper
    static final int SCORE_MOP_ENEMY_PAINT = 800;
    static final int SCORE_MOP_SWING_BASE = 400;
    static final int SCORE_MOP_SWING_PER_ENEMY = 200;
    static final int SCORE_TRANSFER_PAINT = 500;
    static final int SCORE_TRANSFER_NO_ENEMY_BONUS = 300;
    static final int SCORE_MOVE_TO_ENEMY_PAINT = 200;
    static final int MOPPER_LOW_PAINT = 20;

    // resource pattern
    static final int SCORE_COMPLETE_RESOURCE = 1500;
    static final int SCORE_MARK_RESOURCE = 800;

    // withdraw paint dari tower
    static final int SCORE_WITHDRAW_PAINT = 5000;

    // upgrade tower
    static final int SCORE_UPGRADE_TOWER = 4000;

    // threshold
    static final int LOW_PAINT_THRESHOLD = 50;
    static final int SPLASHER_LOW_PAINT = 50;
    static final int TOWER_PAINT_RESERVE = 300;
    static final int MID_GAME_TURN = 100;

    static int scoreTowerSpawn(RobotController rc, UnitType unit, int round) {
        boolean earlyGame = round < MID_GAME_TURN;
        int towerId = rc.getLocation().x + rc.getLocation().y;

        boolean needMopper = ((round + towerId) % 30 == 0);
        boolean needSoldierLate = (!earlyGame && ((round + towerId) % 50 == 0));

        if (unit == UnitType.MOPPER) {
            return needMopper ? 2000 : SCORE_SPAWN_MOPPER;
        }

        if (unit == UnitType.SOLDIER) {
            if (earlyGame) {
                return SCORE_SPAWN_SOLDIER_EARLY;
            } else {
                return needSoldierLate ? 2000 : SCORE_SPAWN_SOLDIER_LATE; 
            }
        } else if (unit == UnitType.SPLASHER) {
            return earlyGame ? SCORE_SPAWN_SPLASHER_EARLY : SCORE_SPAWN_SPLASHER_LATE;
        }
        
        return 0;
    }

    static UnitType getTowerTypeForRuin(MapLocation ruin) {
        if ((ruin.x + ruin.y) % 3 == 0) {
            return UnitType.LEVEL_ONE_PAINT_TOWER;
        }
        return UnitType.LEVEL_ONE_MONEY_TOWER;
    }

    static int scoreCompleteTower(UnitType towerType) {
        switch (towerType) {
            case LEVEL_ONE_MONEY_TOWER: return SCORE_COMPLETE_MONEY_TOWER;
            case LEVEL_ONE_PAINT_TOWER: return SCORE_COMPLETE_PAINT_TOWER;
            case LEVEL_ONE_DEFENSE_TOWER: return SCORE_COMPLETE_DEFENSE_TOWER;
            default: return SCORE_COMPLETE_PAINT_TOWER;
        }
    }

    static int scoreMarkTower(UnitType towerType) {
        switch (towerType) {
            case LEVEL_ONE_MONEY_TOWER: return SCORE_MARK_MONEY_TOWER;
            case LEVEL_ONE_PAINT_TOWER: return SCORE_MARK_PAINT_TOWER;
            default: return SCORE_MARK_PAINT_TOWER;
        }
    }

    static int scorePaintPattern() {
        return SCORE_PAINT_PATTERN;
    }

    static int scoreSoldierMove(RobotController rc, Direction dir, 
            MapLocation nearestRuin, MapLocation nearestTower, int paint,
            MapLocation attackTarget) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation target = myLoc.add(dir);
        int score = SCORE_EXPLORE;

        if (nearestRuin != null) {
            int currDist = myLoc.distanceSquaredTo(nearestRuin);
            int newDist = target.distanceSquaredTo(nearestRuin);
            if (newDist < currDist) {
                score += SCORE_MOVE_TO_RUIN_BONUS;
            }
        } else if (attackTarget != null) {
            int currDist = myLoc.distanceSquaredTo(attackTarget);
            int newDist = target.distanceSquaredTo(attackTarget);
            if (newDist < currDist) {
                score += 150;
            }
        } else {
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            int currDist = myLoc.distanceSquaredTo(center);
            int newDist = target.distanceSquaredTo(center);
            if (newDist < currDist) {
                score += SCORE_MOVE_TO_CENTER;
            }
        }

        if (rc.canSenseLocation(target)) {
            MapInfo tileInfo = rc.senseMapInfo(target);
            PaintType tilePaint = tileInfo.getPaint();

            if (tilePaint.isEnemy()) {
                score += PENALTY_ENEMY_PAINT;
            }
            if (tilePaint == PaintType.EMPTY) {
                score += 100;
            }
            if (tileInfo.isWall()) {
                return -99999;
            }
        }

        if (paint < LOW_PAINT_THRESHOLD && nearestTower != null) {
            int currDist = myLoc.distanceSquaredTo(nearestTower);
            int newDist = target.distanceSquaredTo(nearestTower);
            if (newDist < currDist) {
                score += (LOW_PAINT_THRESHOLD - paint) * 5;
            }
        }

        return score;
    }

    static int scoreSplasherMove(RobotController rc, Direction dir, MapLocation rushTarget) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation target = myLoc.add(dir);
        int score = SCORE_EXPLORE;

        // Rush bonus: arah mendekati target
        if (rushTarget != null) {
            int currDist = myLoc.distanceSquaredTo(rushTarget);
            int newDist = target.distanceSquaredTo(rushTarget);
            if (newDist < currDist) {
                score += SCORE_RUSH_ENEMY;
            }
        }

        // Anti-cluster: hindari ally splasher
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(target, 8, rc.getTeam());
        for (RobotInfo ally : nearbyAllies) {
            if (ally.getType() == UnitType.SPLASHER) {
                score -= 80;
            }
        }

        if (rc.canSenseLocation(target)) {
            MapInfo tileInfo = rc.senseMapInfo(target);
            PaintType tilePaint = tileInfo.getPaint();

            if (tilePaint.isEnemy()) {
                score += 300;
            }
            if (tilePaint == PaintType.EMPTY) {
                score += 50;
            }
            if (tilePaint.isAlly()) {
                score -= 100;
            }
            if (tileInfo.isWall()) {
                return -99999;
            }
        }

        return score;
    }

    static int scoreSplasherAttack(RobotController rc, MapLocation loc) throws GameActionException {
        int score = 0;

        MapInfo[] nearby = rc.senseNearbyMapInfos(loc, 4);
        for (MapInfo tile : nearby) {
            if (tile.isWall() || !tile.isPassable()) continue;
            PaintType paint = tile.getPaint();
            if (paint.isEnemy()) {
                score += SCORE_SPLASH_ENEMY_TILE;
            } else if (paint == PaintType.EMPTY) {
                score += SCORE_SPLASH_EMPTY_TILE;
            } else if (paint.isAlly()) {
                score += PENALTY_SPLASH_ALLY;
            }
        }

        // Bonus besar kalau splash dekat enemy tower
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(loc, 8, rc.getTeam().opponent());
        for (RobotInfo enemy : nearbyRobots) {
            if (enemy.getType().isTowerType()) {
                score += SCORE_SPLASH_NEAR_ENEMY_TOWER;
                break;
            }
        }

        return score;
    }

    /**
     * Evaluasi arah gerak mopper (greedy heuristic).
     * Faktor: enemy paint tiles, ally yg butuh paint, retreat ke tower
     */
    static int scoreMopperMove(RobotController rc, Direction dir,
            MapLocation nearestTower, int paint, boolean hasEnemyPaintNearby) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation target = myLoc.add(dir);
        int score = SCORE_EXPLORE;

        if (rc.canSenseLocation(target)) {
            MapInfo tileInfo = rc.senseMapInfo(target);
            PaintType tilePaint = tileInfo.getPaint();

            if (tilePaint.isEnemy()) {
                score += SCORE_MOVE_TO_ENEMY_PAINT;
            }
            // Ally dan empty tile punya skor sama → tidak ada pull ke empty
            if (tilePaint.isAlly() || tilePaint == PaintType.EMPTY) {
                score -= 30;
            }
            if (tileInfo.isWall()) {
                return -99999;
            }
        }

        // Kalo ga ada enemy paint nearby, deketin ally yg butuh paint (transfer mode)
        if (!hasEnemyPaintNearby) {
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(target, 4, rc.getTeam());
            for (RobotInfo ally : nearbyAllies) {
                if (ally.getType().isRobotType() && ally.getPaintAmount() < 80) {
                    score += 150;
                    break;
                }
            }

            // Eksplorasi: bias ke center map supaya menyebar
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            int currDist = myLoc.distanceSquaredTo(center);
            int newDist = target.distanceSquaredTo(center);
            if (newDist < currDist) {
                score += SCORE_MOVE_TO_CENTER;
            }
        }

        // paint rendah: retreat ke tower ally
        if (paint < MOPPER_LOW_PAINT && nearestTower != null) {
            int currDist = myLoc.distanceSquaredTo(nearestTower);
            int newDist = target.distanceSquaredTo(nearestTower);
            if (newDist < currDist) {
                score += (MOPPER_LOW_PAINT - paint) * 3;
            }
        }

        return score;
    }
}
