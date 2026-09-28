package io.github.firestormfmd.scenescripter.core.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

class ChannelTest {
	@Test
	void emptyChannelUsesDefault() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 20.0);
		assertEquals(20.0, ch.valueAt(55));
	}

	@Test
	void valuesHoldOutsideTheKeyedRange() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(10, 1.0));
		ch.put(Keyframe.of(20, 3.0));
		assertEquals(1.0, ch.valueAt(0));
		assertEquals(3.0, ch.valueAt(100));
	}

	@Test
	void linearInterpolation() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(0, 0.0));
		ch.put(Keyframe.of(10, 10.0));
		assertEquals(2.5, ch.valueAt(2.5), 1e-12);
		assertEquals(7.0, ch.valueAt(7), 1e-12);
	}

	@Test
	void stepHoldsUntilNextKey() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(0, 1.0, Interpolation.STEP));
		ch.put(Keyframe.of(10, 5.0));
		assertEquals(1.0, ch.valueAt(9.99));
		assertEquals(5.0, ch.valueAt(10));
	}

	@Test
	void nonNumericTypesStep() {
		Channel<Boolean> ch = new Channel<>(ValueType.BOOL, false);
		ch.put(Keyframe.of(0, false));
		ch.put(Keyframe.of(10, true));
		assertEquals(false, ch.valueAt(9));
		assertEquals(true, ch.valueAt(10));
	}

	@Test
	void intsRound() {
		Channel<Integer> ch = new Channel<>(ValueType.INT, 0);
		ch.put(Keyframe.of(0, 0));
		ch.put(Keyframe.of(4, 3));
		assertEquals(2, ch.valueAt(2.5)); // 1.875 rounds to 2
	}

	@Test
	void vectorsInterpolatePerComponent() {
		Channel<Vec3> ch = new Channel<>(ValueType.VEC3, Vec3.ZERO);
		ch.put(Keyframe.of(0, new Vec3(0, 64, 0)));
		ch.put(Keyframe.of(20, new Vec3(10, 64, -20)));
		assertEquals(new Vec3(5, 64, -10), ch.valueAt(10));
	}

	@Test
	void easingShapesTheSegment() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(0, 0.0, Interpolation.EASE_IN));
		ch.put(Keyframe.of(10, 1.0));
		assertEquals(0.125, ch.valueAt(5), 1e-12);
	}

	@Test
	void autoBezierHitsKeysAndNeverOvershootsAPeak() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(0, 0.0, Interpolation.BEZIER));
		ch.put(Keyframe.of(10, 5.0, Interpolation.BEZIER));
		ch.put(Keyframe.of(20, 0.0, Interpolation.BEZIER));
		assertEquals(5.0, ch.valueAt(10), 1e-9);
		for (double t = 0; t <= 20; t += 0.25) {
			double v = ch.valueAt(t);
			assertTrue(v >= -1e-9 && v <= 5.0 + 1e-9, "value out of range at " + t + ": " + v);
		}
		// Symmetric keys give a symmetric curve.
		assertEquals(ch.valueAt(4), ch.valueAt(16), 1e-9);
	}

	@Test
	void autoBezierIsMonotonicThroughRisingKeys() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(0, 0.0, Interpolation.BEZIER));
		ch.put(Keyframe.of(7, 2.0, Interpolation.BEZIER));
		ch.put(Keyframe.of(20, 9.0, Interpolation.BEZIER));
		double prev = -1;
		for (double t = 0; t <= 20; t += 0.1) {
			double v = ch.valueAt(t);
			assertTrue(v >= prev - 1e-9, "curve dips at " + t);
			prev = v;
		}
	}

	@Test
	void customHandlesAreUsed() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		// Both handles pull the curve up to 10 at a third of the way in, so the midpoint rises well above linear.
		ch.put(new Keyframe<>(0, 0.0, Interpolation.BEZIER, new Handles(0, 0, 10.0 / 3, 10), null));
		ch.put(new Keyframe<>(10, 0.0, Interpolation.LINEAR, new Handles(-10.0 / 3, 10, 0, 0), null));
		assertEquals(7.5, ch.valueAt(5), 1e-6);
	}

	@Test
	void putReplacesKeyAtSameTick() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(5, 1.0));
		assertEquals(1.0, ch.put(Keyframe.of(5, 2.0)).orElseThrow().value());
		assertEquals(1, ch.keys().size());
		assertEquals(2.0, ch.valueAt(5));
	}

	@Test
	void keysStaySorted() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		ch.put(Keyframe.of(30, 3.0));
		ch.put(Keyframe.of(10, 1.0));
		ch.put(Keyframe.of(20, 2.0));
		assertEquals(10, ch.keys().get(0).tick());
		assertEquals(20, ch.keys().get(1).tick());
		assertEquals(30, ch.keys().get(2).tick());
	}

	@Test
	void rejectsValuesOfTheWrongType() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 0.0);
		@SuppressWarnings({"unchecked", "rawtypes"})
		Keyframe<?> wrong = new Keyframe(0, "nope", Interpolation.STEP, null, null);
		assertThrows(IllegalArgumentException.class, () -> ch.putUnchecked(wrong));
	}

	@Test
	void removeGeneratedKeys() {
		Channel<Double> ch = new Channel<>(ValueType.FLOAT, 20.0);
		ch.put(Keyframe.of(0, 20.0));
		ch.put(new Keyframe<>(40, 13.0, Interpolation.STEP, null, "e1"));
		ch.put(new Keyframe<>(60, 6.0, Interpolation.STEP, null, "e2"));
		assertEquals(1, ch.removeIf(k -> "e1".equals(k.generatedBy())).size());
		assertEquals(2, ch.keys().size());
	}
}
