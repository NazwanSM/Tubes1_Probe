package alternative_bots_2;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public class Combat {
    public static MapLocation selectSoldierTarget(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() >= 10) return null;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;

        MapLocation myLoc = rc.getLocation();
        MapLocation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.location)) continue;

            double score = (enemy.type.isTowerType() ? 100.0 : 0.0)
                         + (50.0 / Math.max(1, enemy.health))
                         - (myLoc.distanceSquaredTo(enemy.location) * 2.0);

            if (score > bestScore) {
                bestScore = score;
                best = enemy.location;
            }
        }
        return best;
    }

    public static Direction selectMopperSwingDirection(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() >= 10) return null;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;

        MapLocation myLoc = rc.getLocation();
        Direction bestDir = null;
        int bestHits = 0;

        for (Direction dir : Direction.values()) {
            if (dir == Direction.CENTER) continue;
            if (!rc.canMopSwing(dir)) continue;

            int hits = 0;
            for (RobotInfo enemy : enemies) {
                int dx = enemy.location.x - myLoc.x;
                int dy = enemy.location.y - myLoc.y;
                if (isInSwingArea(dir, dx, dy)) hits++;
            }

            if (hits > bestHits) {
                bestHits = hits;
                bestDir = dir;
            }
        }

        if (bestDir == null) {
            for (Direction dir : Direction.values()) {
                if (dir == Direction.CENTER) continue;
                if (rc.canMopSwing(dir)) return dir;
            }
        }

        return bestDir;
    }

    public static MapLocation selectSplasherTarget(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() >= 10) return null;

        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestCenter = null;
        int bestScore = 0;

        for (MapInfo tile : nearby) {
            MapLocation center = tile.getMapLocation();
            if (!rc.canAttack(center)) continue;

            int score = 0;

            RobotInfo[] enemiesNear = rc.senseNearbyRobots(center, 2, rc.getTeam().opponent());
            score += enemiesNear.length;

            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx * dx + dy * dy > 4) continue;
                    MapLocation tLoc = center.translate(dx, dy);
                    if (!rc.canSenseLocation(tLoc)) continue;
                    MapInfo tInfo = rc.senseMapInfo(tLoc);
                    if (tInfo.getPaint().isEnemy()) score++;
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestCenter = center;
            }
        }

        return bestCenter;
    }

    public static MapLocation selectTowerTarget(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() >= 10) return null;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;

        MapLocation best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (RobotInfo enemy : enemies) {
            if (!rc.canAttack(enemy.location)) continue;
            double score = 100.0 / Math.max(1, enemy.health);
            if (score > bestScore) {
                bestScore = score;
                best = enemy.location;
            }
        }
        return best;
    }

    public static boolean isInSwingArea(Direction dir, int dx, int dy) {
        int dist = dx * dx + dy * dy;
        if (dist > 5 || dist == 0) return false;
        switch (dir) {
            case NORTH:     return dy > 0 && Math.abs(dx) <= 1;
            case SOUTH:     return dy < 0 && Math.abs(dx) <= 1;
            case EAST:      return dx > 0 && Math.abs(dy) <= 1;
            case WEST:      return dx < 0 && Math.abs(dy) <= 1;
            case NORTHEAST: return dx > 0 && dy > 0;
            case NORTHWEST: return dx < 0 && dy > 0;
            case SOUTHEAST: return dx > 0 && dy < 0;
            case SOUTHWEST: return dx < 0 && dy < 0;
            default: return false;
        }
    }
}
