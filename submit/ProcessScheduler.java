import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Scheduler
 *
 * This is the main object that takes the Process from ProcessList and schedules
 * them for running (simulation)
 *
 * @author dlegaspi@bu.edu
 * @see Process
 * @see ProcessList
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
class ProcessScheduler {
	// default max wait time
	public static int DEFAULT_MAX_WAIT_TIME = 30;

	private int currentTime = 0;
	private Optional<Process> runningProcess = Optional.empty();
	private PriorityQueue<Process> pqueue;
	private final ProcessList processes;
	private final int maxWaitTime;
	private int totalWaitTime = 0;
	private final Consumer<String> eventHandler;

	/**
	 * creates a priority queue
	 *
	 * @return a new priority queue
	 */
	public static PriorityQueue<Process> createPriorityQueue() {
		return new PriorityQueue<>(Process.getPriorityComparator());
	}

	/**
	 * reinsert into priority queue; usually for p that changed priority
	 *
	 * @param queue
	 *            the priority queue
	 * @param p
	 *            the process to reinsert
	 */
	public static void reinsertIntoPriorityQueue(PriorityQueue<Process> queue, Process p) {
		queue.remove(p);
		queue.add(p);
	}

	/**
	 * reset times and other related stuff to throught he whole thing again
	 */
	public void reset() {
		currentTime = 0;
		totalWaitTime = 0;
		pqueue = createPriorityQueue();
		runningProcess = Optional.empty();
	}

	/**
	 * increments the scheduler's clock/time
	 *
	 * @param delta
	 *            delta
	 */
	private void incrementCurrentTime(int delta) {
		currentTime += delta;
	}

	/**
	 * Overload to increment time by 1 unit
	 */
	private void incrementCurrentTime() {
		incrementCurrentTime(1);
	}

	/**
	 * current time as kept by the scheduler
	 *
	 * @return the time
	 */
	public int getCurrentTime() {
		return currentTime;
	}

	/**
	 * The main constructor
	 *
	 * @param processes
	 *            process list
	 * @param maxWaitTime
	 *            max wait time
	 * @param eventHandler
	 *            event handler (logging)
	 */
	public ProcessScheduler(ProcessList processes, int maxWaitTime, Consumer<String> eventHandler) {
		this.processes = processes;
		this.maxWaitTime = maxWaitTime;
		this.eventHandler = eventHandler;
	}

	/**
	 * Convenience overloaded constructor with applied defaults
	 *
	 * @param processes
	 *            the process list
	 */
	public ProcessScheduler(ProcessList processes) {
		this(processes, DEFAULT_MAX_WAIT_TIME, ProcessScheduling.Logger::log);
	}

	/**
	 * Convenience overloaded constructor with applied defaults
	 *
	 * @param processes
	 *            the process list
	 * @param maxWaitTime
	 *            the max wait time
	 */
	public ProcessScheduler(ProcessList processes, int maxWaitTime) {
		this(processes, maxWaitTime, ProcessScheduling.Logger::log);
	}

	/**
	 * @return true if running
	 */
	public boolean isRunning() {
		return runningProcess.isPresent();
	}

	/**
	 * changes the run state of the scheduler based on current time
	 */
	public void runProcess(Process process) {
		process.setRunStartTime(getCurrentTime());
		runningProcess = Optional.of(process);
	}

	/**
	 * stop run state of scheduler based on current time
	 */
	public Process stopRunning() {
		assert runningProcess.isPresent();
		var currentlyRunningProcess = runningProcess.get();
		runningProcess = Optional.empty();
		return currentlyRunningProcess;
	}

	/**
	 * get the running process
	 *
	 * @return the running process, if any
	 */
	public Optional<Process> getRunningProcess() {
		return runningProcess;
	}

	/**
	 * if the scheduler is running a process, check if it's finished: currentTime -
	 * p.startTime >= p.getDuration
	 *
	 * @return true if running process is finished
	 */
	public boolean isRunningProcessFinished() {
		assert isRunning();
		return runningProcess.map(p -> getCurrentTime() - p.getRunStartTime() >= p.getDuration()).orElse(false);
	}

	/**
	 * total wait times
	 *
	 * @return the total
	 */
	public float getTotalWaitTime() {
		return (float) totalWaitTime;
	}

	/**
	 * add to total wait time
	 *
	 * @param delta
	 */
	public void addTotalWaitTime(int delta) {
		totalWaitTime += delta;
	}

