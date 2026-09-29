package io.github.firestormfmd.scenescripter.client.editor;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.world.entity.Entity;

import io.github.firestormfmd.scenescripter.client.ClientScene;
import io.github.firestormfmd.scenescripter.client.net.ClientNet;
import io.github.firestormfmd.scenescripter.core.edit.Edits;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.ChannelSpec;
import io.github.firestormfmd.scenescripter.core.scene.Gait;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.MotionPath;
import io.github.firestormfmd.scenescripter.core.scene.PathKind;
import io.github.firestormfmd.scenescripter.core.scene.PathMarker;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Properties of the selection on the right. Every animatable value has a key button (the diamond) that keys it at
 * the playhead; clicking a value changes it and keys it.
 */
final class Inspector {
	private static final List<String> POSES = List.of("standing", "crouching", "sleeping", "swimming", "fall_flying",
			"sitting", "spin_attack");
	private static final List<ChannelSpec<String>> EQUIPMENT = List.of(BuiltInChannels.MAINHAND, BuiltInChannels.OFFHAND,
			BuiltInChannels.HEAD, BuiltInChannels.CHEST, BuiltInChannels.LEGS, BuiltInChannels.FEET);

	private final EditorScreen screen;
	private int x;
	private int y;
	private int w;
	private int rowY;
	private int bottom;
	private int scroll;

	Inspector(EditorScreen screen) {
		this.screen = screen;
	}

	void draw(Scene scene, int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		Ui ui = screen.ui();
		ui.panel(x, y, w, h);
		ui.area(x, y, w, h, (b, mx, my) -> {
		});
		rowY = y + 5 - scroll;
		bottom = y + h - 4;
		SceneObject o = ClientScene.object(EditorState.selectedObject).orElse(null);
		MotionPath p = EditorState.selectedPath == null ? null : scene.path(EditorState.selectedPath).orElse(null);
		if (o != null && Scene.isTracks(o)) {
			tracks(o);
		} else if (o != null) {
			object(scene, o);
		} else if (p != null) {
			path(scene, p);
		} else {
			heading("Scene");
			label("Objects: " + scene.objects().size() + "   Paths: " + scene.paths().size());
			label("Length: " + scene.length() + " ticks (" + scene.length() / 20 + "s)");
			label("Select something to edit it.");
			sceneRules(scene);
		}
	}

	// ---- Layout helpers ----

	private boolean visible() {
		return rowY >= y && rowY < bottom - Ui.ROW_HEIGHT;
	}

	private void heading(String text) {
		if (visible()) {
			screen.ui().text(text, x + 6, rowY + 2, Ui.TEXT);
			screen.ui().fill(x + 6, rowY + 11, w - 12, 1, Ui.BORDER);
		}
		rowY += 15;
	}

	private void label(String text) {
		if (visible()) {
			screen.ui().text(screen.ui().fit(text, w - 12), x + 6, rowY + 2, Ui.TEXT_DIM);
		}
		rowY += Ui.ROW_HEIGHT;
	}

	/** A row with a name, a clickable value and an optional key button. */
	private void row(String name, String value, boolean keyed, Runnable onValue, Runnable onKey) {
		if (visible()) {
			Ui ui = screen.ui();
			ui.text(ui.fit(name, 62), x + 6, rowY + 2, Ui.TEXT_DIM);
			int vx = x + 70;
			int vw = w - 70 - (onKey != null ? 18 : 6);
			ui.button(vx, rowY, vw, 11, value, false, onValue);
			if (onKey != null) {
				int kx = x + w - 11;
				ui.diamond(kx, rowY + 5, 4, Ui.KEY, keyed);
				ui.area(kx - 5, rowY, 11, 11, (b, mx, my) -> onKey.run());
			}
		}
		rowY += Ui.ROW_HEIGHT + 1;
	}

	private void edit(String current, Consumer<String> commit) {
		screen.editText(x + 70, rowY, w - 88, current, commit);
	}

	private void editAt(int atY, String current, Consumer<String> commit) {
		screen.editText(x + 70, atY, w - 88, current, commit);
	}

	// ---- Object ----

