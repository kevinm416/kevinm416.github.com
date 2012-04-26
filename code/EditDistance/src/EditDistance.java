public class EditDistance {
	// private static final ExecutorService threadPool =
	// Executors.newFixedThreadPool(4);

	private final String maxStr;
	private final String minStr;
	private final int maxLen;
	private final int minLen;

	public EditDistance(String s1, String s2) {
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
			current = new int[currentLen];
			parallelizeAll(prev, current, i);
			prev = combineDists(prev, current, i);
		}
		return current[0];
	}

	private void parallelizeAll(int[] prev, int[] current, int iteration) {
		for (int i = 0; i < current.length; i++) {
			int x;
			int y;
			int leftIdx;
			int downIdx;
			int diagonalIdx;
			if (iteration < minLen) {
				x = i;
				y = current.length - i - 1;
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
			current[i] = dist;
		}
	}

	private int[] combineDists(int[] prev, int[] current, int iteration) {
		int newLen = current.length * 2 - 1;
		int[] ret = new int[newLen];
		for (int i = 0; i < current.length; i++) {
			ret[i * 2] = current[i];
		}

		int startIdx;
		int offset;
		int endIdx;
		if (iteration < minLen) {
			startIdx = 0;
			offset = 1;
		} else {
			startIdx = 2;
			offset = -1;
		}
		if (iteration < maxLen) {
			endIdx = prev.length;
		} else {
			endIdx = prev.length - 2;
		}

		for (int i = startIdx; i < endIdx; i += 2) {
			ret[i + offset] = prev[i];
		}
		return ret;
	}

	private int penalty(int maxIdx, int minIdx) {
		return (maxStr.charAt(maxIdx) == minStr.charAt(minIdx)) ? 0 : 1;
	}

	public static void main(String args[]) {
		EditDistance ed = new EditDistance("Saturday", "Sunday");
		System.out.println(ed.editDist());
	}
}