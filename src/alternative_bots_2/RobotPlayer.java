package alternative_bots_2;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;
import battlecode.common.UnitType;

public class RobotPlayer {

    static int turnCount = 0;
    static MapData mapData;
    static Pathfinding pathfinding;
    static Communication comm;
    static Random rng;
    static MapLocation myLoc;
    static Team myTeam;
    static int myPaint;
    static int roundNum;
    static int myID;
    static int mapWidth;
    static int mapHeight;

    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST,
        Direction.SOUTHEAST, Direction.SOUTH, Direction.SOUTHWEST,
        Direction.WEST, Direction.NORTHWEST
    };

    // Soldier 
    static GoalManager goalManager;
    static MapLocation exploreTarget = null;
    static boolean[][] towerPattern = null;
    static int towerBuildCount = 0;

    // Tower 
    static int spawnCount = 0;

    // Painter
    static boolean[][] paintTowerPattern_cache = null;
    static boolean[][] moneyTowerPattern_cache = null;

    // ruin locations 
    static MapLocation[] knownRuins = new MapLocation[50];
    static int knownRuinCount = 0;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        myTeam = rc.getTeam();
        myID = rc.getID();
        mapWidth = rc.getMapWidth();
        mapHeight = rc.getMapHeight();
        mapData = new MapData(rc);
        pathfinding = new Pathfinding(rc, mapData);
        comm = new Communication(rc);
        goalManager = new GoalManager();
        rng = new Random(rc.getID());

        while (true) {
            turnCount++;
            try {
                myLoc = rc.getLocation();
                myPaint = rc.getPaint();
                roundNum = rc.getRoundNum();

                if (turnCount == 1) {
                    mapData.updateAllVisible();
                } else {
                    mapData.updateNewlyVisible();
                }

                switch (rc.getType()) {
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        MapLocation combatTarget = Combat.selectTowerTarget(rc);
        if (combatTarget != null) {
            rc.attack(combatTarget);
            int msg = Communication.encodeLocation(combatTarget, Communication.MSG_BATTLEFRONT);
            comm.towerBroadcast(msg);
        }

        towerProcessMessages(rc);
        towerSpawnRobots(rc);
        rc.setIndicatorString("Tower | Spawns:" + spawnCount + " | Paint:" + myPaint);
    }

    private static void towerProcessMessages(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int msgData = m.getBytes();
            int msgType = Communication.decodeType(msgData);
            if (msgType == Communication.MSG_ENEMY_TOWER || msgType == Communication.MSG_NEED_MOPPER
                || msgType == Communication.MSG_RUIN_FOUND) {
                comm.towerBroadcast(msgData);
            }
        }
    }

    private static void towerSpawnRobots(RobotController rc) throws GameActionException {
        if (myPaint < 100) return;

        UnitType toSpawn = null;

        if (roundNum < GoalManager.EARLY_END) {
            // EARLY GAME: 80% soldier, 10% splasher, 10% mopper
            int roll = spawnCount % 10;
            if (roll < 8) {
                if (myPaint >= UnitType.SOLDIER.paintCost) toSpawn = UnitType.SOLDIER;
            } else if (roll < 9) {
                if (myPaint >= UnitType.SPLASHER.paintCost) toSpawn = UnitType.SPLASHER;
                else return;
            } else {
                if (myPaint >= UnitType.MOPPER.paintCost) toSpawn = UnitType.MOPPER;
            }
        } else if (roundNum <= GoalManager.MID_END) {
            // MID GAME: 70% soldier, 20% splasher, 10% mopper
            int roll = spawnCount % 10;
            if (roll < 7) {
                if (myPaint >= UnitType.SOLDIER.paintCost) toSpawn = UnitType.SOLDIER;
            } else if (roll < 9) {
                if (myPaint >= UnitType.SPLASHER.paintCost) toSpawn = UnitType.SPLASHER;
                else return;
            } else {
                if (myPaint >= UnitType.MOPPER.paintCost) toSpawn = UnitType.MOPPER;
            }
        } else {
            // LATE GAME: 60% soldier, 30% splasher, 10% mopper
            int roll = spawnCount % 10;
            if (roll < 6) {
                if (myPaint >= UnitType.SOLDIER.paintCost) toSpawn = UnitType.SOLDIER;
            } else if (roll < 9) {
                if (myPaint >= UnitType.SPLASHER.paintCost) toSpawn = UnitType.SPLASHER;
                else return; 
            } else {
                if (myPaint >= UnitType.MOPPER.paintCost) toSpawn = UnitType.MOPPER;
            }
        }

        if (toSpawn == null) return;

        for (Direction dir : directions) {
            MapLocation spawnLoc = myLoc.add(dir);
            if (rc.canBuildRobot(toSpawn, spawnLoc)) {
                rc.buildRobot(toSpawn, spawnLoc);
                spawnCount++;
                return;
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        if (paintTowerPattern_cache == null) {
            paintTowerPattern_cache = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
        }
        if (moneyTowerPattern_cache == null) {
            moneyTowerPattern_cache = rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
        }

        soldierProcessMessages(rc);
        goalManager.evaluateGoals(rc, mapData, knownRuins, knownRuinCount);
        soldierBroadcastRuins(rc);

        int goal = goalManager.getCurrentGoal();
        if (roundNum < GoalManager.EARLY_END) {
            // EARLY: 100% Money Tower
            towerPattern = moneyTowerPattern_cache;
        } else {
            // MID & LATE: 100% Paint Tower
            towerPattern = paintTowerPattern_cache;
        }

        // Combat attack: serang musuh KECUALI jika sedang di ruin 
        boolean atRuin = (goal == GoalManager.GOAL_CAPTURE_RUIN || goal == GoalManager.GOAL_CONTEST_RUIN)
                         && goalManager.getGoalTarget() != null
                         && myLoc.distanceSquaredTo(goalManager.getGoalTarget()) <= 8;
        if (!atRuin) {
            MapLocation combatTarget = Combat.selectSoldierTarget(rc);
            if (combatTarget != null && rc.canAttack(combatTarget)) {
                rc.attack(combatTarget);
            }
        }

        switch (goal) {
            case GoalManager.GOAL_CAPTURE_RUIN:
                executeCaptureRuin(rc);
                break;
            case GoalManager.GOAL_BATTLEFRONT:
                executeBattlefront(rc);
                break;
            case GoalManager.GOAL_CONTEST_RUIN:
                executeContestRuin(rc);
                break;
            case GoalManager.GOAL_RUSH_ENEMY:
                executeRushEnemy(rc);
                break;
            case GoalManager.GOAL_PAINT_AREA:
                executePaintArea(rc);
                break;
            case GoalManager.GOAL_EXPLORE:
            default:
                executeExplore(rc);
                break;
        }

        paintBelowIfNeeded(rc);
        rc.setIndicatorString(goalManager.getGoalName() + " P:" + myPaint);
    }

    private static void soldierProcessMessages(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int data = m.getBytes();
            int type = Communication.decodeType(data);

            int curGoal = goalManager.getCurrentGoal();
            if (type == Communication.MSG_BATTLEFRONT) {
                MapLocation battleLoc = Communication.decodeLocation(data);
                if (curGoal == GoalManager.GOAL_EXPLORE || curGoal == GoalManager.GOAL_PAINT_TILE
                    || curGoal == GoalManager.GOAL_PAINT_AREA) {
                    goalManager.setGoal(GoalManager.GOAL_BATTLEFRONT, battleLoc);
                }
            } else if (type == Communication.MSG_ENEMY_TOWER) {
                MapLocation towerLoc = Communication.decodeLocation(data);
                if (curGoal == GoalManager.GOAL_EXPLORE || curGoal == GoalManager.GOAL_PAINT_TILE
                    || curGoal == GoalManager.GOAL_PAINT_AREA) {
                    goalManager.setGoal(GoalManager.GOAL_RUSH_ENEMY, towerLoc);
                }
            } else if (type == Communication.MSG_RUIN_FOUND) {
                MapLocation ruinLoc = Communication.decodeLocation(data);
                addKnownRuin(ruinLoc);
            }
        }
    }

    private static void soldierBroadcastRuins(RobotController rc) throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruinLoc : ruins) {
            RobotInfo r = rc.senseRobotAtLocation(ruinLoc);
            if (r != null) continue; 
            if (isKnownRuin(ruinLoc)) continue; 
            addKnownRuin(ruinLoc);
            int msg = Communication.encodeLocation(ruinLoc, Communication.MSG_RUIN_FOUND);
            comm.broadcastToNearbyTowers(msg);
        }
    }

    private static void addKnownRuin(MapLocation loc) {
        if (isKnownRuin(loc)) return;
        if (knownRuinCount < knownRuins.length) {
            knownRuins[knownRuinCount++] = loc;
        }
    }

    private static boolean isKnownRuin(MapLocation loc) {
        for (int i = 0; i < knownRuinCount; i++) {
            if (knownRuins[i].equals(loc)) return true;
        }
        return false;
    }

    private static void executeCaptureRuin(RobotController rc) throws GameActionException {
        MapLocation target = goalManager.getGoalTarget();
        if (target == null) { goalManager.clearGoal(); return; }

        if (rc.canSenseLocation(target)) {
            RobotInfo robotAtRuin = rc.senseRobotAtLocation(target);
            if (robotAtRuin != null) {
                goalManager.clearGoal();
                return;
            }

            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(target, 8, myTeam);
            int allySoldierCount = 0;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == UnitType.SOLDIER && ally.getID() != rc.getID()) allySoldierCount++;
            }
            if (allySoldierCount >= 2) {
                goalManager.clearGoal();
                return;
            }
        }

        if (!myLoc.isAdjacentTo(target) && myLoc.distanceSquaredTo(target) > 2) {
            pathfinding.moveTo(target);
            myLoc = rc.getLocation();
        }
        paintTowerPattern(rc, target);

        UnitType preferred = (towerPattern == paintTowerPattern_cache)
            ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
        UnitType fallback = (preferred == UnitType.LEVEL_ONE_PAINT_TOWER)
            ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
        if (rc.canCompleteTowerPattern(preferred, target)) {
            rc.completeTowerPattern(preferred, target);
            towerBuildCount++;
            goalManager.clearGoal();
        } else if (rc.canCompleteTowerPattern(fallback, target)) {
            rc.completeTowerPattern(fallback, target);
            towerBuildCount++;
            goalManager.clearGoal();
        }
    }

    private static void executeContestRuin(RobotController rc) throws GameActionException {
        MapLocation target = goalManager.getGoalTarget();
        if (target == null) { goalManager.clearGoal(); return; }

        if (rc.canSenseLocation(target)) {
            RobotInfo robotAtRuin = rc.senseRobotAtLocation(target);
            if (robotAtRuin != null) {
                goalManager.clearGoal();
                return;
            }

            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(target, 8, myTeam);
            int allySoldierCount = 0;
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == UnitType.SOLDIER && ally.getID() != rc.getID()) allySoldierCount++;
            }
            if (allySoldierCount >= 2) {
                goalManager.clearGoal();
                return;
            }
        }

        int msg = Communication.encodeLocation(target, Communication.MSG_NEED_MOPPER);
        comm.broadcastToNearbyTowers(msg);

        if (!myLoc.isAdjacentTo(target)) {
            pathfinding.moveTo(target);
            myLoc = rc.getLocation();
        }
        paintTowerPattern(rc, target);

        UnitType preferred2 = (towerPattern == paintTowerPattern_cache)
            ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER;
        UnitType fallback2 = (preferred2 == UnitType.LEVEL_ONE_PAINT_TOWER)
            ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER;
        if (rc.canCompleteTowerPattern(preferred2, target)) {
            rc.completeTowerPattern(preferred2, target);
            towerBuildCount++;
            goalManager.clearGoal();
            return;
        } else if (rc.canCompleteTowerPattern(fallback2, target)) {
            rc.completeTowerPattern(fallback2, target);
            towerBuildCount++;
            goalManager.clearGoal();
            return;
        }

        MapInfo[] nearRuin = rc.senseNearbyMapInfos(target, 8);
        int enemyCount = 0;
        for (MapInfo t : nearRuin) {
            if (t.getPaint().isEnemy()) enemyCount++;
        }
        if (enemyCount > 8) {
            mapData.setContested(target);
            goalManager.clearGoal();
        }
    }

    private static void executeRushEnemy(RobotController rc) throws GameActionException {
        MapLocation target = goalManager.getGoalTarget();
        if (target == null) {
            target = Communication.getRotational(myLoc.x, myLoc.y, mapWidth, mapHeight);
            goalManager.setGoal(GoalManager.GOAL_RUSH_ENEMY, target);
        }

        pathfinding.moveTo(target);
        myLoc = rc.getLocation();
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, myTeam.opponent());
        if (enemies.length > 0) {
            RobotInfo targetEnemy = null;
            int targetDist = Integer.MAX_VALUE;
            for (RobotInfo e : enemies) {
                if (e.type.isTowerType()) {
                    int d = myLoc.distanceSquaredTo(e.location);
                    if (d < targetDist) {
                        targetDist = d;
                        targetEnemy = e;
                    }
                }
            }
            if (targetEnemy == null) {
                for (RobotInfo e : enemies) {
                    int d = myLoc.distanceSquaredTo(e.location);
                    if (d < targetDist) {
                        targetDist = d;
                        targetEnemy = e;
                    }
                }
            }

            if (targetEnemy != null) {
                if (targetEnemy.type.isTowerType()) {
                    int enemyMsg = Communication.encodeLocation(targetEnemy.location, Communication.MSG_ENEMY_TOWER);
                    comm.broadcastToNearbyTowers(enemyMsg);
                }
                goalManager.setGoal(GoalManager.GOAL_RUSH_ENEMY, targetEnemy.location);
            }
        }

        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        for (MapLocation ruinLoc : ruins) {
            RobotInfo r = rc.senseRobotAtLocation(ruinLoc);
            if (r == null) {
                goalManager.setGoal(GoalManager.GOAL_CONTEST_RUIN, ruinLoc);
                return;
            }
        }
    }

    private static void executeBattlefront(RobotController rc) throws GameActionException {
        MapLocation target = goalManager.getGoalTarget();
        if (target == null) { goalManager.clearGoal(); return; }

        pathfinding.moveTo(target);
        myLoc = rc.getLocation();

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, myTeam.opponent());
        if (enemies.length == 0 && myLoc.distanceSquaredTo(target) <= 8) {
            goalManager.clearGoal();
        }
    }

    private static void executePaintArea(RobotController rc) throws GameActionException {
        MapLocation target = goalManager.getGoalTarget();
        if (target == null) { goalManager.clearGoal(); return; }

        if (myLoc.distanceSquaredTo(target) > 2) {
            pathfinding.moveTo(target);
            myLoc = rc.getLocation();
        }

        MapInfo[] nearby = rc.senseNearbyMapInfos(myLoc, 4);
        for (MapInfo tile : nearby) {
            MapLocation loc = tile.getMapLocation();
            if (tile.isPassable() && !tile.getPaint().isAlly() && rc.canAttack(loc)) {
                rc.attack(loc, false);
                break;
            }
        }
    }

    private static void executeExplore(RobotController rc) throws GameActionException {
        if (exploreTarget == null || myLoc.distanceSquaredTo(exploreTarget) <= 8 ||
            mapData.isVisited(exploreTarget)) {
            exploreTarget = findExploreTarget();
        }

        if (exploreTarget != null) {
            pathfinding.moveTo(exploreTarget);
            myLoc = rc.getLocation();

            MapInfo[] nearby = rc.senseNearbyMapInfos(myLoc, 4);
            for (MapInfo tile : nearby) {
                MapLocation loc = tile.getMapLocation();
                if (tile.isPassable() && !tile.getPaint().isAlly() && rc.canAttack(loc)) {
                    rc.attack(loc, false);
                    break;
                }
            }
        } else {
            Direction dir = directions[rng.nextInt(directions.length)];
            if (rc.canMove(dir)) {
                rc.move(dir);
                myLoc = rc.getLocation();
            }
        }
    }

    private static MapLocation findExploreTarget() {
        if (roundNum < GoalManager.EARLY_END) {
            MapLocation best = null;
            int bestDist = Integer.MAX_VALUE;

            for (int r = 5; r <= Math.max(mapWidth, mapHeight); r += 5) {
                for (int dx = -r; dx <= r; dx += 5) {
                    for (int dy = -r; dy <= r; dy += 5) {
                        int x = myLoc.x + dx;
                        int y = myLoc.y + dy;
                        if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight) continue;
                        MapLocation loc = new MapLocation(x, y);
                        if (mapData.isVisited(loc)) continue;
                        int dist = myLoc.distanceSquaredTo(loc);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = loc;
                        }
                    }
                }
                if (best != null) return best;
            }
        }

        MapLocation[] candidates = {
            new MapLocation(2, 2),
            new MapLocation(mapWidth - 3, 2),
            new MapLocation(2, mapHeight - 3),
            new MapLocation(mapWidth - 3, mapHeight - 3),
            new MapLocation(mapWidth / 2, 2),
            new MapLocation(mapWidth / 2, mapHeight - 3),
            new MapLocation(2, mapHeight / 2),
            new MapLocation(mapWidth - 3, mapHeight / 2),
            new MapLocation(mapWidth / 2, mapHeight / 2),
        };

        MapLocation best = null;
        int bestScore = Integer.MAX_VALUE;

        for (MapLocation loc : candidates) {
            if (mapData.isVisited(loc)) continue;
            int score = myLoc.distanceSquaredTo(loc);
            if (score < bestScore) {
                bestScore = score;
                best = loc;
            }
        }

        if (best != null) return best;

        for (int i = 0; i < 10; i++) {
            int x = rng.nextInt(mapWidth);
            int y = rng.nextInt(mapHeight);
            MapLocation loc = new MapLocation(x, y);
            if (!mapData.isVisited(loc)) return loc;
        }

        return new MapLocation(rng.nextInt(mapWidth), rng.nextInt(mapHeight));
    }

    private static void paintTowerPattern(RobotController rc, MapLocation ruinCenter) throws GameActionException {
        if (towerPattern == null) return;

        MapLocation nextTarget = getNextPaintTarget(ruinCenter, rc, myLoc, towerPattern);
        if (nextTarget == null) return;

        if (!rc.canAttack(nextTarget)) {
            Direction dir = myLoc.directionTo(nextTarget);
            if (rc.canMove(dir)) {
                rc.move(dir);
                myLoc = rc.getLocation();
            }
        }

        boolean useSecondary = needsSecondary(nextTarget, ruinCenter, towerPattern);
        if (rc.canAttack(nextTarget)) {
            rc.attack(nextTarget, useSecondary);
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        boolean acted = false;
        Direction swingDir = Combat.selectMopperSwingDirection(rc);
        if (swingDir != null && rc.canMopSwing(swingDir)) {
            rc.mopSwing(swingDir);
            acted = true;
        }
        mopperTransferPaint(rc);

        MapLocation mopperTarget = mopperCheckRequests(rc);
        MapLocation bestTarget = (mopperTarget != null) ? mopperTarget : mopperFindBestTarget(rc);

        if (bestTarget != null) {
            if (myLoc.distanceSquaredTo(bestTarget) > 2) {
                pathfinding.moveTo(bestTarget);
                myLoc = rc.getLocation();
            }
            if (!acted && rc.canAttack(bestTarget)) {
                rc.attack(bestTarget);
            }
        } else {
            Direction dir = directions[rng.nextInt(directions.length)];
            if (rc.canMove(dir)) {
                rc.move(dir);
                myLoc = rc.getLocation();
            }
            if (!acted) {
                mopperAttackNearbyEnemyPaint(rc);
            }
        }

        rc.setIndicatorString("MOPPER | P:" + myPaint);
    }

    private static void mopperTransferPaint(RobotController rc) throws GameActionException {
        if (myPaint <= 60) return;

        RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.SOLDIER && ally.paintAmount < 50) {
                if (myLoc.isAdjacentTo(ally.location) || myLoc.equals(ally.location)) {
                    int transferAmount = Math.min(myPaint - 60, 50);
                    if (transferAmount > 0 && rc.canTransferPaint(ally.location, transferAmount)) {
                        rc.transferPaint(ally.location, transferAmount);
                        myPaint -= transferAmount;
                        return;
                    }
                }
            }
        }
    }

    private static MapLocation mopperFindBestTarget(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestLoc = null;
        int bestScore = 0;

        for (MapInfo tile : nearby) {
            PaintType paint = tile.getPaint();
            if (!paint.isEnemy()) continue;

            MapLocation loc = tile.getMapLocation();
            int score = 3;

            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(loc, 8, myTeam);
            for (RobotInfo ally : nearbyAllies) {
                if (ally.type == UnitType.SOLDIER && ally.paintAmount < 50) {
                    score += 5;
                }
            }

            int dist = myLoc.distanceSquaredTo(loc);
            score -= (dist / 8);

            if (score > bestScore) {
                bestScore = score;
                bestLoc = loc;
            }
        }

        return bestLoc;
    }

    private static void mopperAttackNearbyEnemyPaint(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        for (MapInfo tile : nearby) {
            if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                rc.attack(tile.getMapLocation());
                return;
            }
        }
    }

    private static MapLocation mopperCheckRequests(RobotController rc) throws GameActionException {
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            int data = m.getBytes();
            int type = Communication.decodeType(data);
            if (type == Communication.MSG_NEED_MOPPER) {
                return Communication.decodeLocation(data);
            }
        }
        return null;
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        if (myPaint < 100) {
            splasherRefill(rc);
            paintBelowIfNeeded(rc);
            rc.setIndicatorString("REFILL | P:" + myPaint);
            return;
        }

        MapLocation bestTarget = Combat.selectSplasherTarget(rc);

        if (bestTarget == null) {
            bestTarget = splasherFindPaintTarget(rc);
        }

        if (bestTarget != null) {
            if (!rc.canAttack(bestTarget)) {
                pathfinding.moveTo(bestTarget);
                myLoc = rc.getLocation();
            }
            if (rc.canAttack(bestTarget)) {
                rc.attack(bestTarget, false);
            }
        } else {
            splasherMoveToBattlefront(rc);
        }

        paintBelowIfNeeded(rc);
        rc.setIndicatorString("SPLASH | P:" + myPaint);
    }

    private static MapLocation splasherFindPaintTarget(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestCenter = null;
        int bestScore = 2;

        for (MapInfo tile : nearby) {
            MapLocation center = tile.getMapLocation();
            if (!rc.canAttack(center)) continue;

            int score = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx * dx + dy * dy > 4) continue;
                    MapLocation tLoc = center.translate(dx, dy);
                    if (!rc.canSenseLocation(tLoc)) continue;
                    MapInfo tInfo = rc.senseMapInfo(tLoc);
                    PaintType paint = tInfo.getPaint();
                    if (paint == PaintType.EMPTY) score += 1;
                    else if (paint.isEnemy()) score += 2;
                }
            }
            score -= myLoc.distanceSquaredTo(center) / 4;

            if (score > bestScore) {
                bestScore = score;
                bestCenter = center;
            }
        }
        return bestCenter;
    }

    private static void splasherRefill(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, myTeam);
        RobotInfo closestTower = null;
        int closestDist = Integer.MAX_VALUE;

        for (RobotInfo ally : allies) {
            if (ally.type.isTowerType()) {
                int dist = myLoc.distanceSquaredTo(ally.location);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestTower = ally;
                }
            }
        }

        if (closestTower != null) {
            if (myLoc.isAdjacentTo(closestTower.location) || myLoc.equals(closestTower.location)) {
                int takeAmount = -Math.min(closestTower.paintAmount, 150);
                if (rc.canTransferPaint(closestTower.location, takeAmount)) {
                    rc.transferPaint(closestTower.location, takeAmount);
                }
            } else {
                pathfinding.moveTo(closestTower.location);
                myLoc = rc.getLocation();
            }
        } else {
            splasherMoveRandomly(rc);
        }
    }

    private static void splasherMoveToBattlefront(RobotController rc) throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, myTeam.opponent());
        if (enemies.length > 0) {
            RobotInfo bestEnemy = enemies[0];
            for (RobotInfo e : enemies) {
                if (e.type.isTowerType() && !bestEnemy.type.isTowerType()) {
                    bestEnemy = e;
                } else if (e.type.isTowerType() == bestEnemy.type.isTowerType()) {
                    if (myLoc.distanceSquaredTo(e.location) < myLoc.distanceSquaredTo(bestEnemy.location)) {
                        bestEnemy = e;
                    }
                }
            }
            pathfinding.moveTo(bestEnemy.location);
        } else {
            if (rc.getRoundNum() > GoalManager.MID_END) {
                MapLocation enemyPos = Communication.getRotational(myLoc.x, myLoc.y, mapWidth, mapHeight);
                pathfinding.moveTo(enemyPos);
                return;
            }
            MapInfo[] nearby = rc.senseNearbyMapInfos();
            for (MapInfo tile : nearby) {
                if (tile.getPaint().isEnemy()) {
                    pathfinding.moveTo(tile.getMapLocation());
                    return;
                }
            }
            splasherMoveRandomly(rc);
        }
    }

    private static void splasherMoveRandomly(RobotController rc) throws GameActionException {
        Direction dir = directions[rng.nextInt(directions.length)];
        if (rc.canMove(dir)) {
            rc.move(dir);
            myLoc = rc.getLocation();
        }
    }

    public static boolean[][] getPaintPattern(RobotController rc) throws GameActionException {
        if (paintTowerPattern_cache == null) {
            paintTowerPattern_cache = rc.getTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER);
        }
        return paintTowerPattern_cache;
    }

    public static boolean[][] getMoneyPattern(RobotController rc) throws GameActionException {
        if (moneyTowerPattern_cache == null) {
            moneyTowerPattern_cache = rc.getTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER);
        }
        return moneyTowerPattern_cache;
    }

    public static boolean needsSecondary(MapLocation tile, MapLocation ruinCenter, boolean[][] pattern) {
        int dx = tile.x - ruinCenter.x + 2;
        int dy = tile.y - ruinCenter.y + 2;
        if (dx < 0 || dx > 4 || dy < 0 || dy > 4) return false;
        return pattern[dy][dx];
    }

    public static MapLocation getNextPaintTarget(MapLocation ruinCenter, RobotController rc,
                                                  MapLocation loc, boolean[][] pattern) throws GameActionException {
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;

                MapLocation tLoc = ruinCenter.translate(dx, dy);
                if (!rc.canSenseLocation(tLoc)) continue;

                MapInfo info = rc.senseMapInfo(tLoc);
                if (!info.isPassable()) continue;

                boolean useSecondary = pattern[dy + 2][dx + 2];
                PaintType currentPaint = info.getPaint();

                if (!currentPaint.isAlly() || currentPaint.isSecondary() != useSecondary) {
                    int dist = loc.distanceSquaredTo(tLoc);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = tLoc;
                    }
                }
            }
        }
        return best;
    }

    public static boolean isPatternComplete(MapLocation ruinCenter, RobotController rc,
                                             boolean[][] pattern) throws GameActionException {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;

                MapLocation tLoc = ruinCenter.translate(dx, dy);
                if (!rc.canSenseLocation(tLoc)) return false;

                MapInfo info = rc.senseMapInfo(tLoc);
                if (!info.isPassable()) continue;

                boolean useSecondary = pattern[dy + 2][dx + 2];
                PaintType currentPaint = info.getPaint();

                if (!currentPaint.isAlly()) return false;
                if (currentPaint.isSecondary() != useSecondary) return false;
            }
        }
        return true;
    }

    public static int countUnpaintedTiles(MapLocation ruinCenter, RobotController rc,
                                           boolean[][] pattern) throws GameActionException {
        int count = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;

                MapLocation tLoc = ruinCenter.translate(dx, dy);
                if (!rc.canSenseLocation(tLoc)) { count++; continue; }

                MapInfo info = rc.senseMapInfo(tLoc);
                if (!info.isPassable()) continue;

                boolean useSecondary = pattern[dy + 2][dx + 2];
                PaintType currentPaint = info.getPaint();

                if (!currentPaint.isAlly() || currentPaint.isSecondary() != useSecondary) {
                    count++;
                }
            }
        }
        return count;
    }

    private static void paintBelowIfNeeded(RobotController rc) throws GameActionException {
        if (rc.canSenseLocation(myLoc)) {
            MapInfo currentTile = rc.senseMapInfo(myLoc);
            if (!currentTile.getPaint().isAlly() && rc.canAttack(myLoc)) {
                rc.attack(myLoc, false);
            }
        }
    }
}

