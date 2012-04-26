import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class EditDistance {
	private static final int MIN_CHUNK_SIZE = 5;
	private final ExecutorService threadPool;
	private final int threadCount;
	private final String maxStr;
	private final String minStr;
	private final int maxLen;
	private final int minLen;

	public EditDistance(String s1, String s2, int threadCount) {
		this.threadCount = threadCount;
		threadPool = Executors.newFixedThreadPool(threadCount);
		if (s1.length() < s2.length()) {
			minStr = s1;
			maxStr = s2;
		} else {
			minStr = s2;
			maxStr = s1;
		}
		maxLen = maxStr.length();
		minLen = minStr.length();
	}

	public int editDist() {
		int iterations = maxLen + minLen - 1;
		int[] prev = new int[0];
		int[] current = null;

		for (int i = 0; i < iterations; i++) {
			int currentLen;
			if (i < minLen) {
				currentLen = i + 1;
			} else if (i < maxLen) {
				currentLen = minLen;
			} else {
				currentLen = iterations - i;
			}
			
			current = new int[currentLen*2 - 1];
			parallelize(prev, current, currentLen, i);
			prev = current;
		}
		return current[0];
	}

	private void parallelize(int[] prev, int[] current, int currentLen, int iteration) {
		int chunkSize = Math.max(current.length / threadCount, MIN_CHUNK_SIZE);
		List<Future<?>> futures = new ArrayList<Future<?>>();
		for (int i = 0; i < current.length; i += chunkSize) {
			int stopIdx = Math.min(currentLen, i + chunkSize);
			Runnable worker = new Worker(prev, current, currentLen, iteration, i, stopIdx);
			futures.add(threadPool.submit(worker));
		}
		for (Future<?> future : futures) {
			try {
				Object result = future.get();
				if (result != null) {
					throw new RuntimeException(result.toString());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				// We can only finish the computation if we complete
				// all subproblems
				throw new RuntimeException(e);
			}
		}
	}
	
	private void doChunk(int[] prev, int[] current, int currentLen, int iteration, int startIdx, int stopIdx) {
		int mergeStartIdx = (iteration < minLen) ? 0 : 2;
		
		for (int i = startIdx; i < stopIdx; i++) {
			// Edit distance 
			int x;
			int y;
			int leftIdx;
			int downIdx;
			int diagonalIdx;
			if (iteration < minLen) {
				x = i;
				y = currentLen - i - 1;
				leftIdx = i * 2 - 2;
				downIdx = i * 2;
				diagonalIdx = i * 2 - 1;
			} else {
				x = i + iteration - minLen + 1;
				y = minLen - i - 1;
				leftIdx = i * 2;
				downIdx = i * 2 + 2;
				diagonalIdx = i * 2 + 1;
			}
			int left = 1 + ((leftIdx < 0) ? iteration + 1 : prev[leftIdx]);
			int down = 1 + ((downIdx < prev.length) ? prev[downIdx]
					: iteration + 1);
			int diagonal = penalty(x, y)
					+ ((diagonalIdx < 0 || diagonalIdx >= prev.length) ? iteration
							: prev[diagonalIdx]);
			int dist = Math.min(left, Math.min(down, diagonal));
			current[i*2] = dist;
			
			// Merge prev
			int mergeIdx = i*2 + 1;
			if (mergeIdx < current.length) {
				current[mergeIdx] = prev[mergeStartIdx + i*2];
			}
		}
	}

	private int penalty(int maxIdx, int minIdx) {
		return (maxStr.charAt(maxIdx) == minStr.charAt(minIdx)) ? 0 : 1;
	}
	
	public void shutdown() {
		threadPool.shutdown();
	}
	
	private class Worker implements Runnable {
		private final int[] prev;
		private final int[] current;
		private final int currentLen;
		private final int iteration;
		private final int startIdx;
		private final int stopIdx;
		Worker(int[] prev, int[] current, int currentLen, int iteration, int startIdx, int stopIdx) {
			this.prev = prev;
			this.current = current;
			this.currentLen = currentLen;
			this.iteration = iteration;
			this.startIdx = startIdx;
			this.stopIdx = stopIdx;
		}
		
		@Override
		public void run() {
			for (int i = startIdx; i < stopIdx; i++) {
				int a = 2;
				doChunk(prev, current, currentLen, iteration, startIdx, stopIdx);
			}
		}
	}
	
	public static void main(String args[]) {
		EditDistance ed = new EditDistance("Saturday", "Sunday", 4);
		System.out.println(ed.editDist());
		ed.shutdown();
	}
}