	private void object(Scene scene, SceneObject o) {
		heading(o.name() + "  (" + EditActions.prettyName(o.entityType()) + ")");
		int nameY = rowY;
		row("Name", o.name(), false, () -> editAt(nameY, o.name(), v -> EditActions.rename(o, v)), null);
		if (o.entityType().equals("minecraft:mannequin")) {
			int skinY = rowY;
			String skin = o.appearance().getOrDefault("skin", "");
			row("Skin", skin.isEmpty() ? "(default)" : skin, false,
					() -> editAt(skinY, skin, v -> EditActions.change(o, "Change skin", c -> c.appearance().put("skin", v.trim()))), null);
		}
		int tick = ClientScene.tick();
		String life = (o.spawnTick() == 0 && o.despawnTick() < 0) ? "always"
				: o.spawnTick() + " to " + (o.despawnTick() < 0 ? "end" : String.valueOf(o.despawnTick()));
		row("Exists", life, false, () -> {
		}, null);
		if (visible()) {
			Ui ui = screen.ui();
			int bw = (w - 18) / 3;
			ui.button(x + 6, rowY, bw, 11, "Spawn here", false, () -> EditActions.change(o, "Set spawn",
					c -> c.setLifetime(tick, c.despawnTick() > tick ? c.despawnTick() : -1)));
			ui.button(x + 9 + bw, rowY, bw, 11, "Leave here", false, () -> {
				if (tick > o.spawnTick()) {
					EditActions.change(o, "Set despawn", c -> c.setLifetime(c.spawnTick(), tick));
				}
			});
			ui.button(x + 12 + 2 * bw, rowY, bw, 11, "Always", false, () -> EditActions.change(o, "Always present",
					c -> c.setLifetime(0, -1)));
		}
		rowY += 14;

		heading("Transform");
		Vec3 pos = currentPosition(o);
		int posY = rowY;
		row("Position", String.format(Locale.ROOT, "%.1f %.1f %.1f", pos.x(), pos.y(), pos.z()),
				EditActions.hasKeyNow(o, "position"), () -> {
					editAt(posY, String.format(Locale.ROOT, "%.2f %.2f %.2f", pos.x(), pos.y(), pos.z()), v -> {
						String[] parts = v.trim().split("\\s+");
						if (parts.length == 3) {
							try {
								EditActions.key(o, BuiltInChannels.POSITION, new Vec3(Double.parseDouble(parts[0]),
										Double.parseDouble(parts[1]), Double.parseDouble(parts[2])));
							} catch (NumberFormatException e) {
								screen.status("Position needs three numbers");
							}
						}
					});
				}, () -> EditActions.key(o, BuiltInChannels.POSITION, pos));
		number(o, BuiltInChannels.BODY_YAW, "Facing");
		number(o, BuiltInChannels.HEAD_YAW, "Head turn");
		number(o, BuiltInChannels.HEAD_PITCH, "Head tilt");
		text(o, BuiltInChannels.LOOK_AT, "Look at");
		number(o, BuiltInChannels.SCALE, "Scale");

		heading("Pose and state");
		String pose = EditActions.valueNow(o, BuiltInChannels.POSE);
		row("Pose", pose, EditActions.hasKeyNow(o, "pose"), () -> {
			int i = POSES.indexOf(pose);
			EditActions.key(o, BuiltInChannels.POSE, POSES.get((i + 1) % POSES.size()));
		}, () -> EditActions.key(o, BuiltInChannels.POSE, pose));
		toggle(o, BuiltInChannels.SNEAKING, "Sneaking");
		toggle(o, BuiltInChannels.SPRINTING, "Sprinting");
		toggle(o, BuiltInChannels.ON_FIRE, "On fire");
		toggle(o, BuiltInChannels.GLOWING, "Glowing");
		toggle(o, BuiltInChannels.INVISIBLE, "Invisible");
		toggle(o, BuiltInChannels.DEAD, "Dead");
		number(o, BuiltInChannels.HEALTH, "Health");
		text(o, BuiltInChannels.CUSTOM_NAME, "Name tag");
		toggle(o, BuiltInChannels.NAME_VISIBLE, "Show name");
		toggle(o, BuiltInChannels.SILENT, "Silent");
		toggle(o, BuiltInChannels.AMBIENT_SOUNDS, "Idle sounds");

		heading("Equipment");
		for (ChannelSpec<String> slot : EQUIPMENT) {
			text(o, slot, slot.name().substring("equipment.".length()));
		}
		String use = EditActions.valueNow(o, BuiltInChannels.USE_ITEM);
		row("Using item", use.isEmpty() ? "no" : use + " hand", EditActions.hasKeyNow(o, "use_item"),
				() -> EditActions.key(o, BuiltInChannels.USE_ITEM, use.isEmpty() ? "main" : use.equals("main") ? "off" : ""),
				() -> EditActions.key(o, BuiltInChannels.USE_ITEM, use));

		Entity actor = ClientScene.actor(o.id()).orElse(null);
		if (actor != null) {
			var caps = io.github.firestormfmd.scenescripter.actor.Capabilities.of(actor);
			if (!caps.isEmpty()) {
				heading(EditActions.prettyName(o.entityType()));
				for (var cap : caps) {
					capability(o, cap);
				}
			}
		}

		heading("Interactions");
		String group = o.group() == null ? "" : o.group();
		int groupY = rowY;
		row("Group", group.isEmpty() ? "none" : group, false, () -> editAt(groupY, group,
				v -> EditActions.change(o, "Set group", c -> c.setGroup(v.isBlank() ? null : v.trim()))), null);
		var rules = o.rules();
		row("Attacks", rules.attack() == null ? "scene default" : rules.attack().id(), false, () -> EditActions.change(o,
				"Change attack rule", c -> c.setRules(c.rules().withAttack(nextMode(c.rules().attack())))), null);
		row("Friendly fire", rules.friendlyFire() == null ? "scene default" : rules.friendlyFire() ? "on" : "off", false,
				() -> EditActions.change(o, "Change friendly fire", c -> {
					var r = c.rules();
					Boolean next = r.friendlyFire() == null ? Boolean.TRUE : r.friendlyFire() ? Boolean.FALSE : null;
					c.setRules(new io.github.firestormfmd.scenescripter.core.scene.InteractionRules(r.attack(), r.knockback(),
							r.crits(), r.hitCooldown(), r.autoDeath(), next, r.explosions()));
				}), null);

		heading("Crowd");
		row("Formation", EditorState.crowdFormation.id(), false, () -> {
			var all = io.github.firestormfmd.scenescripter.core.crowd.Formation.values();
			EditorState.crowdFormation = all[(EditorState.crowdFormation.ordinal() + 1) % all.length];
		}, null);
		int countY = rowY;
		row("Extra members", Integer.toString(EditorState.crowdCount), false, () -> editAt(countY,
				Integer.toString(EditorState.crowdCount), v -> {
					try {
						EditorState.crowdCount = Math.clamp(Integer.parseInt(v.trim()), 1, 200);
					} catch (NumberFormatException e) {
						screen.status("A whole number, 1 to 200");
					}
				}), null);
		int spacingY = rowY;
		row("Spacing", EditorState.crowdSpacing + " blocks", false, () -> editAt(spacingY,
				Double.toString(EditorState.crowdSpacing), v -> {
					try {
						EditorState.crowdSpacing = Math.clamp(Double.parseDouble(v.trim()), 0.5, 16);
					} catch (NumberFormatException e) {
						screen.status("Spacing is a number of blocks");
					}
				}), null);
		if (visible()) {
			screen.ui().button(x + 6, rowY, w - 12, 11, "Make crowd", false, () -> makeCrowd(scene, o));
		}
		rowY += 14;

		heading("Motion");
		for (MotionClip clip : o.motion()) {
			String desc = clip.pathId() + " @" + clip.startTick()
					+ (clip.timing() == io.github.firestormfmd.scenescripter.core.scene.TimingMode.FIT ? "-" + clip.endTick() : "");
			row(ClientScene.scene().flatMap(s -> s.path(clip.pathId())).map(MotionPath::name).orElse("?"), desc + "  (remove)", false,
					() -> EditActions.change(o, "Remove motion", c -> c.removeMotion(clip)), null);
		}
		if (EditorState.lastPath != null && scene.path(EditorState.lastPath).isPresent()) {
			String pathName = scene.path(EditorState.lastPath).get().name();
			row("Walk", "Start \"" + pathName + "\" here", false, () -> EditActions.change(o, "Add motion",
					c -> c.addMotion(MotionClip.atSpeed(EditorState.lastPath, tick))), null);
		} else {
			label("Draw a path to make this walk.");
		}

		heading("Performance capture");
		var st = ClientScene.state();
		boolean ranged = st.loopStart() >= 0 && st.loopEnd() > st.loopStart();
		int punchIn = ranged ? st.loopStart() : tick;
		int punchOut = ranged ? st.loopEnd() : -1;
		int prerollY = rowY;
		row("Pre-roll", EditorState.capturePreroll / 20.0 + "s", false, () -> editAt(prerollY,
				Double.toString(EditorState.capturePreroll / 20.0), v -> {
					try {
						EditorState.capturePreroll = (int) Math.round(Math.max(0, Double.parseDouble(v.trim())) * 20);
					} catch (NumberFormatException e) {
						screen.status("Pre-roll is a number of seconds");
					}
				}), null);
		label(ranged ? "Records the loop range " + punchIn + "-" + punchOut : "Records from tick " + punchIn + " to the end");
		if (visible()) {
			Ui ui = screen.ui();
			int bw = (w - 15) / 2;
			ui.button(x + 6, rowY, bw, 11, "Capture", false, () ->
					ClientNet.startCapture(o.id(), punchIn, punchOut, EditorState.capturePreroll, false));
			ui.button(x + 9 + bw, rowY, bw, 11, "Loop capture", false, () ->
					ClientNet.startCapture(o.id(), punchIn, punchOut, EditorState.capturePreroll, true));
		}
		rowY += 14;
		for (var take : ClientScene.takes()) {
			if (!take.objectId().equals(o.id())) {
				continue;
			}
			if (visible()) {
				Ui ui = screen.ui();
				ui.text(ui.fit(take.name() + " " + take.start() + "-" + take.end(), w - 110), x + 6, rowY + 2, Ui.TEXT_DIM);
				int bx = x + w - 102;
				ui.button(bx, rowY, 30, 11, "keys", false, () -> ClientNet.useTake(take.id(), "keys"));
				ui.button(bx + 32, rowY, 30, 11, "raw", false, () -> ClientNet.useTake(take.id(), "raw"));
				ui.button(bx + 64, rowY, 32, 11, "path", false, () -> ClientNet.useTake(take.id(), "path"));
			}
			rowY += Ui.ROW_HEIGHT + 1;
		}

		if (o.entityType().equals("minecraft:tnt") || o.entityType().equals("minecraft:creeper")
				|| o.entityType().equals("minecraft:end_crystal") || o.entityType().equals("minecraft:fireball")) {
			heading("Explosion");
			boolean tnt = o.entityType().equals("minecraft:tnt");
			boolean creeper = o.entityType().equals("minecraft:creeper");
			if (tnt || creeper) {
				String fuse = o.appearance().getOrDefault("fuse", tnt ? "80" : "30");
				int fuseY = rowY;
				row("Fuse", fuse + " ticks", false, () -> editAt(fuseY, fuse, v -> EditActions.change(o, "Set fuse",
						c -> c.appearance().put("fuse", v.trim()))), null);
			}
			String power = o.appearance().getOrDefault("power", "");
			int powerY = rowY;
			row("Power", power.isEmpty() ? "default" : power, false, () -> editAt(powerY, power,
					v -> EditActions.change(o, "Set power", c -> {
						if (v.isBlank()) {
							c.appearance().remove("power");
						} else {
							c.appearance().put("power", v.trim());
						}
					})), null);
			if (creeper) {
				boolean charged = "true".equals(o.appearance().get("powered"));
				row("Charged", charged ? "yes" : "no", false, () -> EditActions.change(o, "Toggle charged",
						c -> c.appearance().put("powered", Boolean.toString(!charged))), null);
			}
			if (tnt) {
				label("Explodes when its fuse runs out unless an");
				label("explode event comes first.");
			}
		}

		heading("Events");
		if (visible()) {
			Ui ui = screen.ui();
			int bw = (w - 18) / 4;
			String[] types = {"attack", "hurt", "die", "revive"};
			for (int i = 0; i < types.length; i++) {
				String type = types[i];
				ui.button(x + 6 + i * (bw + 2), rowY, bw, 11, type, false, () -> EventTools.add(o, type, tick));
			}
		}
		rowY += 13;
		if (visible()) {
			Ui ui = screen.ui();
			int bw = (w - 18) / 4;
			String[] types = {"shoot", "explode", "ignite", "swing"};
			for (int i = 0; i < types.length; i++) {
				String type = types[i];
				ui.button(x + 6 + i * (bw + 2), rowY, bw, 11, type, false, () -> EventTools.add(o, type, tick));
			}
		}
		rowY += 13;
		String vehicle = EditActions.valueNow(o, BuiltInChannels.VEHICLE);
		row("Riding", vehicle.isEmpty() ? "nothing (mount nearest)" : vehicle + " (dismount)", EditActions.hasKeyNow(o, "vehicle"),
				() -> EventTools.add(o, vehicle.isEmpty() ? "mount" : "dismount", tick), null);
		for (var e : o.events()) {
			if (!visible()) {
				rowY += Ui.ROW_HEIGHT;
				continue;
			}
			String desc = e.type() + " @" + e.tick() + (e.target() != null ? " -> " + e.target() : "")
					+ (e.isGenerated() ? " (auto)" : "");
			row(e.isGenerated() ? "auto" : "event", desc, false, () -> {
				if (!e.isGenerated()) {
					ClientNet.edit(new Edits.RemoveEvent(o.id(), e.id()));
				}
			}, null);
		}

		rowY += 6;
		if (visible()) {
			screen.ui().button(x + 6, rowY, w - 12, 12, "Delete object", false, () -> EditActions.delete(o));
		}
		rowY += 16;
	}

