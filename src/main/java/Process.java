import java.util.Comparator;

/**
 * Process
 *
 * The main object that represents the process to be run
 *
 * @author dlegaspi@bu.edu
 */
public class Process {
    public static int NOT_RUNNING = -1;

    private final int id;
    private int priority;
    private final int arrivalTime;
    private final int duration;
    private int waitTime;
    private int runStartTime;

    /**
     * Constructor to take array of ids to create the Process
     *
     * @param ids args array of length 4
     */
    public Process(Integer... ids) {
        this(ids[0], ids[1], ids[2], ids[3]);
    }

    /**
     * The main constructor
     *
     * @param id          process ID
     * @param priority    priority
     * @param duration    duration
     * @param arrivalTime arrival time
     */
    public Process(int id, int priority, int duration, int arrivalTime) {
        this.id = id;
        this.priority = priority;
        this.arrivalTime = arrivalTime;
        this.duration = duration;
        this.waitTime = 0;
        this.runStartTime = NOT_RUNNING;
    }

    /**
     * gets the id
     *
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * gets the priority
     *
     * @return the priority
     */
    public int getPriority() {
        return priority;
    }

    /**
     * set the process priority
     *
     * @param priority the new priority
     */
    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * overload to decrement priority by one (increase priority)
     */
    public void decrementPriorityByOne() {
        setPriority(getPriority() - 1);
    }

    /**
     * get the arrival time
     *
     * @return arrival time
     */
    public int getArrivalTime() {
        return arrivalTime;
    }

    /**
     * get the duration
     *
     * @return the duration
     */
    public int getDuration() {
        return duration;
    }

    /**
     * The process wait time (removed from scheduler queue)
     *
     * @return the process wait time
     */
    public int getWaitTime() {
        return waitTime;
    }

    /**
     * set the process wait time (removed from scheduler queue)
     *
     * @param waitTime the new wait time
     */
    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    /**
     * The comparator for process using the priority field
     *
     * @return the comparator
     */
    public static Comparator<Process> getPriorityComparator() {
        return Comparator.comparingInt(Process::getPriority);
    }

    /**
     * The comparator for process using the arrival field
     *
     * @return the comparator
     */
    public static Comparator<Process> getArrivalComparator() {
        return Comparator.comparingInt(Process::getArrivalTime);
    }

    /**
     * get current run start time
     *
     * @return the current start r
     */
    public int getRunStartTime() {
        return runStartTime;
    }

    /**
     * set current run start time
     *
     * @param runStartTime the start time
     */
    public void setRunStartTime(int runStartTime) {
        this.runStartTime = runStartTime;
    }

    @Override
    public String toString() {
        // @formatter:off
        return String.format(
                        "Process id = %d%n" + "       Priority = %d%n" + "       Arrival = %d%n"
                                        + "       Duration = %d",
                        getId(), getPriority(), getArrivalTime(), getDuration());
        // @formatter:on
    }
}
