import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Process scheduler implementation
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
	 * Poor man's java.util.logging.Logger
	 */
	public static class Logger {
		private static Logger instance;
		private final PrintWriter output;

		/**
		 * our private constructor
		 *
		 * @param fname
		 *            the file name output
		 * @throws IOException
		 *             for any file error(s)
		 */
		private Logger(String fname) throws IOException {
			output = new PrintWriter(new FileWriter(fname), true);
		}

		/**
		 * Initialize our logger
		 *
		 * @param fname
		 *            the file name output
		 * @throws IOException
		 *             for any file error(s)
		 */
		public static void init(String fname) throws IOException {
			instance = new Logger(fname);
		}

		/**
		 * The actual logging method
		 * 
		 * @param format
		 *            format
		 * @param args
		 *            args
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

		pq.remove(modifiedPriority);
		pq.add(modifiedPriority);

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