	private static io.github.firestormfmd.scenescripter.core.scene.AttackMode nextMode(
			io.github.firestormfmd.scenescripter.core.scene.AttackMode mode) {
		var all = io.github.firestormfmd.scenescripter.core.scene.AttackMode.values();
		if (mode == null) {
			return all[0];
		}
		return mode.ordinal() + 1 < all.length ? all[mode.ordinal() + 1] : null;
	}

	/** Copies the object into a crowd laid out in the chosen formation, in one undoable step. */
	private void makeCrowd(Scene scene, SceneObject o) {
		java.util.Set<String> taken = new java.util.HashSet<>();
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (int n = scene.objects().size() + 1; ids.size() < EditorState.crowdCount; n++) {
			String id = "o" + n;
			if (scene.object(id).isEmpty() && scene.findEventOwner(id).isEmpty() && taken.add(id)) {
				ids.add(id);
			}
		}
		var members = io.github.firestormfmd.scenescripter.core.crowd.CrowdBuilder.build(o, EditorState.crowdFormation,
				EditorState.crowdCount, EditorState.crowdSpacing, i -> ids.get(i - 1), 4, 6, o.id().hashCode());
		java.util.List<io.github.firestormfmd.scenescripter.core.edit.EditOp> ops = new java.util.ArrayList<>();
		int index = scene.indexOfObject(o.id()) + 1;
		for (var m : members) {
			ops.add(new Edits.AddObject(m, index++));
		}
		ClientNet.edit(new Edits.Composite("Make crowd", ops));
		screen.status("Added " + members.size() + " crowd members");
	}

