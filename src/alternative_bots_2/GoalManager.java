package alternative_bots_2;

import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

public class GoalManager {
    public static final int GOAL_CAPTURE_RUIN = 10;
    public static final int GOAL_BATTLEFRONT  = 11;
    public static final int GOAL_CONTEST_RUIN = 12;
    public static final int GOAL_RUSH_ENEMY   = 13;
    public static final int GOAL_PAINT_AREA   = 14;
    public static final int GOAL_EXPLORE      = 15;
    public static final int GOAL_PAINT_TILE   = 16;

    // Round thresholds
    public static final int EARLY_END = 250;   
    public static final int MID_END   = 1250;  
                                                
    // Early & Mid (round <= 1250): fokus cari ruin & explore
    private static final int EARLY_PRI_CAPTURE_RUIN = 7;
    private static final int EARLY_PRI_EXPLORE      = 6;
    private static final int EARLY_PRI_CONTEST_RUIN = 5;
    private static final int EARLY_PRI_PAINT_AREA   = 4;
    private static final int EARLY_PRI_BATTLEFRONT  = 3;
    private static final int EARLY_PRI_RUSH_ENEMY   = 2;
    private static final int EARLY_PRI_PAINT_TILE   = 1;

    // Late (round > 1250): fokus combat & push
    private static final int LATE_PRI_BATTLEFRONT   = 7;
    private static final int LATE_PRI_RUSH_ENEMY    = 6;
    private static final int LATE_PRI_CAPTURE_RUIN  = 5;
    private static final int LATE_PRI_CONTEST_RUIN  = 4;
    private static final int LATE_PRI_PAINT_AREA    = 3;
    private static final int LATE_PRI_EXPLORE       = 2;
    private static final int LATE_PRI_PAINT_TILE    = 1;

    private int currentGoal = GOAL_EXPLORE;
    private MapLocation goalTarget = null;

    public GoalManager() {}

    private static int getPriority(int goalType, int roundNum) {
        boolean late = roundNum > MID_END;
        switch (goalType) {
            case GOAL_CAPTURE_RUIN: return late ? LATE_PRI_CAPTURE_RUIN : EARLY_PRI_CAPTURE_RUIN;
            case GOAL_BATTLEFRONT:  return late ? LATE_PRI_BATTLEFRONT  : EARLY_PRI_BATTLEFRONT;
            case GOAL_CONTEST_RUIN: return late ? LATE_PRI_CONTEST_RUIN : EARLY_PRI_CONTEST_RUIN;
            case GOAL_RUSH_ENEMY:   return late ? LATE_PRI_RUSH_ENEMY  : EARLY_PRI_RUSH_ENEMY;
            case GOAL_PAINT_AREA:   return late ? LATE_PRI_PAINT_AREA  : EARLY_PRI_PAINT_AREA;
            case GOAL_EXPLORE:      return late ? LATE_PRI_EXPLORE     : EARLY_PRI_EXPLORE;
            case GOAL_PAINT_TILE:   return late ? LATE_PRI_PAINT_TILE  : EARLY_PRI_PAINT_TILE;
            default: return 0;
        }
    }

    public int getCurrentGoal() { return currentGoal; }
    public MapLocation getGoalTarget() { return goalTarget; }

    public void setGoal(int goalType, MapLocation target) {
        this.currentGoal = goalType;
        this.goalTarget = target;
    }

    public void clearGoal() {
        this.currentGoal = GOAL_EXPLORE;
        this.goalTarget = null;
    }

    public void evaluateGoals(RobotController rc, MapData mapData,
                             MapLocation[] knownRuins, int knownRuinCount) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int roundNum = rc.getRoundNum();

        int bestPriority = getPriority(GOAL_PAINT_TILE, roundNum);
        int bestGoal = GOAL_PAINT_TILE;
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        MapLocation[] ruins = rc.senseNearbyRuins(-1);

        boolean allContested = true;
        boolean foundAnyRuin = false;

        int priCapture = getPriority(GOAL_CAPTURE_RUIN, roundNum);
        int priContest = getPriority(GOAL_CONTEST_RUIN, roundNum);

        for (MapLocation ruinLoc : ruins) {
            RobotInfo robotAtRuin = rc.senseRobotAtLocation(ruinLoc);
            if (robotAtRuin != null) continue;

            foundAnyRuin = true;
            int dist = myLoc.distanceSquaredTo(ruinLoc);
            int enemyPaintCount = countEnemyPaintNearRuin(rc, ruinLoc);

            int allySoldiersNearRuin = countAllySoldiersNearRuin(rc, ruinLoc);
            if (allySoldiersNearRuin >= 2) continue;

            if (enemyPaintCount == 0) {
                allContested = false;
                int score = (10000 / Math.max(1, dist)) + 50 - (allySoldiersNearRuin * 3000);
                if (priCapture > bestPriority ||
                    (priCapture == bestPriority && score > bestScore)) {
                    bestPriority = priCapture;
                    bestGoal = GOAL_CAPTURE_RUIN;
                    bestScore = score;
                    bestTarget = ruinLoc;
                }
            } else if (enemyPaintCount < 5) {
                int score = (8000 / Math.max(1, dist)) - (enemyPaintCount * 3) - (allySoldiersNearRuin * 3000);
                if (priContest > bestPriority ||
                    (priContest == bestPriority && score > bestScore)) {
                    bestPriority = priContest;
                    bestGoal = GOAL_CONTEST_RUIN;
                    bestScore = score;
                    bestTarget = ruinLoc;
                }
            }
        }

