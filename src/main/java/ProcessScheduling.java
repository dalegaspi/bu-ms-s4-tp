import java.io.IOException;
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
 * Tested to work with Java 11.  It uses some features beyond Java 8 like type
 * inference.
 *
 * @author dlegaspi@bu.edu
 */
public class ProcessScheduling {

	/**
	 * the default input file
	 */
	public static final String DEFAULT_INPUT = "process_scheduling_input.txt ";

	/**
	 * Process object
	 */
	public static class Process {
		private final int id;
		private int priority;
		private final int arrival;
		private final int duration;
		private float waitTime;

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

		public void setPriority(int priority) {
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
		public static Comparator<Process> getComparator() {
			return Comparator.comparingInt(Process::getPriority);
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
	static class ProcessList {
		private List<Process> list;
		private int originalListSize;

		/**
		 * private constructor; clones the list
		 *
		 * @param processes
		 */
		private ProcessList(List<Process> processes) {
			this.list = new ArrayList<>(processes);
			this.originalListSize = processes.size();
		}

		/**
		 * process list from file
		 *
		 * @param fname
		 *            filename
		 * @return the process list
		 * @throws IOException
		 */
		static ProcessList getProcessListFromFile(String fname) throws IOException {
			try (var ps = Files.lines(Paths.get(fname))) {
				// @formatter:off
				return new ProcessList(ps.map(p -> p.split(" "))
						.map(strings -> Arrays.stream(strings)
								.map(Integer::valueOf)
								.collect(Collectors.toList())
								.toArray(new Integer[4]))
						.map(ints -> new Process(ints[0], ints[1], ints[2], ints[3]))
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
			return this.list.size() == 0;
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
	}

	/**
	 * Scheduler object
	 */
	static class Scheduler {
		public static int DEFAULT_MAX_WAIT_TIME = 30;

		private int currentTime = 0;
		private boolean isRunning = false;
		private PriorityQueue<Process> queue;
		private float totalWaitTime;
		private ProcessList processes;
		private final int maxWaitTime;
		private Consumer<String> eventHandler;

		/**
		 * increments the scheduler's clock/time
		 *
		 * @param delta delta
		 */
		private void incrementTime(int delta) {
			currentTime += delta;
		}

		/**
		 * Overload to increment time by 1 unit
		 */
		private void incrementTime() {
			incrementTime(1);
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
		 * @param processes process list
		 * @param maxWaitTime max wait time
		 * @param eventHandler event handler (logging)
		 */
		public Scheduler(ProcessList processes, int maxWaitTime, Consumer<String> eventHandler) {
			this.processes = processes;
			this.maxWaitTime = maxWaitTime;
			this.queue = new PriorityQueue<>(processes.getOriginalListSize(), Process.getComparator());
			this.eventHandler = eventHandler;
		}

		/**
		 * Convenience overloaded constructor with applied defaults
		 *
		 * @param processes the process list
		 */
		public Scheduler(ProcessList processes) {
			this(processes, DEFAULT_MAX_WAIT_TIME, System.out::println);
		}

		/**
		 * is it running?
		 *
		 * @return true if running
		 */
		public boolean isRunning() {
			return isRunning;
		}

		/**
		 * changes the state of the scheduler
		 *
		 * @param running true/false
		 */
		public void setRunning(boolean running) {
			isRunning = running;
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
		 * @param time the time delta
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

		private void log(String format, Object... args) {
			eventHandler.accept(String.format(format, args));
		}

		/**
		 * main loop
		 */
		public void loop() {
			log("%nMaximum wait time = %d%n", getMaxWaitTime());
		}
	}

	/**
	 * entry point
	 *
	 * @param args
	 *            command line args
	 */
	public static void main(String[] args) throws IOException {
		String fname = args.length > 0 ? args[0] : DEFAULT_INPUT;

		var plist = ProcessList.getProcessListFromFile(fname);

		System.out.println(plist);
		var scheduler = new Scheduler(plist);

		// run all processes

		scheduler.loop();
	}
}
