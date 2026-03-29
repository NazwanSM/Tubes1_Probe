package main_bot;
import battlecode.common.*;
import java.util.Random;

public class RobotPlayer {
    static int turnCount = 0;
    static final Random rng = new Random(6147);
    static MapLocation spawnLoc = null;
    static MapLocation enemyBase = null;
    static MapLocation[] possibleBases = new MapLocation[3];
    static int currentBaseIndex = 0;

    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static final Direction[] CARDINAL_DIRS = {
        Direction.NORTH,
        Direction.SOUTH,
        Direction.EAST,
        Direction.WEST,
    };

    static final UnitType[] TOWER_PRIORITY = {
        UnitType.LEVEL_ONE_MONEY_TOWER,
        UnitType.LEVEL_ONE_PAINT_TOWER
    };

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        spawnLoc = rc.getLocation();
        int w = rc.getMapWidth();
        int h = rc.getMapHeight();
        
        possibleBases[0] = new MapLocation(w - 1 - spawnLoc.x, h - 1 - spawnLoc.y); // Rotasi 180 (Kanan-Bawah)
        possibleBases[1] = new MapLocation(w - 1 - spawnLoc.x, spawnLoc.y);         // Refleksi Horizontal
        possibleBases[2] = new MapLocation(spawnLoc.x, h - 1 - spawnLoc.y);         // Refleksi Vertikal
        enemyBase = possibleBases[0];

        while (true) {
            turnCount++;
            try {
                switch (rc.getType()) {
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
                    default: runTower(rc); break;
                }
            } catch (GameActionException e) {
                System.out.println("GAE: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runTower(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();

        Comms.processMessages(rc);
        Combat.towerAttack(rc);

        UnitType[] spawnCandidates = {UnitType.SOLDIER, UnitType.SPLASHER, UnitType.MOPPER};
        UnitType idealUnit = null;
        int highestHeuristic = Integer.MIN_VALUE;
        
        for (UnitType unit : spawnCandidates) {
            int score = Heuristics.scoreTowerSpawn(rc, unit, round);
            score += rng.nextInt(30);
            
            if (score > highestHeuristic) {
                highestHeuristic = score;
                idealUnit = unit;
            }
        }

        Direction bestDirection = null;
        if (idealUnit != null) {
            for (Direction dir : directions) {
                MapLocation spawnLoc = rc.getLocation().add(dir);
                if (rc.canBuildRobot(idealUnit, spawnLoc)) {
                    bestDirection = dir;
                    break;
                }
            }
        }

        if (idealUnit != null && bestDirection != null) {
            MapLocation loc = rc.getLocation().add(bestDirection);
            rc.buildRobot(idealUnit, loc);
        }

        Comms.towerRelayAndCommand(rc);
    }

    public static void runSoldier(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int paint = rc.getPaint();
        int round = rc.getRoundNum();

        Comms.processMessages(rc);

        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        MapLocation nearestRuin = Nav.findNearestEmptyRuin(rc, ruins);
        MapLocation nearestTower = Nav.findNearestAllyTower(rc);

        if (nearestRuin == null && Comms.hasRecentRuinIntel(round)) {
            nearestRuin = Comms.lastRuinFound;
        }

        int bestScore = Integer.MIN_VALUE;
        int bestAction = Heuristics.ACT_NOTHING;
        Direction bestDir = null;
        MapLocation bestTarget = null;
        UnitType bestTower = null;
        boolean bestSecondary = false;
        int bestTransferAmt = 0;

        // Kandidat 1: Complete tower pattern
        for (MapLocation ruin : ruins) {
            UnitType tt = Heuristics.getTowerTypeForRuin(ruin);
            if (rc.canCompleteTowerPattern(tt, ruin)) {
                int score = Heuristics.scoreCompleteTower(tt);
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = Heuristics.ACT_COMPLETE_TOWER;
                    bestTarget = ruin;
                    bestTower = tt;
                }
            }
        }

        // Kandidat 2: Paint pattern tile
        for (MapLocation ruin : ruins) {
            if (rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo occ = rc.senseRobotAtLocation(ruin);
                if (occ.getType().isTowerType()) continue;
            }

            MapInfo[] patternTiles = rc.senseNearbyMapInfos(ruin, 8);
            for (MapInfo tile : patternTiles) {
                if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                    if (rc.canAttack(tile.getMapLocation())) {
                        int score = Heuristics.scorePaintPattern();
                        boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_PAINT;
                            bestTarget = tile.getMapLocation();
                            bestSecondary = sec;
                        }
                    }
                }
            }
        }