        for (int i = 0; i < knownRuinCount; i++) {
            MapLocation ruinLoc = knownRuins[i];
            if (rc.canSenseLocation(ruinLoc)) continue; 

            int dist = myLoc.distanceSquaredTo(ruinLoc);
            int score = (10000 / Math.max(1, dist)) + 20;
            if (priCapture > bestPriority ||
                (priCapture == bestPriority && score > bestScore)) {
                bestPriority = priCapture;
                bestGoal = GOAL_CAPTURE_RUIN;
                bestScore = score;
                bestTarget = ruinLoc;
            }
        }

        int priRush = getPriority(GOAL_RUSH_ENEMY, roundNum);
        boolean shouldRush = (foundAnyRuin && allContested);

        if (!shouldRush && roundNum > MID_END && bestPriority <= priRush) {
            shouldRush = true;
        }

        if (shouldRush && bestPriority <= priRush) {
            int mapW = mapData.getMapWidth();
            int mapH = mapData.getMapHeight();

            MapLocation rushTarget = null;
            int rushScore = 0;
            RobotInfo[] allEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (RobotInfo e : allEnemies) {
                if (e.type.isTowerType()) {
                    int dist = myLoc.distanceSquaredTo(e.location);
                    int score = 20000 / Math.max(1, dist);
                    if (score > rushScore) {
                        rushScore = score;
                        rushTarget = e.location;
                    }
                }
            }

            if (rushTarget == null) {
                rushTarget = Communication.getRotational(myLoc.x, myLoc.y, mapW, mapH);
                rushScore = 9000 / Math.max(1, myLoc.distanceSquaredTo(rushTarget));
            }

            if (priRush > bestPriority ||
                (priRush == bestPriority && rushScore > bestScore)) {
                bestPriority = priRush;
                bestGoal = GOAL_RUSH_ENEMY;
                bestScore = rushScore;
                bestTarget = rushTarget;
            }
        }

        int priBattle = getPriority(GOAL_BATTLEFRONT, roundNum);
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo closest = enemies[0];
            int closestDist = Integer.MAX_VALUE;
            for (RobotInfo e : enemies) {
                int d = myLoc.distanceSquaredTo(e.location);
                if (d < closestDist) {
                    closestDist = d;
                    closest = e;
                }
            }
            int score = closest.type.isTowerType() ? 200 : 80;
            score += (100 / Math.max(1, closestDist));
            if (priBattle > bestPriority ||
                (priBattle == bestPriority && score > bestScore)) {
                bestPriority = priBattle;
                bestGoal = GOAL_BATTLEFRONT;
                bestTarget = closest.location;
                bestScore = score;
            }
        }

        int priPaint = getPriority(GOAL_PAINT_AREA, roundNum);
        if (bestPriority < priPaint) {
            MapInfo[] nearby = rc.senseNearbyMapInfos();
            MapLocation bestPaintLoc = null;
            int bestPaintScore = 0;
            for (MapInfo tile : nearby) {
                MapLocation loc = tile.getMapLocation();
                if (!tile.isPassable()) continue;
                PaintType paint = tile.getPaint();
                if (paint == PaintType.EMPTY || paint.isEnemy()) {
                    int score = paint.isEnemy() ? 4 : 2;
                    int dist = myLoc.distanceSquaredTo(loc);
                    score -= (dist / 10);
                    if (score > bestPaintScore) {
                        bestPaintScore = score;
                        bestPaintLoc = loc;
                    }
                }
            }
            if (bestPaintLoc != null && bestPaintScore > 1) {
                bestPriority = priPaint;
                bestGoal = GOAL_PAINT_AREA;
                bestTarget = bestPaintLoc;
                bestScore = bestPaintScore;
            }
        }

        int priExplore = getPriority(GOAL_EXPLORE, roundNum);
        if (bestPriority < priExplore) {
            bestPriority = priExplore;
            bestGoal = GOAL_EXPLORE;
            bestTarget = null;
        }

        currentGoal = bestGoal;
        goalTarget = bestTarget;
    }

    private int countEnemyPaintNearRuin(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        int count = 0;
        MapInfo[] tilesNearRuin = rc.senseNearbyMapInfos(ruinLoc, 8);
        for (MapInfo t : tilesNearRuin) {
            if (t.getPaint().isEnemy()) {
                count++;
            }
        }
        return count;
    }

    private int countAllySoldiersNearRuin(RobotController rc, MapLocation ruinLoc) throws GameActionException {
        RobotInfo[] nearbyAllies = rc.senseNearbyRobots(ruinLoc, 8, rc.getTeam());
        int count = 0;
        for (RobotInfo ally : nearbyAllies) {
            if (ally.type == UnitType.SOLDIER) count++;
        }
        return count;
    }

    public String getGoalName() {
        switch (currentGoal) {
            case GOAL_CAPTURE_RUIN: return "CAPTURE_RUIN";
            case GOAL_BATTLEFRONT:  return "BATTLEFRONT";
            case GOAL_CONTEST_RUIN: return "CONTEST_RUIN";
            case GOAL_RUSH_ENEMY:   return "RUSH_ENEMY";
            case GOAL_PAINT_AREA:   return "PAINT_AREA";
            case GOAL_EXPLORE:      return "EXPLORE";
            case GOAL_PAINT_TILE:   return "PAINT_TILE";
            default: return "UNKNOWN";
        }
    }
}
