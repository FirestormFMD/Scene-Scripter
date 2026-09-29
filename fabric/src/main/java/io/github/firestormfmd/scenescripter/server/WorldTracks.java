package io.github.firestormfmd.scenescripter.server;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.WeatherData;

import io.github.firestormfmd.scenescripter.SceneScripter;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;

/**
 * The scene track's effect on the world: time of day and weather while the scene controls them, and command
 * events with their undo commands. Everything is put back when the scene closes.
 */
final class WorldTracks {
	private static final int DAY = 24000;

	private final ServerLevel level;
	private Long savedTime;
	private Boolean savedRaining;
	private Boolean savedThundering;
	/** Command events that have run, by event ID, with their undo commands, oldest first. */
	private final Map<String, SceneEvent> ranCommands = new LinkedHashMap<>();

	WorldTracks(ServerLevel level) {
		this.level = level;
	}

	private MinecraftServer server() {
		return level.getServer();
	}

	/** Sets the world's time and weather to the scene track's at a tick, if the scene controls them. */
	void apply(SceneEvaluator.TrackState state) {
		if (state.timeOfDay() != null) {
			setTimeOfDay(Math.floorMod(state.timeOfDay(), DAY));
		}
		if (state.weather() != null) {
			setWeather(state.weather());
		}
	}

	private void setTimeOfDay(int timeOfDay) {
		level.dimensionType().defaultClock().ifPresent(clock -> {
			var clocks = level.clockManager();
			long total = clocks.getTotalTicks(clock);
			if (savedTime == null) {
				savedTime = total;
			}
			long wanted = Math.floorDiv(total, DAY) * DAY + timeOfDay;
			if (wanted != total) {
				clocks.setTotalTicks(clock, wanted);
				server().getPlayerList().broadcastAll(clocks.createFullSyncPacket());
			}
		});
	}

	private void setWeather(String weather) {
		WeatherData data = server().getWeatherData();
		if (savedRaining == null) {
			savedRaining = data.isRaining();
			savedThundering = data.isThundering();
		}
		boolean raining = !weather.equals("clear");
		boolean thundering = weather.equals("thunder");
		if (data.isRaining() != raining || data.isThundering() != thundering) {
			server().setWeatherParameters(raining ? 0 : 12000, raining ? 12000 : 0, raining, thundering);
		}
	}

	/** Runs a command event's command. */
	void runCommand(SceneEvent event) {
		if (event.params().get("command") instanceof String command && !command.isBlank()) {
			run(command);
			ranCommands.put(event.id(), event);
		}
	}

	/** Runs the undo command of every command event after {@code tick}, newest first, as the playhead moves back. */
	void rewindTo(int tick) {
		List<SceneEvent> ran = List.copyOf(ranCommands.values());
		for (int i = ran.size() - 1; i >= 0; i--) {
			SceneEvent e = ran.get(i);
			if (e.tick() > tick) {
				undo(e);
				ranCommands.remove(e.id());
			}
		}
	}

	/** Undoes every command and restores time and weather. */
	void restore() {
		rewindTo(Integer.MIN_VALUE);
		if (savedTime != null) {
			long time = savedTime;
			level.dimensionType().defaultClock().ifPresent(clock -> {
				level.clockManager().setTotalTicks(clock, time);
				server().getPlayerList().broadcastAll(level.clockManager().createFullSyncPacket());
			});
			savedTime = null;
		}
		if (savedRaining != null) {
			server().setWeatherParameters(savedRaining ? 0 : 12000, savedRaining ? 12000 : 0, savedRaining, savedThundering);
			savedRaining = null;
			savedThundering = null;
		}
	}

	private void undo(SceneEvent e) {
		if (e.params().get("undo") instanceof String undo && !undo.isBlank()) {
			run(undo);
		}
	}

	private void run(String command) {
		String c = command.startsWith("/") ? command.substring(1) : command;
		try {
			CommandSourceStack source = server().createCommandSourceStack().withLevel(level);
			server().getCommands().performPrefixedCommand(source, c);
		} catch (RuntimeException e) {
			SceneScripter.LOGGER.warn("Scene command failed: {}", c, e);
		}
	}
}
