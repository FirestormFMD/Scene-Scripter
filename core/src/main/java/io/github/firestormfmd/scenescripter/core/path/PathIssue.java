package io.github.firestormfmd.scenescripter.core.path;

/**
 * A problem found while planning a path, shown in the editor as a red segment.
 *
 * @param tick tick of the clip where the problem starts
 * @param distance distance along the path where the problem starts
 */
public record PathIssue(Kind kind, int tick, double distance) {
	public enum Kind {
		/** No ground within the search window; the object hovers. */
		NO_GROUND("No ground under the path"),
		/** A rise higher than the object can jump; the object pops up onto it. */
		TOO_HIGH("Too high to jump"),
		/** A rise within jump height that the jump still cannot clear at this speed. */
		JUMP_BLOCKED("Jump can't clear this at the current speed"),
		/** A fluid is in the way and the ground filter treats fluids as blocked. */
		FLUID_BLOCKED("Fluid blocks the path"),
		/** A fitted clip is shorter than its wait markers. */
		NOT_ENOUGH_TIME("Clip is shorter than its waits");

		private final String message;

		Kind(String message) {
			this.message = message;
		}

		public String message() {
			return message;
		}
	}
}
