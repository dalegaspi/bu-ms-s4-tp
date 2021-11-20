import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Process scheduler implementation
 *
 * @author dlegaspi@bu.edu
 */
public class ProcessScheduling {

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
	 */
	static class ProcessList {
		private List<Process> list;
		private int originalListSize;

		/**
		 * constructor; clones the list
		 *
		 * @param processes
		 */
		public ProcessList(List<Process> processes) {
			this.list = new ArrayList<>(processes);
			this.originalListSize = processes.size();
		}

		/**
		 * process list from file
		 *
		 * @param fname filename
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

		public List<Process> toList() {
			return list;
		}

		@Override
		public String toString() {
			return list.stream().map(p -> String.format("Id = %d, priority = %d, duration = %d, arrival = %d",
					p.getId(), p.getPriority(), p.getDuration(), p.getArrival())).collect(Collectors.joining("\n"));
		}

		public boolean isEmpty() {
			return this.list.size() == 0;
		}

		public int getOriginalListSize() {
			return this.originalListSize;
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

		private void incrementTime(int delta) {
			currentTime += delta;
		}

		private void incrementTime() {
			incrementTime(1);
		}

		public Scheduler(ProcessList processes, int maxWaitTime) {
			this.processes = processes;
			this.maxWaitTime = maxWaitTime;
			this.queue = new PriorityQueue<>(processes.getOriginalListSize(), Process.getComparator());
		}

		public Scheduler(ProcessList process) {
			this(process, DEFAULT_MAX_WAIT_TIME);
		}

		public boolean isRunning() {
			return isRunning;
		}

		public void setRunning(boolean running) {
			isRunning = running;
		}

		public float getTotalWaitTime() {
			return totalWaitTime;
		}

		private void incTotalWaitTime(int time) {
			totalWaitTime += time;
		}

		public float getAverageWaitTime() {
			return totalWaitTime / processes.getOriginalListSize();
		}

		public int getMaxWaitTime() {
			return maxWaitTime;
		}

		public void loop() {

		}

		public int getCurrentTime() {
			return currentTime;
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
