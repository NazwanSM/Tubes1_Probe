package mainBot;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.UnitType;

public class RobotPlayer {

    private static BaseRobot robot = null;
    private static Tower tower = null;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        UnitType type = rc.getType();

        if (type.isTowerType()) {
            tower = new Tower(rc);
        } else {
            switch (type) {
                case SOLDIER:  robot = new Soldier(rc);  break;
                case SPLASHER: robot = new Splasher(rc); break;
                case MOPPER:   robot = new Mopper(rc);   break;
                default: break;
            }
        }

        while (true) {
            try {
                if (type.isTowerType()) {
                    tower.run();
                } else if (robot != null) {
                    robot.run();
                }
            } catch (GameActionException e) {
                System.out.println("GAE: " + e.getMessage());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("ERR: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }
}

