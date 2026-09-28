package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.ValueType;

/**
 * Name, type and default of a channel.
 */
public record ChannelSpec<T>(String name, ValueType<T> type, T defaultValue) {
	public ChannelSpec {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(type, "type");
		type.cast(defaultValue);
	}

	public Channel<T> create() {
		return new Channel<>(type, defaultValue);
	}
}