	@SuppressWarnings("unchecked")
	private void capability(SceneObject o, io.github.firestormfmd.scenescripter.actor.Capabilities.Capability cap) {
		ChannelSpec<?> spec = cap.spec();
		Object value = o.channel(spec.name()).map(ch -> (Object) ch.valueAt(ClientScene.tick())).orElse(spec.defaultValue());
		boolean keyed = EditActions.hasKeyNow(o, spec.name());
		int ry = rowY;
		if (spec.type() == io.github.firestormfmd.scenescripter.core.anim.ValueType.BOOL) {
			boolean b = Boolean.TRUE.equals(value);
			row(cap.label(), b ? "yes" : "no", keyed, () -> keyCapability(o, spec, !b), () -> keyCapability(o, spec, b));
		} else if (spec.name().equals("wool_color")) {
			var colors = io.github.firestormfmd.scenescripter.actor.Capabilities.COLORS;
			String c = String.valueOf(value);
			row(cap.label(), c, keyed, () -> keyCapability(o, spec, colors.get((colors.indexOf(c) + 1) % colors.size())),
					() -> keyCapability(o, spec, c));
		} else {
			String t = String.valueOf(value);
			row(cap.label(), t.isEmpty() ? "-" : t, keyed, () -> editAt(ry, t, v -> keyCapability(o, spec, v.trim())),
					() -> keyCapability(o, spec, t));
		}
	}