        // Kandidat 3: Mark tower pattern
        for (MapLocation ruin : ruins) {
            if (rc.canSenseRobotAtLocation(ruin)) {
                RobotInfo occ = rc.senseRobotAtLocation(ruin);
                if (occ.getType().isTowerType()) continue;
            }

            UnitType tt = Heuristics.getTowerTypeForRuin(ruin);
            if (rc.canMarkTowerPattern(tt, ruin)) {
                int score = Heuristics.scoreMarkTower(tt);
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = Heuristics.ACT_MARK_TOWER;
                    bestTarget = ruin;
                    bestTower = tt;
                }
            }
        }

        // Kandidat 4: Resource pattern
        if (ruins.length == 0) {
            MapInfo[] allTiles = rc.senseNearbyMapInfos();
            for (MapInfo tile : allTiles) {
                if (tile.isResourcePatternCenter()) {
                    MapLocation center = tile.getMapLocation();
                    if (rc.canCompleteResourcePattern(center)) {
                        int score = Heuristics.SCORE_COMPLETE_RESOURCE;
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_COMPLETE_RESOURCE;
                            bestTarget = center;
                        }
                    }
                    if (rc.canMarkResourcePattern(center)) {
                        int score = Heuristics.SCORE_MARK_RESOURCE;
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_MARK_RESOURCE;
                            bestTarget = center;
                        }
                    }
                }
            }
        }

        // Kandidat 5: Withdraw paint dari tower ally (kalo paint rendah)
        if (paint < Heuristics.LOW_PAINT_THRESHOLD && nearestTower != null) {
            int distToTower = myLoc.distanceSquaredTo(nearestTower);
            if (distToTower <= 2) {
                RobotInfo towerInfo = rc.senseRobotAtLocation(nearestTower);
                if (towerInfo != null) {
                    int towerPaint = towerInfo.getPaintAmount();
                    int availablePaint = Math.max(0, towerPaint - Heuristics.TOWER_PAINT_RESERVE);
                    int wantPaint = rc.getType().paintCapacity - paint;
                    int withdrawAmt = -Math.min(wantPaint, availablePaint);
                    if (withdrawAmt < 0 && rc.canTransferPaint(nearestTower, withdrawAmt)) {
                        int score = Heuristics.SCORE_WITHDRAW_PAINT;
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_WITHDRAW_PAINT;
                            bestTarget = nearestTower;
                            bestTransferAmt = withdrawAmt;
                        }
                    }
                }
            }
        }

        // Kandidat 6: Upgrade tower ally
        for (RobotInfo ally : rc.senseNearbyRobots(2, rc.getTeam())) {
            if (ally.getType().isTowerType() && rc.canUpgradeTower(ally.getLocation())) {
                int score = Heuristics.SCORE_UPGRADE_TOWER;
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = Heuristics.ACT_UPGRADE_TOWER;
                    bestTarget = ally.getLocation();
                }
            }
        }

        // Kandidat 7: Movement ke tiap direction
        MapLocation atkOrder = Comms.hasAttackOrder(round) ? Comms.attackTarget : null;
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                int score = Heuristics.scoreSoldierMove(rc, dir, nearestRuin, nearestTower, paint, atkOrder);
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = Heuristics.ACT_MOVE;
                    bestDir = dir;
                }
            }
        }

        // EKSEKUSI
        switch (bestAction) {
            case Heuristics.ACT_COMPLETE_TOWER:
                rc.completeTowerPattern(bestTower, bestTarget);
                break;
            case Heuristics.ACT_PAINT:
                rc.attack(bestTarget, bestSecondary);
                break;
            case Heuristics.ACT_MARK_TOWER:
                rc.markTowerPattern(bestTower, bestTarget);
                paintPatternTiles(rc, bestTarget);
                break;
            case Heuristics.ACT_COMPLETE_RESOURCE:
                rc.completeResourcePattern(bestTarget);
                break;
            case Heuristics.ACT_MARK_RESOURCE:
                rc.markResourcePattern(bestTarget);
                break;
            case Heuristics.ACT_WITHDRAW_PAINT:
                rc.transferPaint(bestTarget, bestTransferAmt);
                break;
            case Heuristics.ACT_UPGRADE_TOWER:
                rc.upgradeTower(bestTarget);
                break;
            case Heuristics.ACT_MOVE:
                rc.move(bestDir);
                break;
            default:
                Nav.randomMove(rc, rng);
                break;
        }

        if (bestAction != Heuristics.ACT_MOVE && bestAction != Heuristics.ACT_NOTHING
                && rc.isMovementReady() && nearestRuin != null) {
            Nav.moveTo(rc, nearestRuin);
        }

        // Bonus action
        if (rc.isActionReady()) {
            myLoc = rc.getLocation();
            boolean acted = false;

            // Prioritas 1: Complete tower yang sudah ready
            for (MapLocation ruin : ruins) {
                if (acted) break;
                for (UnitType tt : TOWER_PRIORITY) {
                    if (rc.canCompleteTowerPattern(tt, ruin)) {
                        rc.completeTowerPattern(tt, ruin);
                        acted = true;
                        break;
                    }
                }
            }

            // Prioritas 2: Paint pattern tile dengan warna yang benar
            if (!acted) {
                for (MapLocation ruin : ruins) {
                    if (acted) break;
                    if (rc.canSenseRobotAtLocation(ruin)) {
                        RobotInfo occ = rc.senseRobotAtLocation(ruin);
                        if (occ.getType().isTowerType()) continue;
                    }
                    MapInfo[] patternTiles = rc.senseNearbyMapInfos(ruin, 8);
                    for (MapInfo tile : patternTiles) {
                        if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                            if (rc.canAttack(tile.getMapLocation())) {
                                boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;
                                rc.attack(tile.getMapLocation(), sec);
                                acted = true;
                                break;
                            }
                        }
                    }
                }
            }

            // Prioritas 3: Paint tile sendiri / nearby (mark-aware)
            if (!acted && rc.canAttack(myLoc)) {
                MapInfo currentTile = rc.senseMapInfo(myLoc);
                if (!currentTile.getPaint().isAlly()) {
                    boolean sec = currentTile.getMark() == PaintType.ALLY_SECONDARY;
                    rc.attack(myLoc, sec);
                    acted = true;
                }
            }
            if (!acted) {
                MapInfo[] nearbyTiles = rc.senseNearbyMapInfos(myLoc, 4);
                for (MapInfo tile : nearbyTiles) {
                    MapLocation tLoc = tile.getMapLocation();
                    if (tile.isPassable() && !tile.getPaint().isAlly() && rc.canAttack(tLoc)) {
                        boolean sec = tile.getMark() == PaintType.ALLY_SECONDARY;
                        rc.attack(tLoc, sec);
                        break;
                    }
                }
            }
        }

        Comms.robotReport(rc, nearestRuin);
    }

    private static void paintPatternTiles(RobotController rc, MapLocation ruin) throws GameActionException {
        if (!rc.isActionReady()) return;

        MapInfo[] patternTiles = rc.senseNearbyMapInfos(ruin, 8);
        for (MapInfo tile : patternTiles) {
            if (tile.getMark() != PaintType.EMPTY && tile.getMark() != tile.getPaint()) {
                boolean secondary = tile.getMark() == PaintType.ALLY_SECONDARY;
                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation(), secondary);
                    return;
                }
            }
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int round = rc.getRoundNum();

        Comms.processMessages(rc);

        MapLocation nearestAllyTower = Nav.findNearestAllyTower(rc);
        int paint = rc.getPaint();

        MapLocation rushTarget = enemyBase;
        if (Comms.hasEnemyTowerIntel(round)) {
            rushTarget = Comms.enemyTowerLoc;
        } else if (Comms.hasAttackOrder(round)) {
            rushTarget = Comms.attackTarget;
        }

        if (rushTarget != null && rc.canSenseLocation(rushTarget)) {
            RobotInfo targetInfo = rc.senseRobotAtLocation(rushTarget);
            
            if (targetInfo == null || !targetInfo.getType().isTowerType() || targetInfo.getTeam() == rc.getTeam()) {
                if (rushTarget.equals(enemyBase)) {
                    currentBaseIndex++;
                    if (currentBaseIndex < 3) {
                        enemyBase = possibleBases[currentBaseIndex];
                        rushTarget = enemyBase;
                    } else {
                        enemyBase = null;
                        rushTarget = null;
                    }
                } else if (rushTarget.equals(Comms.enemyTowerLoc)) {
                    Comms.enemyTowerLoc = null; 
                    rushTarget = enemyBase;
                }
            }
        }

        if (rc.isActionReady() && paint < Heuristics.SPLASHER_LOW_PAINT && nearestAllyTower != null) {
            int distToTower = myLoc.distanceSquaredTo(nearestAllyTower);
            if (distToTower <= 2) {
                RobotInfo towerInfo = rc.senseRobotAtLocation(nearestAllyTower);
                if (towerInfo != null) {
                    int towerPaint = towerInfo.getPaintAmount();
                    int availablePaint = Math.max(0, towerPaint - Heuristics.TOWER_PAINT_RESERVE);
                    int wantPaint = rc.getType().paintCapacity - paint;
                    int withdrawAmt = -Math.min(wantPaint, availablePaint);
                    if (withdrawAmt < 0 && rc.canTransferPaint(nearestAllyTower, withdrawAmt)) {
                        rc.transferPaint(nearestAllyTower, withdrawAmt);
                    }
                }
            }
        }

        int bestAtkScore = Integer.MIN_VALUE;
        MapLocation bestAtkTarget = null;

        MapLocation[] atkCandidates = new MapLocation[9];
        atkCandidates[0] = myLoc;
        for (int i = 0; i < 8; i++) {
            atkCandidates[i + 1] = myLoc.add(directions[i]);
        }

        if (rc.isActionReady()) {
            for (MapLocation loc : atkCandidates) {
                if (rc.canAttack(loc)) {
                    int score = Heuristics.scoreSplasherAttack(rc, loc);
                    if (score > bestAtkScore) {
                        bestAtkScore = score;
                        bestAtkTarget = loc;
                    }
                }
            }
            if (bestAtkTarget != null && bestAtkScore > 100) {
                rc.attack(bestAtkTarget);
            }
        }

        if (rc.isMovementReady()) {
            int bestMoveScore = Integer.MIN_VALUE;
            Direction bestMoveDir = null;
            for (Direction dir : directions) {
                if (rc.canMove(dir)) {
                    int score = Heuristics.scoreSplasherMove(rc, dir, rushTarget, nearestAllyTower, paint);
                    if (score > bestMoveScore) {
                        bestMoveScore = score;
                        bestMoveDir = dir;
                    }
                }
            }
            if (bestMoveDir != null) {
                rc.move(bestMoveDir);
            } else {
                Nav.randomMove(rc, rng);
            }
        }

        if (rc.isActionReady()) {
            MapLocation newLoc = rc.getLocation();
            bestAtkScore = Integer.MIN_VALUE;
            bestAtkTarget = null;
            atkCandidates[0] = newLoc;
            for (int i = 0; i < 8; i++) {
                atkCandidates[i + 1] = newLoc.add(directions[i]);
            }
            for (MapLocation loc : atkCandidates) {
                if (rc.canAttack(loc)) {
                    int score = Heuristics.scoreSplasherAttack(rc, loc);
                    if (score > bestAtkScore) {
                        bestAtkScore = score;
                        bestAtkTarget = loc;
                    }
                }
            }
            if (bestAtkTarget != null && bestAtkScore > 50) {
                rc.attack(bestAtkTarget);
            }
        }

        Comms.robotReport(rc, null);
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        int paint = rc.getPaint();

        Comms.processMessages(rc);

        MapLocation nearestTower = Nav.findNearestAllyTower(rc);

        MapInfo[] allTiles = rc.senseNearbyMapInfos();
        boolean hasEnemyPaintNearby = false;
        for (MapInfo tile : allTiles) {
            if (tile.getPaint().isEnemy()) {
                hasEnemyPaintNearby = true;
                break;
            }
        }

        int bestScore = Integer.MIN_VALUE;
        int bestAction = Heuristics.ACT_NOTHING;
        Direction bestDir = null;
        MapLocation bestTarget = null;
        int bestTransferAmt = 0;

        // Kandidat 1: Mop enemy paint tile
        if (rc.isActionReady()) {
            for (MapInfo tile : allTiles) {
                if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                    int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                    int score = Heuristics.SCORE_MOP_ENEMY_PAINT - dist;
                    if (score > bestScore) {
                        bestScore = score;
                        bestAction = Heuristics.ACT_MOP;
                        bestTarget = tile.getMapLocation();
                    }
                }
            }
        }

        // Kandidat 2: Mop swing (hit enemy robots)
        if (rc.isActionReady()) {
            for (Direction dir : CARDINAL_DIRS) {
                if (rc.canMopSwing(dir)) {
                    MapLocation swingCenter = myLoc.add(dir);
                    RobotInfo[] enemies = rc.senseNearbyRobots(swingCenter, 2, rc.getTeam().opponent());
                    if (enemies.length > 0) {
                        int score = Heuristics.SCORE_MOP_SWING_BASE
                                  + Heuristics.SCORE_MOP_SWING_PER_ENEMY * enemies.length;
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_MOP_SWING;
                            bestDir = dir;
                        }
                    }
                }
            }
        }

        // Kandidat 3: Transfer paint ke ally (bonus besar kalo ga ada musuh)
        if (rc.isActionReady() && paint > 10) {
            RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo ally : allies) {
                UnitType allyType = ally.getType();
                if ((allyType == UnitType.SOLDIER || allyType == UnitType.SPLASHER) && ally.getPaintAmount() < 80) {
                    int transferAmt = Math.min(paint - 10, 50);
                    if (rc.canTransferPaint(ally.getLocation(), transferAmt)) {
                        int score = Heuristics.SCORE_TRANSFER_PAINT;
                        if (!hasEnemyPaintNearby) {
                            score += Heuristics.SCORE_TRANSFER_NO_ENEMY_BONUS;
                        }
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_TRANSFER_PAINT;
                            bestTarget = ally.getLocation();
                            bestTransferAmt = transferAmt;
                        }
                    }
                }
            }
        }

        // Kandidat 4: Withdraw paint dari tower ally (kalo paint rendah)
        if (paint < Heuristics.MOPPER_LOW_PAINT && nearestTower != null) {
            int distToTower = myLoc.distanceSquaredTo(nearestTower);
            if (distToTower <= 2) {
                RobotInfo towerInfo = rc.senseRobotAtLocation(nearestTower);
                if (towerInfo != null) {
                    int towerPaint = towerInfo.getPaintAmount();
                    int availablePaint = Math.max(0, towerPaint - Heuristics.TOWER_PAINT_RESERVE);
                    int wantPaint = rc.getType().paintCapacity - paint;
                    int withdrawAmt = -Math.min(wantPaint, availablePaint);
                    if (withdrawAmt < 0 && rc.canTransferPaint(nearestTower, withdrawAmt)) {
                        int score = Heuristics.SCORE_WITHDRAW_PAINT;
                        if (score > bestScore) {
                            bestScore = score;
                            bestAction = Heuristics.ACT_WITHDRAW_PAINT;
                            bestTarget = nearestTower;
                            bestTransferAmt = withdrawAmt;
                        }
                    }
                }
            }
        }

        // Kandidat 5: Movement ke tiap direction
        for (Direction dir : directions) {
            if (rc.canMove(dir)) {
                int score = Heuristics.scoreMopperMove(rc, dir, nearestTower, paint, hasEnemyPaintNearby);
                if (score > bestScore) {
                    bestScore = score;
                    bestAction = Heuristics.ACT_MOVE;
                    bestDir = dir;
                }
            }
        }

        // EKSEKUSI: jalankan aksi dengan skor tertinggi
        switch (bestAction) {
            case Heuristics.ACT_MOP:
                rc.attack(bestTarget);
                break;
            case Heuristics.ACT_MOP_SWING:
                rc.mopSwing(bestDir);
                break;
            case Heuristics.ACT_TRANSFER_PAINT:
                rc.transferPaint(bestTarget, bestTransferAmt);
                break;
            case Heuristics.ACT_WITHDRAW_PAINT:
                rc.transferPaint(bestTarget, bestTransferAmt);
                break;
            case Heuristics.ACT_MOVE:
                rc.move(bestDir);
                break;
            default:
                Nav.randomMove(rc, rng);
                break;
        }

        // Bonus action
        if (rc.isActionReady()) {
            myLoc = rc.getLocation();
            boolean acted = false;

            MapInfo[] postMoveTiles = rc.senseNearbyMapInfos();
            for (MapInfo tile : postMoveTiles) {
                if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                    acted = true;
                    break;
                }
            }

            if (!acted && rc.getPaint() > 10) {
                RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
                for (RobotInfo ally : allies) {
                    UnitType allyType = ally.getType();
                    if ((allyType == UnitType.SOLDIER || allyType == UnitType.SPLASHER) && ally.getPaintAmount() < 80) {
                        int transferAmt = Math.min(rc.getPaint() - 10, 50);
                        if (rc.canTransferPaint(ally.getLocation(), transferAmt)) {
                            rc.transferPaint(ally.getLocation(), transferAmt);
                            break;
                        }
                    }
                }
            }
        }

        Comms.robotReport(rc, null);
    }
}

