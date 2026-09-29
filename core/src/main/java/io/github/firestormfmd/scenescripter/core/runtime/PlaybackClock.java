package io.github.firestormfmd.scenescripter.core.runtime;

import java.util.Optional;

/**
 * The playhead. Each server tick, {@link #advance()} says how far the playhead moved, so events on every tick
 * passed can be fired even when previewing faster than real time.
 */
public final class PlaybackClock {
	public static final double MIN_SPEED = 0.1;
	public static final double MAX_SPEED = 4;

	/**
	 * A playhead move from {@code from} to {@code to}.
	 *
	 * @param contiguous true when the playhead moved forward tick by tick, so events in between should fire; false
	 *                   for jumps (seeking, looping) where only the resulting state matters
	 */
	public record Step(int from, int to, boolean contiguous) {
	}

	private int length;
	private int tick;
	private boolean playing;
	private double speed = 1;
	private double carry;
	private int loopStart = -1;
	private int loopEnd = -1;

	public PlaybackClock(int length) {
		setLength(length);
	}

	public int tick() {
		return tick;
	}

	public boolean isPlaying() {
		return playing;
	}

	public double speed() {
		return speed;
	}

	public int length() {
		return length;
	}

	public void setLength(int length) {
		if (length <= 0) {
			throw new IllegalArgumentException("Length must be positive");
		}
		this.length = length;
		tick = Math.min(tick, length);
	}

	/** Playback speed relative to real time; recording always uses 1. */
	public void setSpeed(double speed) {
		this.speed = Math.clamp(speed, MIN_SPEED, MAX_SPEED);
	}

	public void play() {
		if (tick >= length) {
			tick = loopStart >= 0 ? loopStart : 0;
		}
		playing = true;
		carry = 0;
	}

	public void pause() {
		playing = false;
	}

	/** Loops playback between two ticks; pass -1 for both to stop looping. */
	public void setLoop(int start, int end) {
		if (start < 0 || end < 0) {
			loopStart = -1;
			loopEnd = -1;
			return;
		}
		if (end <= start) {
			throw new IllegalArgumentException("Loop must end after it starts");
		}
		loopStart = start;
		loopEnd = end;
	}

	public boolean isLooping() {
		return loopStart >= 0;
	}

	public int loopStart() {
		return loopStart;
	}

	public int loopEnd() {
		return loopEnd;
	}

	/** Moves the playhead for one server tick. Empty when paused or when a slow preview has not reached a tick. */
	public Optional<Step> advance() {
		if (!playing) {
			return Optional.empty();
		}
		carry += speed;
		int n = (int) Math.floor(carry);
		carry -= n;
		if (n == 0) {
			return Optional.empty();
		}
		int from = tick;
		int to = tick + n;
		if (isLooping() && to > loopEnd) {
			tick = loopStart;
			return Optional.of(new Step(from, loopStart, false));
		}
		if (to >= length) {
			to = length;
			playing = false;
		}
		tick = to;
		return Optional.of(new Step(from, to, true));
	}

	/** Jumps the playhead; nothing between is fired. */
	public Step seek(int target) {
		int from = tick;
		tick = Math.clamp(target, 0, length);
		carry = 0;
		return new Step(from, tick, false);
	}
}
