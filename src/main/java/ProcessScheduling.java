import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
		private final int id;
		private int priority;
		private final int arrival;
		private final int duration;
		private float waitTime;

		public Process(Integer... ids) {
			this(ids[0], ids[1], ids[2], ids[3]);
		}

		public Process(int id, int priority, int duration, int arrival) {
			this.id = id;
			this.priority = priority;
			this.arrival = arrival;
			this.duration = duration;
			this.waitTime = 0;
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

		public int getArrival() {
			return arrival;
		}

		public int getDuration() {
			return duration;
		}

		public float getWaitTime() {
			return waitTime;
		}

		public void setWaitTime(float waitTime) {
			this.waitTime = waitTime;
		}

		/**
		 * The comparator for process using the priority field
		 *
		 * @return
		 */
		public static Comparator<Process> getPriorityComparator() {
			return Comparator.comparingInt(Process::getPriority);
		}

		public static Comparator<Process> getArrivalComparator() {
			return Comparator.comparingInt(Process::getArrival);
		}
	}

	/**
	 * Process list
	 *
	 * we are creating a new class to abstract some process list operations that
	 * differentiate between list implementations for simplified access and less
	 * prone to bugs. This also allows us to change the collection implementation as
	 * we see fit without changing the rest of the code that uses the list
	 */
	static class ProcessList  {
		private Deque<Process> list;
		private int originalListSize;

		/**
		 * private constructor; clones the list
		 *
		 * @param processes
		 */
		private ProcessList(List<Process> processes) {
			// sort the list by arrival (asc) then assign to the internal collection
			this.list = processes.stream()
					.sorted(Process.getArrivalComparator())
					.collect(Collectors.toCollection(ArrayDeque::new));
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
		 * returns iterator for the process list
		 *
		 * @return the iterator
		 */
		public Iterator<Process> iterator() {
			return list.iterator();
		}

		/**
		 * is it empty?
		 *
		 * @return true if empty
		 */
		public boolean isEmpty() {
			return this.list.isEmpty();
		}

		/**
		 * the list is modified over time, so we save the original size
		 *
		 * @return the original list size
		 */
		public int getOriginalListSize() {
			return this.originalListSize;
		}

		@Override
		public String toString() {
			return list.stream().map(p -> String.format("Id = %d, priority = %d, duration = %d, arrival = %d",
					p.getId(), p.getPriority(), p.getDuration(), p.getArrival())).collect(Collectors.joining("\n"));
		}

		/**
		 * peek top of list
		 *
		 * @return the process at head of list
		 */
		Process peek() {
			return list.peek();
		}

		/**
		 * remove from top of list
		 *
		 * @return the process at head of list
		 */
		Process remove() {
			return list.remove();
		}
	}

	/**
	 * Scheduler object
	 */
	static class Scheduler {
		// default max wait time
		public static int DEFAULT_MAX_WAIT_TIME = 30;
		public static int NOT_RUNNING = -1;

		private int currentTime = 0;
		private int setRunningTime = 0;
		private PriorityQueue<Process> pqueue;
		private float totalWaitTime;
		private ProcessList processes;
		private final int maxWaitTime;
		private Consumer<String> eventHandler;

		/**
		 * reset times and other related stuff to throught he whole thing again
		 */
		public void reset() {
			totalWaitTime = 0;
			currentTime = 0;
			this.pqueue = new PriorityQueue<>(processes.getOriginalListSize(), Process.getPriorityComparator());
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
		 * The running flag is tied to the current time...since the current time >= 0
		 * we can just use setRunning as an int to save the start running time and
		 * this doubles as the flag...if setRunningTime > -1, it means that the
		 * scheduler is running
		 *
		 * @return true if running
		 */
		public boolean isRunning() {
			return setRunningTime > NOT_RUNNING;
		}

		/**
		 * changes the run state of the scheduler based on current time
		 *
		 */
		public void startRunning() {
			setRunningTime = getCurrentTime();
		}

		/**
		 * stop run state of scheduler based on current time
		 */
		public void stopRunning() {
			setRunningTime = NOT_RUNNING;
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
		 * @param format string format
		 * @param args args
		 */
		private void log(String format, Object... args) {
			eventHandler.accept(String.format(format, args));
		}

		/**
		 * adjust process priorities based on max wait times and current time
		 */
		private void adjustProcessPriorities() {
			if (!processes.isEmpty()) {

			}
		}

		/**
		 * main loop
		 */
		public void loop() {
			log("%nMaximum wait time = %d%n", getMaxWaitTime());
			reset();
			stopRunning();

			while (!processes.isEmpty()) {
				// get the top process without removing
				var top = processes.peek();

				if (top.getArrival() <= getCurrentTime()) {
					// remove from list then add to priority queue
					pqueue.add(processes.remove());
				}

				if (!pqueue.isEmpty() && !isRunning()) {
					// startRunning();
				}

				adjustProcessPriorities();
				incrementCurrentTime();
			}

			while (!pqueue.isEmpty()) {
				var p = pqueue.remove();
				// run process
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
	 * entry point
	 *
	 * @param args
	 *            command line args
	 */
	public static void main(String[] args) throws IOException {
		String input_fname = args.length > 0 ? args[0] : DEFAULT_INPUT_FILENAME;
		String output_fname = args.length > 1 ? args[1] : DEFAUL_OUTPUT_FILENAME;
		Logger.init(output_fname);

		var plist = ProcessList.getProcessListFromFile(input_fname);
		Logger.log(plist.toString());

		var scheduler = new Scheduler(plist);
		scheduler.loop();

		Logger.log("Total wait time = %f", scheduler.getTotalWaitTime());
		Logger.log("Average wait time = %f", scheduler.getAverageWaitTime());
	}
}
