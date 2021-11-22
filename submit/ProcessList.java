import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Process list
 *
 * This represents the processes to be run in the Scheduler
 *
 * @author dlegaspi@bu.edu
 * @see java.util.ArrayDeque
 * @see Process
 */
class ProcessList extends ArrayDeque<Process> {
	private final int originalListSize;

	/**
	 * returns a new sorted Process list based arrival date on the given list
	 *
	 * @param processes
	 *            the process list
	 * @return
	 */
	private static Collection<Process> sortListByArrival(Collection<Process> processes) {
		return processes.stream().sorted(Process.getArrivalComparator()).collect(Collectors.toList());
	}

	/**
	 * private constructor; clones the list
	 *
	 * @param processes
	 *            the process list
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
	 * @return the original list size
	 */
	public int getOriginalListSize() {
		assert originalListSize > 0;
		return this.originalListSize;
	}

	@Override
	public String toString() {
		return this.stream().map(p -> String.format("Id = %d, priority = %d, duration = %d, arrival = %d", p.getId(),
				p.getPriority(), p.getDuration(), p.getArrivalTime())).collect(Collectors.joining("\n"));
	}
}
