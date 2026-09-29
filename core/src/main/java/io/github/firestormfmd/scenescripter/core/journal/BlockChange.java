package io.github.firestormfmd.scenescripter.core.journal;

import java.util.Objects;

import io.github.firestormfmd.scenescripter.core.math.BlockPos;

public record BlockChange<S>(BlockPos pos, S state) {
	public BlockChange {
		Objects.requireNonNull(pos, "pos");
		Objects.requireNonNull(state, "state");
	}
}
