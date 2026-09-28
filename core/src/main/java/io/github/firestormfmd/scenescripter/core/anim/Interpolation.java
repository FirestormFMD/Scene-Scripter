package io.github.firestormfmd.scenescripter.core.anim;

/**
 * How a channel moves from one keyframe to the next. A keyframe's interpolation applies to the segment that
 * starts at that keyframe.
 */
public enum Interpolation {
	/** Holds the value until the next keyframe. */
	STEP("step"),
	LINEAR("linear"),
	/** Smooth curve shaped by the keyframes' handles, or automatic handles when none are set. */
	BEZIER("bezier"),
	EASE_IN("ease_in"),
	EASE_OUT("ease_out"),
	EASE_IN_OUT("ease_in_out"),
	/** Pulls back slightly before moving. */
	BACK_IN("back_in"),
	/** Overshoots slightly, then settles. */
	BACK_OUT("back_out"),
	BOUNCE_OUT("bounce_out");

	private static final double BACK_C1 = 1.70158;
	private static final double BACK_C3 = BACK_C1 + 1;

	private final String id;

	Interpolation(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static Interpolation byId(String id) {
		for (Interpolation i : values()) {
			if (i.id.equals(id)) {
				return i;
			}
		}
		throw new IllegalArgumentException("Unknown interpolation: " + id);
	}

	/**
	 * Maps linear progress {@code s} in [0, 1] through this curve. Not meaningful for {@link #BEZIER}, which
	 * depends on handles; it is treated as linear here.
	 */
	public double ease(double s) {
		return switch (this) {
			case STEP -> s < 1 ? 0 : 1;
			case LINEAR, BEZIER -> s;
			case EASE_IN -> s * s * s;
			case EASE_OUT -> 1 - Math.pow(1 - s, 3);
			case EASE_IN_OUT -> s < 0.5 ? 4 * s * s * s : 1 - Math.pow(-2 * s + 2, 3) / 2;
			case BACK_IN -> BACK_C3 * s * s * s - BACK_C1 * s * s;
			case BACK_OUT -> 1 + BACK_C3 * Math.pow(s - 1, 3) + BACK_C1 * Math.pow(s - 1, 2);
			case BOUNCE_OUT -> bounceOut(s);
		};
	}

	private static double bounceOut(double s) {
		final double n1 = 7.5625;
		final double d1 = 2.75;
		if (s < 1 / d1) {
			return n1 * s * s;
		} else if (s < 2 / d1) {
			s -= 1.5 / d1;
			return n1 * s * s + 0.75;
		} else if (s < 2.5 / d1) {
			s -= 2.25 / d1;
			return n1 * s * s + 0.9375;
		} else {
			s -= 2.625 / d1;
			return n1 * s * s + 0.984375;
		}
	}
}