	/** Keys a mob-specific channel, creating it on the object the first time. */
	@SuppressWarnings("unchecked")
	private static void keyCapability(SceneObject o, ChannelSpec<?> spec, Object value) {
		int tick = ClientScene.tick();
		EditActions.change(o, "Key " + spec.name(), c -> ((io.github.firestormfmd.scenescripter.core.anim.Channel<Object>)
				(io.github.firestormfmd.scenescripter.core.anim.Channel<?>) c.channel(spec)).put(
				new io.github.firestormfmd.scenescripter.core.anim.Keyframe<>(tick, value,
						io.github.firestormfmd.scenescripter.core.anim.Interpolation.STEP, null, null)));
	}

	// ---- Scene track ----

	private void tracks(SceneObject t) {
		int tick = ClientScene.tick();
		heading("Scene track");
		label("Time and weather change the world only once keyed.");
		int time = EditActions.valueNow(t, BuiltInChannels.TIME_OF_DAY);
		int timeY = rowY;
		row("Time of day", time + (time == 6000 ? " (noon)" : time == 18000 ? " (midnight)" : ""),
				EditActions.hasKeyNow(t, BuiltInChannels.TIME_OF_DAY.name()), () -> editAt(timeY, Integer.toString(time), v -> {
					try {
						EditActions.key(t, BuiltInChannels.TIME_OF_DAY, Math.floorMod(Integer.parseInt(v.trim()), 24000));
					} catch (NumberFormatException e) {
						screen.status("Time of day is a tick count from 0 to 24000");
					}
				}), () -> EditActions.key(t, BuiltInChannels.TIME_OF_DAY, time));
		String weather = EditActions.valueNow(t, BuiltInChannels.WEATHER);
		row("Weather", weather, EditActions.hasKeyNow(t, BuiltInChannels.WEATHER.name()), () -> EditActions.key(t,
				BuiltInChannels.WEATHER, weather.equals("clear") ? "rain" : weather.equals("rain") ? "thunder" : "clear"),
				() -> EditActions.key(t, BuiltInChannels.WEATHER, weather));

		heading("Add at the playhead");
		if (visible()) {
			Ui ui = screen.ui();
			int bw = (w - 18) / 3;
			int ay = rowY;
			ui.button(x + 6, rowY, bw, 11, "Sound", false, () -> screen.editText(x + 6, ay, w - 12,
					"entity.lightning_bolt.thunder", v -> addTrackEvent(t, tick, "sound", java.util.Map.of("sound", v.trim()))));
			ui.button(x + 9 + bw, rowY, bw, 11, "Command", false, () -> screen.editText(x + 6, ay, w - 12,
					"say Action! | say Cut", v -> {
						String[] parts = v.split("\\|", 2);
						java.util.Map<String, Object> params = new java.util.HashMap<>();
						params.put("command", parts[0].trim());
						if (parts.length > 1 && !parts[1].isBlank()) {
							params.put("undo", parts[1].trim());
						}
						addTrackEvent(t, tick, "command", params);
					}));
			ui.button(x + 12 + 2 * bw, rowY, bw, 11, "Marker", false, () -> screen.editText(x + 6, ay, w - 12,
					"Marker", v -> addTrackEvent(t, tick, "marker", java.util.Map.of("name", v.trim()))));
		}
		rowY += 14;
		label("Command: \"command | undo command\"");
		for (var e : t.events()) {
			String desc = e.type() + " @" + e.tick() + ": " + e.params().getOrDefault("sound",
					e.params().getOrDefault("command", e.params().getOrDefault("name", "")));
			row("event", desc + "  (remove)", false, () -> ClientNet.edit(new Edits.RemoveEvent(t.id(), e.id())), null);
		}
	}

