package io.github.firestormfmd.scenescripter.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.io.IOException;
import java.nio.file.Path;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import io.github.firestormfmd.scenescripter.core.io.SceneFormatException;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.server.SceneManager;
import io.github.firestormfmd.scenescripter.server.SceneSession;

/**
 * {@code /scene}: open, save and play scenes without the editor, for example from command blocks.
 */
public final class SceneCommands {
	private SceneCommands() {
	}

	private static final SuggestionProvider<CommandSourceStack> SCENES = (ctx, builder) ->
			SharedSuggestionProvider.suggest(SceneManager.get().map(m -> m.storage().list()).orElse(java.util.List.of()), builder);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("scene")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(literal("new")
						.then(argument("name", StringArgumentType.word())
								.executes(ctx -> create(ctx, 20 * 60))
								.then(argument("ticks", IntegerArgumentType.integer(20, 20 * 60 * 60))
										.executes(ctx -> create(ctx, IntegerArgumentType.getInteger(ctx, "ticks"))))))
				.then(literal("open")
						.then(argument("name", StringArgumentType.word()).suggests(SCENES)
								.executes(SceneCommands::open)))
				.then(literal("save").executes(ctx -> run(ctx, m -> {
					m.save();
					return "Scene saved";
				})))
				.then(literal("close").executes(ctx -> run(ctx, m -> {
					m.close();
					return "Scene closed; the world is back to how it was";
				})))
				.then(literal("list").executes(ctx -> run(ctx, m -> {
					var names = m.storage().list();
					return names.isEmpty() ? "No scenes yet" : "Scenes: " + String.join(", ", names);
				})))
				.then(literal("delete")
						.then(argument("name", StringArgumentType.word()).suggests(SCENES)
								.executes(ctx -> run(ctx, m -> {
									m.delete(StringArgumentType.getString(ctx, "name"));
									return "Scene deleted";
								}))))
				.then(literal("play")
						.executes(ctx -> withSession(ctx, s -> {
							s.setSpeed(1);
							s.play();
							return "Playing";
						}))
						.then(argument("speed", FloatArgumentType.floatArg(0.1f, 4f))
								.executes(ctx -> withSession(ctx, s -> {
									s.setSpeed(FloatArgumentType.getFloat(ctx, "speed"));
									s.play();
									return "Playing";
								}))))
				.then(literal("pause").executes(ctx -> withSession(ctx, s -> {
					s.pause();
					return "Paused at tick " + s.clock().tick();
				})))
				.then(literal("stop").executes(ctx -> withSession(ctx, s -> {
					s.pause();
					s.seek(0);
					return "Stopped";
				})))
				.then(literal("seek")
						.then(argument("tick", IntegerArgumentType.integer(0))
								.executes(ctx -> withSession(ctx, s -> {
									s.seek(IntegerArgumentType.getInteger(ctx, "tick"));
									return "At tick " + s.clock().tick();
								}))))
				.then(literal("status").executes(ctx -> withSession(ctx, s ->
						"Scene " + s.name() + ": tick " + s.clock().tick() + " of " + s.scene().length()
								+ (s.clock().isPlaying() ? ", playing" : ", paused")
								+ ", " + s.scene().objects().size() + " objects"
								+ (s.isDirty() ? ", unsaved changes" : ""))))
				.then(literal("export").executes(ctx -> withSession(ctx, s -> {
					Path out = SceneManager.get().orElseThrow().storage().export(s.name(), s.scene());
					return "Exported to " + out;
				}))));
	}

	@FunctionalInterface
	private interface Action<T> {
		String run(T target) throws IOException, SceneFormatException;
	}

	private static int create(CommandContext<CommandSourceStack> ctx, int ticks) {
		return run(ctx, m -> {
			ServerLevel level = ctx.getSource().getLevel();
			Scene scene = m.create(StringArgumentType.getString(ctx, "name"), ticks, level,
					net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
			return "Created scene " + scene.name() + " (" + ticks + " ticks)";
		});
	}

	private static int open(CommandContext<CommandSourceStack> ctx) {
		return run(ctx, m -> {
			Scene scene = m.open(StringArgumentType.getString(ctx, "name"), ctx.getSource().getLevel());
			return "Opened scene " + scene.name();
		});
	}

	private static int run(CommandContext<CommandSourceStack> ctx, Action<SceneManager> action) {
		SceneManager manager = SceneManager.get().orElse(null);
		if (manager == null) {
			ctx.getSource().sendFailure(Component.literal("Scene Scripter is not running"));
			return 0;
		}
		try {
			String message = action.run(manager);
			ctx.getSource().sendSuccess(() -> Component.literal(message), false);
			return Command.SINGLE_SUCCESS;
		} catch (IOException | SceneFormatException | RuntimeException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage() == null ? e.toString() : e.getMessage()));
			return 0;
		}
	}

	private static int withSession(CommandContext<CommandSourceStack> ctx, Action<SceneSession> action) {
		return run(ctx, m -> {
			SceneSession s = m.session().orElseThrow(() -> new IOException("No scene is open; use /scene open <name>"));
			return action.run(s);
		});
	}
}
