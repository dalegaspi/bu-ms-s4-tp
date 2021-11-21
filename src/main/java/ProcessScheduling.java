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
	 * the defaults
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
		 * @param ids
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

		public int getId() {
			return id;
		}

		public int getPriority() {
			return priority;
		}

		/**
		 * set the process priority; using synchronized is a bit superfluous here, but
		 * for completeness we are taking into account that per term project description
		 * setting priority can be performed at each logical time (i.e., in a separate
		 * thread)
		 *
		 * @param priority
		 *            the new priority
		 */
		public synchronized void setPriority(int priority) {
			this.priority = priority;
		}

		public int getArrivalTime() {
			return arrivalTime;
		}

		public int getDuration() {
			return duration;
		}

		public int getWaitTime() {
			return waitTime;
		}

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

		public void stop() {
			this.runStartTime = NOT_RUNNING;
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
	static class Scheduler {
		// default max wait time
		public static int DEFAULT_MAX_WAIT_TIME = 30;

		private int currentTime = 0;
		private Optional<Process> runningProcess = Optional.empty();
		private PriorityQueue<Process> pqueue;
		private float totalWaitTime;
		private ProcessList processes;
		private final int maxWaitTime;
		private Consumer<String> eventHandler;

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
			totalWaitTime = 0;
			currentTime = 0;
			this.pqueue = createPriorityQueue();
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
		public Scheduler(ProcessList processes, int maxWaitTime, Consumer<String> eventHandler) {
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
		public Scheduler(ProcessList processes) {
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
		public Scheduler(ProcessList processes, int maxWaitTime) {
			this(processes, maxWaitTime, Logger::log);
		}

		/**
		 *
		 * @return true if running
		 */
		public boolean isRunning() {
			return runningProcess.isPresent();
		}

		/**
		 * changes the run state of the scheduler based on current time
		 *
		 */
		public void runProcess(Process process) {
			process.setRunStartTime(getCurrentTime());
			runningProcess = Optional.of(process);
		}

		/**
		 * stop run state of scheduler based on current time
		 */
		public void stopRunning() {
			runningProcess = Optional.empty();
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
			return totalWaitTime;
		}

		/**
		 * increments total wait time
		 *
		 * @param time
		 *            the time delta
		 */
		private void incTotalWaitTime(int time) {
			totalWaitTime += time;
		}

		/**
		 * computes average wait time based on the accumulated total / # of processes
		 *
		 * @return the ave wait time
		 */
		public float getAverageWaitTime() {
			return totalWaitTime / processes.getOriginalListSize();
		}

		/**
		 * Calculation of a wait time of a process
		 *
		 * @param timeOfRemovalFromQueue time removed from queue
		 * @param p the process
		 * @return the wait time for the process
		 */
		public static int calculateWaitTime(int timeOfRemovalFromQueue, Process p) {
			return timeOfRemovalFromQueue - p.getArrivalTime();
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
		private void adjustProcessPrioritiesInQueue() {
			if (!processes.isEmpty()) {

			}
		}

		/**
		 * The scheduler simulation
		 */
		public void simulate() {
			log("%nMaximum wait time = %d%n", getMaxWaitTime());
			reset();
			stopRunning();

			while (!processes.isEmpty()) {
				// get the top process without removing
				var top = processes.peek();

				if (top.getArrivalTime() <= getCurrentTime()) {
					// remove from list then add to priority queue
					pqueue.add(processes.remove());
				}

				if (!pqueue.isEmpty() && !isRunning()) {
					// if we're not running anything, run something
					var p = pqueue.remove();

					// sets running process;
					runProcess(p);
				}

				if (isRunning() && isRunningProcessFinished()) {
					stopRunning();
					adjustProcessPrioritiesInQueue();
				}

				incrementCurrentTime();
			}

			while (!pqueue.isEmpty()) {
				var p = pqueue.remove();
				// run process
				adjustProcessPrioritiesInQueue();
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
		private Logger(String fname) throws IOException {
			output = new PrintWriter(new FileWriter(fname), true);
		}

		public static void init(String fname) throws IOException {
			instance = new Logger(fname);
		}

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
		var pq = Scheduler.createPriorityQueue();

		pq.addAll(IntStream.rangeClosed(1, 10).mapToObj(i -> new Process(i, i, i, i)).collect(Collectors.toList()));

		var pl = new ArrayList<Process>();

		for (int i = 0; i < 5; i++)
			pl.add(pq.remove());

		// modify the priority of the top one
		var modifiedPriority = pq.peek();
		modifiedPriority.setPriority(100000);

		Scheduler.reinsertIntoPriorityQueue(pq, modifiedPriority);

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

		var scheduler = new Scheduler(plist);
		scheduler.simulate();

		Logger.log("Total wait time = %f", scheduler.getTotalWaitTime());
		Logger.log("Average wait time = %f", scheduler.getAverageWaitTime());
	}
}
