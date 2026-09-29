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
import io.github.firestormfmd.scenescripter.core.math.BlockBox;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
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
				})))
				.then(literal("tutorial")
						.executes(ctx -> tutorial(ctx, "tutorial"))
						.then(argument("name", StringArgumentType.word())
								.executes(ctx -> tutorial(ctx, StringArgumentType.getString(ctx, "name")))))
				.then(literal("record")
						.executes(ctx -> withSession(ctx, s -> {
							SceneManager.get().orElseThrow().playForRecording(ctx.getSource().getPlayer(), 40);
							return "Playing for recording after a 2s hold";
						}))
						.then(argument("preroll", IntegerArgumentType.integer(0, 20 * 60))
								.executes(ctx -> withSession(ctx, s -> {
									SceneManager.get().orElseThrow().playForRecording(ctx.getSource().getPlayer(),
											IntegerArgumentType.getInteger(ctx, "preroll"));
									return "Playing for recording";
								}))))
				.then(literal("reset").executes(ctx -> withSession(ctx, s -> {
					s.reset();
					return "Back to the start; the world is as it was before the scene";
				})))
				.then(literal("run")
						.then(argument("name", StringArgumentType.word()).suggests(SCENES)
								.executes(ctx -> run(ctx, m -> {
									String name = StringArgumentType.getString(ctx, "name");
									if (m.session().map(s -> !s.name().equals(name)).orElse(true)) {
										m.open(name, ctx.getSource().getLevel());
									}
									m.playForRecording(ctx.getSource().getPlayer(), 40);
									return "Playing " + name;
								}))))
				.then(literal("import")
						.then(argument("file", StringArgumentType.string())
								.executes(ctx -> run(ctx, m -> "Imported " + m.importScene(StringArgumentType.getString(ctx, "file"),
										ctx.getSource().getLevel(), null).name()))
								.then(literal("here").executes(ctx -> run(ctx, m -> "Imported " + m.importScene(
										StringArgumentType.getString(ctx, "file"), ctx.getSource().getLevel(),
										net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition())).name()
										+ " with its origin here")))))
				.then(literal("capture")
						.then(literal("stop").executes(ctx -> run(ctx, m -> {
							m.stopCapture(player(ctx));
							return "Capture stopped";
						})))
						.then(argument("object", StringArgumentType.word())
								.executes(ctx -> capture(ctx, 60, false))
								.then(argument("preroll", IntegerArgumentType.integer(0, 20 * 60))
										.executes(ctx -> capture(ctx, IntegerArgumentType.getInteger(ctx, "preroll"), false))
										.then(literal("loop").executes(ctx ->
												capture(ctx, IntegerArgumentType.getInteger(ctx, "preroll"), true))))))
				.then(literal("takes").executes(ctx -> withSession(ctx, s -> s.takes().isEmpty() ? "No takes yet"
						: "Takes: " + String.join(", ", s.takes().stream().map(t -> t.id() + " (" + t.objectId() + ", ticks "
								+ t.startTick() + "-" + t.endTick() + ")").toList()))))
				.then(literal("take")
						.then(argument("id", StringArgumentType.word())
								.then(argument("mode", StringArgumentType.word())
										.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("keys", "raw", "path"), b))
										.executes(ctx -> run(ctx, m -> {
											String mode = StringArgumentType.getString(ctx, "mode");
											if (!java.util.List.of("keys", "raw", "path").contains(mode)) {
												throw new IOException("Use keys, raw or path");
											}
											m.useTake(StringArgumentType.getString(ctx, "id"), mode);
											return "Applied " + StringArgumentType.getString(ctx, "id") + " as " + mode;
										})))))
				.then(literal("apply")
						.executes(ctx -> withSession(ctx, s -> "This keeps the scene's block changes up to tick "
								+ s.clock().tick() + " in the world for good. Run /scene apply confirm to do it."))
						.then(literal("confirm").executes(ctx -> withSession(ctx, s -> {
							s.applyToWorld();
							return "The scene's block changes up to tick " + s.clock().tick() + " are now part of the world";
						}))))
				.then(literal("bounds")
						.then(literal("fit").executes(ctx -> withSession(ctx, s -> {
							s.fitBounds(8);
							return "Bounds: " + describe(s.scene().bounds());
						})))
						.then(literal("clear").executes(ctx -> withSession(ctx, s -> {
							s.setBounds(null);
							return "Bounds cleared; no chunks are kept loaded";
						})))
						.then(argument("x1", IntegerArgumentType.integer()).then(argument("y1", IntegerArgumentType.integer())
								.then(argument("z1", IntegerArgumentType.integer()).then(argument("x2", IntegerArgumentType.integer())
										.then(argument("y2", IntegerArgumentType.integer()).then(argument("z2", IntegerArgumentType.integer())
												.executes(ctx -> withSession(ctx, s -> {
													s.setBounds(box(ctx));
													return "Bounds: " + describe(s.scene().bounds());
												}))))))))));
	}

	private static BlockBox box(CommandContext<CommandSourceStack> ctx) {
		int x1 = IntegerArgumentType.getInteger(ctx, "x1");
		int y1 = IntegerArgumentType.getInteger(ctx, "y1");
		int z1 = IntegerArgumentType.getInteger(ctx, "z1");
		int x2 = IntegerArgumentType.getInteger(ctx, "x2");
		int y2 = IntegerArgumentType.getInteger(ctx, "y2");
		int z2 = IntegerArgumentType.getInteger(ctx, "z2");
		return new BlockBox(new BlockPos(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2)),
				new BlockPos(Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)));
	}

	private static String describe(BlockBox b) {
		return b == null ? "none" : b.min().x() + " " + b.min().y() + " " + b.min().z() + " to "
				+ b.max().x() + " " + b.max().y() + " " + b.max().z();
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

	private static net.minecraft.server.level.ServerPlayer player(CommandContext<CommandSourceStack> ctx) throws IOException {
		net.minecraft.server.level.ServerPlayer p = ctx.getSource().getPlayer();
		if (p == null) {
			throw new IOException("Only a player can do that");
		}
		return p;
	}

	private static int tutorial(CommandContext<CommandSourceStack> ctx, String name) {
		return run(ctx, m -> {
			Scene scene = m.createTutorial(name, ctx.getSource().getLevel(),
					net.minecraft.core.BlockPos.containing(ctx.getSource().getPosition()));
			return "Created " + scene.name() + " here. Open the editor (Right Ctrl) and press Play; the knight charges north";
		});
	}

	/** Starts performing an object from the playhead, after a pre-roll of the scene. */
	private static int capture(CommandContext<CommandSourceStack> ctx, int preroll, boolean loop) {
		return run(ctx, m -> {
			SceneSession s = m.session().orElseThrow(() -> new IOException("No scene is open"));
			m.startCapture(player(ctx), StringArgumentType.getString(ctx, "object"),
					s.clock().tick(), -1, preroll, loop);
			return "Capturing after a " + preroll / 20.0 + "s pre-roll; press Right Ctrl or run /scene capture stop to finish";
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