	private static void addTrackEvent(SceneObject t, int tick, String type, java.util.Map<String, Object> params) {
		ClientScene.scene().ifPresent(scene -> ClientNet.edit(new Edits.AddEvent(t.id(),
				new io.github.firestormfmd.scenescripter.core.scene.SceneEvent(EditActions.freshId(scene, "e"), tick, type,
						null, params, null))));
	}

	// ---- Scene-wide rules ----

	private void sceneRules(Scene scene) {
		heading("Interaction rules");
		var settings = scene.settings();
		var r = settings.rules();
		row("Attacks", r.attack() == null ? "auto" : r.attack().id(), false, () -> setRules(scene,
				r.withAttack(nextMode(r.attack()) == null ? io.github.firestormfmd.scenescripter.core.scene.AttackMode.AUTO
						: nextMode(r.attack()))), null);
		boolean ff = Boolean.TRUE.equals(r.friendlyFire());
		row("Friendly fire", ff ? "on" : "off", false, () -> setRules(scene,
				new io.github.firestormfmd.scenescripter.core.scene.InteractionRules(r.attack(), r.knockback(), r.crits(),
						r.hitCooldown(), r.autoDeath(), !ff, r.explosions())), null);
		boolean death = !Boolean.FALSE.equals(r.autoDeath());
		row("Auto death", death ? "on" : "off", false, () -> setRules(scene,
				new io.github.firestormfmd.scenescripter.core.scene.InteractionRules(r.attack(), r.knockback(), r.crits(),
						r.hitCooldown(), !death, r.friendlyFire(), r.explosions())), null);
		var ex = r.explosions();
		boolean breaks = !Boolean.FALSE.equals(ex.breakBlocks());
		boolean hurtsReal = Boolean.TRUE.equals(ex.damageRealEntities());
		boolean fire = Boolean.TRUE.equals(ex.fire());
		row("Blasts break blocks", breaks ? "yes" : "no", false, () -> setRules(scene, withExplosions(r,
				new io.github.firestormfmd.scenescripter.core.scene.ExplosionRules(!breaks, ex.damageObjects(),
						ex.damageRealEntities(), ex.dropItems(), ex.fire()))), null);
		row("Blasts hurt players", hurtsReal ? "yes" : "no", false, () -> setRules(scene, withExplosions(r,
				new io.github.firestormfmd.scenescripter.core.scene.ExplosionRules(ex.breakBlocks(), ex.damageObjects(),
						!hurtsReal, ex.dropItems(), ex.fire()))), null);
		row("Blasts start fires", fire ? "yes" : "no", false, () -> setRules(scene, withExplosions(r,
				new io.github.firestormfmd.scenescripter.core.scene.ExplosionRules(ex.breakBlocks(), ex.damageObjects(),
						ex.damageRealEntities(), ex.dropItems(), !fire))), null);
	}

