
import TSim.CommandException;
import TSim.SensorEvent;
import TSim.TSimInterface;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * One Train instance for each Train. Call start() to start the train in a
 * seperate thread (as with anything extending Thread)
 */
public final class Train extends Thread implements Runnable {

    private RailMap railMap;
    private Map<Sensor, Runnable> pendingActions;
    private int currentVelocity;
    private int maxVelocity;
    private int id;
    private boolean goingForwards;
    //private int x0, y0;
    private Point point0;
    private int prevDir;

    public Train(RailMap railMap, int maxVelocity, int id) {
        this.railMap = railMap;
        this.maxVelocity = maxVelocity;
        this.id = id;
        this.point0 = railMap.trainStartPos(id);

        this.pendingActions = new HashMap<Sensor, Runnable>();
        goingForwards = true;
        setMaxVelocity();

    }

    /**
     * Run an action next time coming to a sensor (or leaving the sensor?)
     */
    // TODO: decide wheater it is for entering or leaving sensor
    public void addOneTimeAction(Sensor s, final Runnable action) {
        if (pendingActions.containsKey(s)) {
            // already exists one, must concatenate existing with new action.
            final Runnable prevAction = pendingActions.get(s);
            pendingActions.put(s, Tools.plusActions(prevAction, action));
        } else {
            pendingActions.put(s, action);
        }
    }

    @Override
    public void run() {
        // initialize so the start-segment is taken
        trainAcquireSemaphor(railMap.getSegmentSemaphor(point0));

        while (true) {
            SensorEvent event = getSensorEvent();

            final int x = event.getXpos();
            final int y = event.getYpos();
            final Point point = new Point(x, y);
            Sensor sensor =
                    railMap.getSensorArray()[x][y];
            if (event.getStatus() == SensorEvent.INACTIVE) {
            } else {

                if (pendingActions.containsKey(sensor)) {
                    pendingActions.get(sensor).run();
                    pendingActions.remove(sensor);
                }
                int direction =
                        railMap.getDirectionTrainCameWith(point0, point, prevDir);
                say("Hitted with direction " + direction);
                sensor.getAction(direction, this);
                point0 = Point.createNewAndMove(point, direction);
                prevDir = direction;
            }
        }
    }

    public void setVelocity(int velocity) {
        say("Setting velocity " + velocity);
        TSimInterface iface = TSimInterface.getInstance();
        try {
            iface.setSpeed(id, velocity);
        } catch (CommandException ex) {
            sayImportant("error setting velocity!!!!!!!!!");
        }
        currentVelocity = velocity;
    }

    public int getLastSetVelocity() {
        return currentVelocity;
    }


    public void stopTrain() {
        say("Stopping train ...");
        setVelocity(0);
    }

    public void stopWaitTurnAround() {

        setVelocity(goingForwards ? 1 : -1);
        SensorEvent se = getSensorEvent();
        if(se.getStatus() != SensorEvent.INACTIVE){
            sayImportant("expected INACTIVE on turnaround");
        }
        stopTrain();
        goingForwards ^= true; // turn direction
        try {
            sleep(1000);
        } catch (InterruptedException ex) {
            sayImportant("train got interrupted during sleep!");
        }
        setMaxVelocity();
    }

    public boolean isMovingUpOrRight() {
        return true;
    }

    public void setMaxVelocity() {
        say("Setting max velocity ...");
        setVelocity((goingForwards ? 1 : -1) * maxVelocity);
    }

    public void trainAcquireSemaphor(Semaphore s) {
        try {
            s.acquire();
            say("Aquired semaphore " + s);
        } catch (InterruptedException ex) {
            sayImportant("error when aquire semaphore " + ex.getMessage());
        }
    }

    public void waitIfTakenThenGo(Semaphore s) {
        boolean aquired = s.tryAcquire();
        if (!aquired) {
            stopTrain();
            trainAcquireSemaphor(s);
            setMaxVelocity();
        }
    }

    void releaseSemaphor(Semaphore s) {
        s.release();
    }

    private SensorEvent getSensorEvent() {
        SensorEvent event = null;
        try {
            event = TSimInterface.getInstance().getSensor(id);
        } catch (CommandException ex) {
            sayImportant("interface didn't allow getting sensor!!!");
        } catch (InterruptedException ex) {
            sayImportant("train got interrupted waiting for sensor!");
        }

        return event;
    }


    /**
     * make train say something to stderr
     */
    public void say(String msg) {
//        System.err.println("Train " + id + " says: " + msg);
    }


    /**
     * make train say something important to stderr
     */
    public void sayImportant(String msg) {
        System.err.println("¤¤¤¤¤¤¤¤¤¤¤¤¤ Train " + id + " says: " + msg + " ¤¤¤¤¤¤¤¤¤¤¤¤¤");
    }
}
