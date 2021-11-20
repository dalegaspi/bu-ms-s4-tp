import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Process scheduler class
 *
 * @author dlegaspi@bu.edu
 */
public class ProcessScheduling {

	public static final String DEFAULT_INPUT = "process_scheduling_input.txt ";

	/**
	 * Process object
	 */
	public static class Process {
		public int id;
		public int priority;
		public int arrival;
		public int duration;

		public Process(int id, int priority, int arrival, int duration) {
			this.id = id;
			this.priority = priority;
			this.arrival = arrival;
			this.duration = duration;
		}
	}

	/**
	 * entry point
	 *
	 * @param args
	 *            command line args
	 */
	public static void main(String[] args) {
		String fname = args.length > 0 ? args[0] : DEFAULT_INPUT;
		System.out.printf("Input filename: %s%n", fname);

		try (var ps = Files.lines(Paths.get(fname))) {
			// @formatter:off
			var plist = ps.map(p -> p.split(" "))
					.map(strings -> Arrays.stream(strings)
                            .map(Integer::valueOf)
                            .collect(Collectors.toList())
							.toArray(new Integer[4]))
					.map(ints -> new Process(ints[0], ints[1], ints[2], ints[3]))
                    .collect(Collectors.toList());
			// @formatter:on
            System.out.printf("There are %d processes %n", plist.size());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