	private static io.github.firestormfmd.scenescripter.core.scene.InteractionRules withExplosions(
			io.github.firestormfmd.scenescripter.core.scene.InteractionRules r,
			io.github.firestormfmd.scenescripter.core.scene.ExplosionRules ex) {
		return new io.github.firestormfmd.scenescripter.core.scene.InteractionRules(r.attack(), r.knockback(), r.crits(),
				r.hitCooldown(), r.autoDeath(), r.friendlyFire(), ex);
	}

	private static void setRules(Scene scene, io.github.firestormfmd.scenescripter.core.scene.InteractionRules rules) {
		var h = Edits.SetSceneHeader.of(scene);
		ClientNet.edit(new Edits.SetSceneHeader(h.name(), h.length(), h.origin(), h.bounds(), h.settings().withRules(rules)));
	}

	private Vec3 currentPosition(SceneObject o) {
		Entity actor = ClientScene.actor(o.id()).orElse(null);
		if (actor != null) {
			return new Vec3(actor.getX(), actor.getY(), actor.getZ());
		}
		return EditActions.valueNow(o, BuiltInChannels.POSITION);
	}

	private void number(SceneObject o, ChannelSpec<Double> spec, String name) {
		double value = EditActions.valueNow(o, spec);
		int ry = rowY;
		row(name, String.format(Locale.ROOT, "%.2f", value), EditActions.hasKeyNow(o, spec.name()),
				() -> editAt(ry, String.format(Locale.ROOT, "%.2f", value), v -> {
					try {
						EditActions.key(o, spec, Double.parseDouble(v.trim()));
					} catch (NumberFormatException e) {
						screen.status(name + " must be a number");
					}
				}), () -> EditActions.key(o, spec, value));
	}

	private void text(SceneObject o, ChannelSpec<String> spec, String name) {
		String value = EditActions.valueNow(o, spec);
		int ry = rowY;
		row(name, value.isEmpty() ? "-" : value, EditActions.hasKeyNow(o, spec.name()),
				() -> editAt(ry, value, v -> EditActions.key(o, spec, v.trim())), () -> EditActions.key(o, spec, value));
	}

	private void toggle(SceneObject o, ChannelSpec<Boolean> spec, String name) {
		boolean value = EditActions.valueNow(o, spec);
		row(name, value ? "yes" : "no", EditActions.hasKeyNow(o, spec.name()),
				() -> EditActions.key(o, spec, !value), () -> EditActions.key(o, spec, value));
	}

	/** Keys the selected actor's current position at the playhead. */
	void keyPosition(Scene scene) {
		ClientScene.object(EditorState.selectedObject).ifPresent(o -> {
			EditActions.key(o, BuiltInChannels.POSITION, currentPosition(o));
			screen.status("Keyed position at tick " + ClientScene.tick());
		});
	}

	// ---- Path ----

