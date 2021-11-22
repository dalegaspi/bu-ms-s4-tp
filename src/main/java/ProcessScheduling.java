import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Process scheduler implementation
 *
 * All the required classes/interfaces are in this file for simplicity.
 *
 * Tested to work with Java 11.
 *
 * @author dlegaspi@bu.edu
 */
public class ProcessScheduling {

	/**
	 * the defaults for input/output filenames
	 */
	public static final String DEFAULT_INPUT_FILENAME = "process_scheduling_input.txt";
	public static final String DEFAUL_OUTPUT_FILENAME = "process_scheduling_output.txt";

	/**
	 * Process object
	 */
	public static class Process {
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
		 * @param id
		 *            process ID
		 * @param priority
		 *            priority
		 * @param duration
		 *            duration
		 * @param arrivalTime
		 *            arrival time
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
		 * @param priority
		 *            the new priority
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

		public int getRunStartTime() {
			return runStartTime;
		}

		public void setRunStartTime(int runStartTime) {
			this.runStartTime = runStartTime;
		}

		@Override
		public String toString() {
			return String.format(
			// @formatter:off
					"Process id = %d%n" +
					"       Priority = %d%n" +
					"       Arrival = %d%n" +
					"       Duration = %d",
			// @formatter:on
					getId(), getPriority(), getArrivalTime(), getDuration());
		}
	}

	/**
	 * Process list
	 *
	 * subclass ArrayDeque to be able to define a constructor that takes an unsorted
	 * list and sort it by arrival date
	 *
	 * @see java.util.ArrayDeque
	 */
	static class ProcessList extends ArrayDeque<Process> {

		private int originalListSize;

		/**
		 * returns a new sorted Process list based arrival date on the given list
		 *
		 * @param processes
		 * @return
		 */
		private static Collection<Process> sortListByArrival(Collection<Process> processes) {
			return processes.stream().sorted(Process.getArrivalComparator()).collect(Collectors.toList());
		}

		/**
		 * private constructor; clones the list
		 *
		 * @param processes
		 */
		private ProcessList(Collection<Process> processes) {
			// sort the list by arrival (asc) then assign to the internal collection
			super(sortListByArrival(processes));
			this.originalListSize = processes.size();
		}

		/**
		 * Process list from file convenience static method. It assumes that each line
		 * has 4 integers.
		 *
		 * @param fname
		 *            filename
		 * @return the process list
		 * @throws IOException
		 *             any error in file processing
		 */
		public static ProcessList getProcessListFromFile(String fname) throws IOException {
			try (var ps = Files.lines(Paths.get(fname))) {
				// @formatter:off
				return new ProcessList(ps.map(p -> p.split(" "))
						.map(strings -> Arrays.stream(strings)
								.map(Integer::valueOf)
								.collect(Collectors.toList())
								.toArray(new Integer[4]))
						.map(Process::new)
						.collect(Collectors.toList()));
				// @formatter:on
			}
		}

		/**
		 * the original size of the process list
		 *
		 * @return
		 */
		public int getOriginalListSize() {
			assert originalListSize > 0;
			return this.originalListSize;
		}

		@Override
		public String toString() {
			return this.stream().map(p -> String.format("Id = %d, priority = %d, duration = %d, arrival = %d",
					p.getId(), p.getPriority(), p.getDuration(), p.getArrivalTime())).collect(Collectors.joining("\n"));
		}
	}

	/**
	 * Scheduler object
	 */
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	static class ProcessScheduler {
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
		 * @return
		 */
		public static PriorityQueue<Process> reinsertIntoPriorityQueue(PriorityQueue<Process> queue, Process p) {
			queue.remove(p);
			queue.add(p);
			return queue;
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
			this(processes, DEFAULT_MAX_WAIT_TIME, Logger::log);
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
			this(processes, maxWaitTime, Logger::log);
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
						// process is waiting too long is true if (currentTime - process.arrivalTime) >= maxWaitTime)
						.filter(p -> calculateWaitTime(getCurrentTime(), p) > getMaxWaitTime())
						.collect(Collectors.toList());

				processesToUpdate.forEach(p -> {
					var currentProcessWaitTime = calculateWaitTime(getCurrentTime(), p);

					// if process is waiting too long i.e., (currentTime - process.arrivalTime) >= maxWaitTime),
					// decrement its priority by 1
					log("PID = %d, wait time = %d, current priority = %d", p.getId(), currentProcessWaitTime,
							p.getPriority());
					// adjust priority
					p.decrementPriorityByOne();
					log("PID = %d, new priority = %d", p.getId(), p.getPriority());

					// reinsert adjust process in the queue because PriorityQueue only recomputes on enqueue
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
		 *
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
					log("%nD becomes empty at time at time %d", getCurrentTime());

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

	/**
	 * Poor man's java.util.logging.Logger
	 */
	public static class Logger {
		private static Logger instance;

		private PrintWriter output;

		/**
		 * our private constructor
		 *
		 * @param fname
		 * @throws IOException
		 */
		private Logger(String fname) throws IOException {
			output = new PrintWriter(new FileWriter(fname), true);
		}

		/**
		 * Initialize our logger
		 *
		 * @param fname
		 * @throws IOException
		 */
		public static void init(String fname) throws IOException {
			instance = new Logger(fname);
		}

		/**
		 * The actual logging method
		 * @param format format
		 * @param args args
		 */
		public static void log(String format, Object... args) {
			System.out.printf(format + "%n", args);
			if (instance != null)
				instance.output.println(String.format(format, args));
		}
	}

	/**
	 * This is just a method to test priority queue behavior
	 */
	public static void testPriorityQueueBehavior() {
		var pq = ProcessScheduler.createPriorityQueue();

		pq.addAll(IntStream.rangeClosed(1, 10).mapToObj(i -> new Process(i, i, i, i)).collect(Collectors.toList()));

		var pl = new ArrayList<Process>();

		for (int i = 0; i < 5; i++)
			pl.add(pq.remove());

		// modify the priority of the top one
		var modifiedPriority = pq.peek();
		modifiedPriority.setPriority(100000);

		ProcessScheduler.reinsertIntoPriorityQueue(pq, modifiedPriority);

		// reinsert the rest
		while (!pq.isEmpty())
			pl.add(pq.remove());

		// assert that the modified priority is the last one
		assert pl.get(pl.size() - 1).getId() == modifiedPriority.getId();
	}

	/**
	 * entry point
	 *
	 * @param args
	 *            command line args
	 */
	public static void main(String[] args) throws IOException {
		// testPriorityQueueBehavior();

		String input_fname = args.length > 0 ? args[0] : DEFAULT_INPUT_FILENAME;
		String output_fname = args.length > 1 ? args[1] : DEFAUL_OUTPUT_FILENAME;
		Logger.init(output_fname);

		var plist = ProcessList.getProcessListFromFile(input_fname);
		Logger.log(plist.toString());

		var scheduler = new ProcessScheduler(plist);
		scheduler.simulate();

		Logger.log("Total wait time = %.1f", scheduler.getTotalWaitTime());
		Logger.log("Average wait time = %.1f", scheduler.getAverageWaitTime());
	}
}
