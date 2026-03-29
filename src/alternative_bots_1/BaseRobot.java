package alternative_bots_1;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;

public abstract class BaseRobot {

    protected final RobotController rc;
    protected final StackManager goals;
    protected final MapData mapData;
    protected final BugNav nav;

    protected static final Direction[] DIRS = {
        Direction.NORTH, 
        Direction.NORTHEAST, 
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH, 
        Direction.SOUTHWEST, 
        Direction.WEST, 
        Direction.NORTHWEST
    };

    protected MapLocation battlefront = null;
    protected final MapLocation mapCenter;
    protected static final int TOWER_PAINT_RESERVE = 100;

    public BaseRobot(RobotController rc, Task defaultTask) {
        this.rc = rc;
        this.goals = new StackManager(defaultTask);
        this.mapData = new MapData(rc.getMapWidth(), rc.getMapHeight());
        this.nav = new BugNav(rc);
        this.mapCenter = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);

        try { battlefront = Comms.readBattlefront(rc); } catch (Exception e) {}
    }

    public final void run() throws GameActionException {
        mapData.updateNewlyVisible(rc);
        executeTurn();
    }

    protected abstract void executeTurn() throws GameActionException;

    protected void executeRefill() throws GameActionException {
        MapLocation towerLoc = findNearestFriendlyTower();

        if (towerLoc == null) {
            nav.setTarget(mapCenter);
            nav.move();
            return;
        }

        nav.setTarget(towerLoc);
        nav.move();

        if (rc.getLocation().isAdjacentTo(towerLoc)) {
            RobotInfo tower = rc.senseRobotAtLocation(towerLoc);
            if (tower != null) {
                int towerPaint = tower.getPaintAmount();
                int available = towerPaint - TOWER_PAINT_RESERVE;
                if (available > 0) {
                    int capacity = rc.getType().paintCapacity;
                    int needed   = capacity - rc.getPaint();
                    int toTake   = Math.min(needed, available);
                    if (toTake > 0 && rc.canTransferPaint(towerLoc, -toTake)) {
                        rc.transferPaint(towerLoc, -toTake);
                    }
                }
            }
            goals.pop();
        }
    }

    protected RobotInfo pickBestTarget() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length == 0) return null;

        RobotInfo bestMobile = null;
        RobotInfo bestTower  = null;

        for (int i = enemies.length; --i >= 0;) {
            RobotInfo e = enemies[i];
            if (e.getType().isTowerType()) {
                if (bestTower == null || e.getHealth() < bestTower.getHealth())
                    bestTower = e;
            } else {
                if (bestMobile == null || e.getHealth() < bestMobile.getHealth())
                    bestMobile = e;
            }
        }
        return bestMobile != null ? bestMobile : bestTower;
    }

    protected void reportEnemies() throws GameActionException {
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            Comms.reportBattlefront(rc, enemies[0].getLocation());
        }
    }

    protected void paintUnderfoot() throws GameActionException {
        MapLocation me = rc.getLocation();
        if (rc.canSenseLocation(me)) {
            MapInfo tile = rc.senseMapInfo(me);
            if (!tile.getPaint().isAlly() && rc.canAttack(me)) {
                rc.attack(me);
            }
        }
    }

    protected MapLocation findNearestRuin() throws GameActionException {
        MapLocation[] ruins = rc.senseNearbyRuins(-1);
        if (ruins.length == 0) return null;

        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = ruins.length; --i >= 0;) {
            RobotInfo occ = rc.senseRobotAtLocation(ruins[i]);
            if (occ != null) continue;
            int d = me.distanceSquaredTo(ruins[i]);
            if (d < bestDist) { bestDist = d; best = ruins[i]; }
        }
        return best;
    }

    protected MapLocation findNearestFriendlyTower() throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (int i = allies.length; --i >= 0;) {
            if (allies[i].getType().isTowerType()) {
                int d = me.distanceSquaredTo(allies[i].getLocation());
                if (d < bestDist) { bestDist = d; best = allies[i].getLocation(); }
            }
        }
        return best;
    }

    protected boolean tryRandomMove() throws GameActionException {
        int start = (rc.getID() + rc.getRoundNum()) % 8;
        for (int i = 0; i < 8; i++) {
            Direction d = DIRS[(start + i) % 8];
            if (rc.canMove(d)) { rc.move(d); return true; }
        }
        return false;
    }

    protected void explore() throws GameActionException {
        int mapW = rc.getMapWidth(), mapH = rc.getMapHeight();
        MapLocation me = rc.getLocation();

        if (rc.getRoundNum() < 40 && me.distanceSquaredTo(mapCenter) > 25) {
            nav.setTarget(mapCenter);
            if (nav.move()) return;
        }

        int hash = (rc.getID() * 31 + rc.getRoundNum() / 20) % 8;
        Direction dir = DIRS[hash];
        int ex = Math.max(0, Math.min(mapW - 1, me.x + dir.getDeltaX() * 10));
        int ey = Math.max(0, Math.min(mapH - 1, me.y + dir.getDeltaY() * 10));
        nav.setTarget(new MapLocation(ex, ey));
        if (!nav.move()) tryRandomMove();
    }
}
