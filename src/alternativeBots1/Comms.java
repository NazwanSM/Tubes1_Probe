package alternativeBots1;

import battlecode.common.*;

public class Comms {

    static final int MSG_ENEMY_SPOTTED = 0; 
    static final int MSG_RUIN_FOUND    = 1;
    static final int MSG_ATTACK_HERE   = 2;
    static final int MSG_ENEMY_TOWER   = 3;

    static MapLocation lastEnemySpotted = null;
    static int lastEnemyRound = -100;

    static MapLocation lastRuinFound = null;
    static int lastRuinRound = -100;

    static MapLocation attackTarget = null;
    static int attackTargetRound = -100;

    static MapLocation enemyTowerLoc = null;
    static int enemyTowerRound = -100;

    static final int INTEL_EXPIRE = 10;

    static int encode(int type, MapLocation loc, int extra) {
        return (type << 28) | (loc.x << 20) | (loc.y << 12) | (extra & 0xFFF);
    }

    static int getMsgType(int msg) {
        return (msg >>> 28) & 0xF;
    }

    static MapLocation getMsgLoc(int msg) {
        int x = (msg >>> 20) & 0xFF;
        int y = (msg >>> 12) & 0xFF;
        return new MapLocation(x, y);
    }

    static int getMsgExtra(int msg) {
        return msg & 0xFFF;
    }

    static void processMessages(RobotController rc) throws GameActionException {
        int currentRound = rc.getRoundNum();
        int startRound = Math.max(1, currentRound - 4);
        for (int r = startRound; r <= currentRound; r++) {
            Message[] msgs = rc.readMessages(r);
            for (Message m : msgs) {
                int data = m.getBytes();
                int type = getMsgType(data);
                MapLocation loc = getMsgLoc(data);
                int round = m.getRound();

                switch (type) {
                    case MSG_ENEMY_SPOTTED:
                        if (round > lastEnemyRound) {
                            lastEnemySpotted = loc;
                            lastEnemyRound = round;
                        }
                        break;
                    case MSG_RUIN_FOUND:
                        if (round > lastRuinRound) {
                            lastRuinFound = loc;
                            lastRuinRound = round;
                        }
                        break;
                    case MSG_ATTACK_HERE:
                        if (round > attackTargetRound) {
                            attackTarget = loc;
                            attackTargetRound = round;
                        }
                        break;
                    case MSG_ENEMY_TOWER:
                        if (round > enemyTowerRound) {
                            enemyTowerLoc = loc;
                            enemyTowerRound = round;
                        }
                        break;
                }
            }
        }
    }

    static void robotReport(RobotController rc, MapLocation nearestRuin) throws GameActionException {
        MapLocation towerLoc = Nav.findNearestAllyTower(rc);
        if (towerLoc == null) return;

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo target = enemies[0];
            boolean isTower = false;
            for (RobotInfo enemy : enemies) {
                if (enemy.getType().isTowerType()) {
                    target = enemy;
                    isTower = true;
                    break;
                }
            }

            int msgType = isTower ? MSG_ENEMY_TOWER : MSG_ENEMY_SPOTTED;
            int msg = encode(msgType, target.getLocation(), enemies.length);
            if (rc.canSendMessage(towerLoc, msg)) {
                rc.sendMessage(towerLoc, msg);
                return;
            }
        }

        if (nearestRuin != null) {
            int msg = encode(MSG_RUIN_FOUND, nearestRuin, 0);
            if (rc.canSendMessage(towerLoc, msg)) {
                rc.sendMessage(towerLoc, msg);
            }
        }
    }

    static void towerRelayAndCommand(RobotController rc) throws GameActionException {
        int round = rc.getRoundNum();
        int msgSent = 0;

        if (enemyTowerLoc != null && round - enemyTowerRound <= 5 && msgSent < 18) {
            int msg = encode(MSG_ENEMY_TOWER, enemyTowerLoc, 0);
            if (rc.canBroadcastMessage()) {
                rc.broadcastMessage(msg);
                msgSent++;
            }
        }

        if (lastEnemySpotted != null && round - lastEnemyRound <= 5 && msgSent < 18) {
            int msg = encode(MSG_ENEMY_SPOTTED, lastEnemySpotted, 0);
            if (rc.canBroadcastMessage()) {
                rc.broadcastMessage(msg);
                msgSent++;
            }
        }

        if (lastRuinFound != null && round - lastRuinRound <= 5 && msgSent < 18) {
            int msg = encode(MSG_RUIN_FOUND, lastRuinFound, 0);
            if (rc.canBroadcastMessage()) {
                rc.broadcastMessage(msg);
                msgSent++;
            }
        }

        MapLocation cmdTarget = null;
        if (enemyTowerLoc != null && round - enemyTowerRound <= INTEL_EXPIRE) {
            cmdTarget = enemyTowerLoc;
        } else if (lastEnemySpotted != null && round - lastEnemyRound <= INTEL_EXPIRE) {
            cmdTarget = lastEnemySpotted;
        }

        if (cmdTarget != null) {
            int msg = encode(MSG_ATTACK_HERE, cmdTarget, 0);
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
            for (RobotInfo ally : nearbyAllies) {
                if (msgSent >= 19) break; // sisain 1 slot jaga-jaga
                if (ally.getType().isRobotType()) {
                    if (rc.canSendMessage(ally.getLocation(), msg)) {
                        rc.sendMessage(ally.getLocation(), msg);
                        msgSent++;
                    }
                }
            }
        }
    }

    static boolean hasRecentEnemyIntel(int currentRound) {
        return lastEnemySpotted != null && currentRound - lastEnemyRound <= INTEL_EXPIRE;
    }

    static boolean hasRecentRuinIntel(int currentRound) {
        return lastRuinFound != null && currentRound - lastRuinRound <= INTEL_EXPIRE;
    }

    static boolean hasAttackOrder(int currentRound) {
        return attackTarget != null && currentRound - attackTargetRound <= INTEL_EXPIRE;
    }

    static boolean hasEnemyTowerIntel(int currentRound) {
        return enemyTowerLoc != null && currentRound - enemyTowerRound <= INTEL_EXPIRE;
    }
}