	/**
	 * computes average wait time based on the accumulated total / # of processes
	 *
	 * @return the ave wait time
	 */
	public float getAverageWaitTime() {
		return getTotalWaitTime() / processes.getOriginalListSize();
	}

	/**
	 * Calculation of a wait time of a process
	 *
	 * @param referenceTime
	 *            reference time
	 * @param p
	 *            the process
	 * @return the wait time for the process
	 */
	public static int calculateWaitTime(int referenceTime, Process p) {
		return referenceTime - p.getArrivalTime();
	}

	/**
	 * the max wait time
	 *
	 * @return max wait time
	 */
	public int getMaxWaitTime() {
		return maxWaitTime;
	}

	/**
	 * handler by logging, it's like printf
	 *
	 * @param format
	 *            string format
	 * @param args
	 *            args
	 */
	private void log(String format, Object... args) {
		eventHandler.accept(String.format(format, args));
	}

	/**
	 * adjust process priorities based on max wait times and current time
	 */
	private void adjustAndReportProcessPrioritiesInQueue() {
		log("%nUpdate priority:");
		if (!pqueue.isEmpty()) {
			// determine the processes that have been waiting too long
			var processesToUpdate = pqueue.stream()
					// process is waiting too long if (currentTime - process.arrivalTime) >=
					// maxWaitTime)
					.filter(p -> calculateWaitTime(getCurrentTime(), p) > getMaxWaitTime())
					.collect(Collectors.toList());

			processesToUpdate.forEach(p -> {
				// if process is waiting too long, i.e.:
				// (currentTime - process.arrivalTime) >= maxWaitTime)
				// decrement its priority by 1
				log("PID = %d, wait time = %d, current priority = %d", p.getId(),
						calculateWaitTime(getCurrentTime(), p), p.getPriority());

				// adjust priority
				p.decrementPriorityByOne();
				log("PID = %d, new priority = %d", p.getId(), p.getPriority());

				// reinsert in the queue as PriorityQueue only recomputes on enqueue
				reinsertIntoPriorityQueue(pqueue, p);
			});
		}
		log("");
	}

	/**
	 * Log that process is removed from queue to start running
	 *
	 * @param process
	 */
	public void reportProcessRemovedFromQueue(Process process) {
		log("Process removed from queue is %d, at time %d, wait time = %d, Total wait time = %.1f", process.getId(),
				getCurrentTime(), process.getWaitTime(), getTotalWaitTime());
		log("%s", process.toString());
	}

	/**
	 * Log that process is finished
	 *
	 * @param process
	 */
	public void reportProcessFinished(Process process) {
		log("Process %d is finished at time %d", process.getId(), getCurrentTime());
	}

	/**
	 * run one process
	 */
	private void runOneProcess() {
		assert !isRunning();

		// if we're not running anything, run something
		var processToRun = pqueue.remove();

		// calculate and save the process wait time
		var waitTime = calculateWaitTime(getCurrentTime(), processToRun);
		processToRun.setWaitTime(waitTime);
		addTotalWaitTime(waitTime);

		// run process
		reportProcessRemovedFromQueue(processToRun);
		runProcess(processToRun);
	}

	/**
	 * stop running process and adjust priorities
	 */
	private void stopRunningProcessAndAdjustPriorities() {
		assert isRunning();
		var stoppedProcess = stopRunning();
		reportProcessFinished(stoppedProcess);
		adjustAndReportProcessPrioritiesInQueue();
	}

	/**
	 * The scheduler simulation
	 */
	public void simulate() {
		log("%nMaximum wait time = %d%n", getMaxWaitTime());
		reset();

		while (!processes.isEmpty()) {
			// get the top process without removing
			var top = processes.peek();

			// remove from list then add to priority queue
			if (top.getArrivalTime() <= getCurrentTime())
				pqueue.add(processes.remove());

			// if pqueue is not empty run one process if not running
			if (!pqueue.isEmpty() && !isRunning())
				runOneProcess();

			if (processes.isEmpty())
				log("%nD becomes empty at time at time %d%n", getCurrentTime());

			if (isRunning() && isRunningProcessFinished())
				stopRunningProcessAndAdjustPriorities();
			else
				incrementCurrentTime();
		}

		// run the rest of the processes that are still in the queue
		while (!pqueue.isEmpty()) {
			// run one process if not running
			if (!isRunning())
				runOneProcess();

			if (isRunning() && isRunningProcessFinished())
				stopRunningProcessAndAdjustPriorities();
			else
				incrementCurrentTime();
		}
	}
}