	private void path(Scene scene, MotionPath p) {
		heading("Path: " + p.name());
		int nameY = rowY;
		row("Name", p.name(), false, () -> editAt(nameY, p.name(), v -> changePath(p, "Rename path", c -> c.setName(v.trim()))), null);
		row("Kind", p.kind().id(), false, () -> changePath(p, "Change path kind",
				c -> c.setKind(c.kind() == PathKind.GROUND ? PathKind.AIR : PathKind.GROUND)), null);
		row("Gait", p.gait().id(), false, () -> changePath(p, "Change gait", c -> {
			Gait next = Gait.values()[(c.gait().ordinal() + 1) % Gait.values().length];
			c.setGait(next);
			c.setBaseSpeed(next.playerSpeed());
		}), null);
		int speedY = rowY;
		row("Speed", String.format(Locale.ROOT, "%.2f blocks/s", p.baseSpeed()), false,
				() -> editAt(speedY, String.valueOf(p.baseSpeed()), v -> {
					try {
						double s = Double.parseDouble(v.trim());
						changePath(p, "Change speed", c -> c.setBaseSpeed(Math.max(0.1, s)));
					} catch (NumberFormatException e) {
						screen.status("Speed must be a number");
					}
				}), null);
		int jumpY = rowY;
		row("Jump height", p.jumpHeight() == null ? "vanilla" : String.format(Locale.ROOT, "%.2f", p.jumpHeight()), false,
				() -> editAt(jumpY, p.jumpHeight() == null ? "" : String.valueOf(p.jumpHeight()), v -> {
					try {
						Double jh = v.isBlank() ? null : Double.parseDouble(v.trim());
						changePath(p, "Change jump height", c -> c.setJumpHeight(jh));
					} catch (NumberFormatException | IllegalStateException e) {
						screen.status("Jump height must be a number, or empty for vanilla");
					}
				}), null);
		label(p.points().size() + " points; drag them in the world");

		heading("Markers");
		for (PathMarker m : p.markers()) {
			String desc = m.kind().id() + " at " + Math.round(m.at() * 100) + "%"
					+ (m.kind() == PathMarker.Kind.WAIT ? " (" + m.ticks() + " ticks)" : "")
					+ (m.kind() == PathMarker.Kind.GAIT ? " (" + m.gait().id() + ")" : "");
			row(m.kind().id(), desc + "  (remove)", false, () -> changePath(p, "Remove marker", c -> {
				List<PathMarker> ms = new java.util.ArrayList<>(c.markers());
				ms.remove(m);
				c.setMarkers(ms);
			}), null);
		}
		int addY = rowY;
		row("Add", "jump / wait / gait at %...", false, () -> editAt(addY, "jump 50", v -> addMarker(p, v)), null);
		label("e.g. \"jump 50\", \"wait 25 40\", \"gait 60 sprint\"");

		heading("Use");
		for (SceneObject o : scene.objects()) {
			if (!visible()) {
				rowY += Ui.ROW_HEIGHT + 1;
				continue;
			}
			int tick = ClientScene.tick();
			row(o.name(), "walk this from tick " + tick, false, () -> EditActions.change(o, "Add motion",
					c -> c.addMotion(MotionClip.atSpeed(p.id(), tick))), null);
		}
		rowY += 6;
		if (visible()) {
			screen.ui().button(x + 6, rowY, w - 12, 12, "Delete path", false, () -> {
				ClientNet.edit(new Edits.RemovePath(p.id()));
				EditorState.selectPath(null);
			});
		}
		rowY += 16;
	}

	private void addMarker(MotionPath p, String spec) {
		String[] parts = spec.trim().toLowerCase(Locale.ROOT).split("\\s+");
		try {
			double at = Math.clamp(Double.parseDouble(parts[1]) / 100.0, 0, 1);
			PathMarker marker = switch (parts[0]) {
				case "jump" -> PathMarker.jump(at);
				case "wait" -> PathMarker.waitFor(at, Integer.parseInt(parts[2]));
				case "gait" -> PathMarker.gait(at, Gait.byId(parts[2]));
				default -> throw new IllegalArgumentException("Unknown marker " + parts[0]);
			};
			changePath(p, "Add marker", c -> {
				List<PathMarker> ms = new java.util.ArrayList<>(c.markers());
				ms.add(marker);
				c.setMarkers(ms);
			});
		} catch (RuntimeException e) {
			screen.status("Try \"jump 50\", \"wait 25 40\" or \"gait 60 sprint\"");
		}
	}

	static void changePath(MotionPath p, String label, Consumer<MotionPath> change) {
		MotionPath copy = p.copy();
		change.accept(copy);
		ClientNet.edit(new Edits.ReplacePath(copy, label));
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (mx < x || mx > x + w) {
			return false;
		}
		scroll = Math.max(0, scroll - (int) (amount * 12));
		return true;
	}
}
