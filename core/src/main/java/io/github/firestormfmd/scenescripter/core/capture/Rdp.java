package io.github.firestormfmd.scenescripter.core.capture;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntToDoubleFunction;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * Ramer-Douglas-Peucker simplification: keeps the fewest points that stay within a tolerance of the original line,
 * which turns a per-tick recording into a handful of editable keyframes.
 */
public final class Rdp {
	private Rdp() {
	}

	/**
	 * Indices of the points to keep, always including the first and last.
	 *
	 * @param epsilon largest allowed distance between the original points and the simplified line
	 */
	public static List<Integer> simplify(List<Vec3> points, double epsilon) {
		List<Integer> keep = new ArrayList<>();
		if (points.isEmpty()) {
			return keep;
		}
		boolean[] marked = new boolean[points.size()];
		marked[0] = true;
		marked[points.size() - 1] = true;
		mark(points, 0, points.size() - 1, epsilon, marked);
		for (int i = 0; i < marked.length; i++) {
			if (marked[i]) {
				keep.add(i);
			}
		}
		return keep;
	}

	/** Simplifies a value over time, such as a facing angle, treating (index, value) as a 2D line. */
	public static List<Integer> simplify(int count, IntToDoubleFunction value, double epsilon) {
		List<Vec3> points = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			points.add(new Vec3(i, value.applyAsDouble(i), 0));
		}
		return simplify(points, epsilon);
	}

	private static void mark(List<Vec3> points, int first, int last, double epsilon, boolean[] marked) {
		// Iterative with an explicit stack so long recordings cannot overflow the call stack.
		java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
		stack.push(new int[] {first, last});
		while (!stack.isEmpty()) {
			int[] range = stack.pop();
			int a = range[0];
			int b = range[1];
			double worst = -1;
			int worstIndex = -1;
			for (int i = a + 1; i < b; i++) {
				double d = distanceToSegment(points.get(i), points.get(a), points.get(b));
				if (d > worst) {
					worst = d;
					worstIndex = i;
				}
			}
			if (worst > epsilon) {
				marked[worstIndex] = true;
				stack.push(new int[] {a, worstIndex});
				stack.push(new int[] {worstIndex, b});
			}
		}
	}

	static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSquared();
		if (len2 < 1.0e-12) {
			return p.distanceTo(a);
		}
		double t = Math.clamp(p.subtract(a).dot(ab) / len2, 0, 1);
		return p.distanceTo(a.add(ab.scale(t)));
	}
}
