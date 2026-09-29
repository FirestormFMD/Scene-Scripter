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
	/** Poses only some mobs animate, such as a warden digging back into the ground. */
	private static final java.util.Map<String, List<String>> MOB_POSES = java.util.Map.of(
			"minecraft:warden", List.of("roaring", "sniffing", "emerging", "digging"),
			"minecraft:frog", List.of("croaking", "using_tongue", "long_jumping"),
			"minecraft:goat", List.of("long_jumping"),
			"minecraft:breeze", List.of("sliding", "shooting", "inhaling"));
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

	/** Look switches some types have: entity type, appearance key, label, and whether it is on by default. */
	private static final String[][] APPEARANCE_TOGGLES = {
			{"minecraft:creeper", "powered", "Charged", "off"},
			{"minecraft:end_crystal", "base", "Bedrock base", "on"},
			{"minecraft:arrow", "crit", "Critical trail", "off"},
			{"minecraft:spectral_arrow", "crit", "Critical trail", "off"},
	};

	/** What displays, falling blocks and dropped items show, and whether a mob is a baby. */
	private void contentRows(SceneObject o) {
		String type = o.entityType();
		if (type.equals("minecraft:falling_block") || type.equals("minecraft:block_display")) {
			appearanceText(o, "block", "Block", "minecraft:stone");
		}
		if (type.equals("minecraft:item") || type.equals("minecraft:item_display")) {
			appearanceText(o, "item", "Item", "minecraft:diamond");
		}
		if (type.equals("minecraft:text_display")) {
			appearanceText(o, "text", "Text", o.name());
		}
		if (type.endsWith("_display")) {
			List<String> modes = List.of("fixed", "vertical", "horizontal", "center");
			String mode = o.appearance().getOrDefault("billboard", type.equals("minecraft:text_display") ? "center" : "fixed");
			row("Faces camera", mode, false, () -> EditActions.change(o, "Change billboard",
					c -> c.appearance().put("billboard", modes.get((modes.indexOf(mode) + 1) % modes.size()))), null);
		}
		Entity actor = ClientScene.actor(o.id()).orElse(null);
		if (actor instanceof net.minecraft.world.entity.AgeableMob || actor instanceof net.minecraft.world.entity.monster.zombie.Zombie
				|| actor instanceof net.minecraft.world.entity.monster.piglin.Piglin
				|| actor instanceof net.minecraft.world.entity.monster.Zoglin) {
			boolean baby = "true".equals(o.appearance().get("baby"));
			row("Baby", baby ? "yes" : "no", false, () -> EditActions.change(o, "Change baby", c -> {
				if (baby) {
					c.appearance().remove("baby");
				} else {
					c.appearance().put("baby", "true");
				}
			}), null);
		}
	}

	private void appearanceText(SceneObject o, String key, String label, String example) {
		int ry = rowY;
		String value = o.appearance().getOrDefault(key, "");
		row(label, value.isEmpty() ? "-" : value, false, () -> editAt(ry, value.isEmpty() ? example : value,
				v -> EditActions.change(o, "Change " + label.toLowerCase(Locale.ROOT), c -> {
					if (v.isBlank()) {
						c.appearance().remove(key);
					} else {
						c.appearance().put(key, v.trim());
					}
				})), null);
	}

	private void object(Scene scene, SceneObject o) {
		heading(o.name() + "  (" + EditActions.prettyName(o.entityType()) + ")");
		int nameY = rowY;
		row("Name", o.name(), false, () -> editAt(nameY, o.name(), v -> EditActions.rename(o, v)), null);
		if (o.entityType().equals("minecraft:mannequin")) {
			int skinY = rowY;
			String skin = o.appearance().getOrDefault("skin", "");
			row("Skin", skin.isEmpty() ? "(default)" : skin, false,
					() -> editAt(skinY, skin, v -> EditActions.change(o, "Change skin", c -> c.appearance().put("skin", v.trim()))), null);
			int textureY = rowY;
			String texture = o.appearance().getOrDefault("skin_texture", "");
			row("Skin file", texture.isEmpty() ? "none" : texture, false, () -> editAt(textureY, texture.isEmpty()
					? "myskins:entity/knight" : texture, v -> EditActions.change(o, "Change skin file", c -> {
						if (v.isBlank()) {
							c.appearance().remove("skin_texture");
						} else {
							c.appearance().put("skin_texture", v.trim());
						}
					})), null);
			String model = o.appearance().getOrDefault("model", "");
			row("Arms", model.isEmpty() ? "from skin" : model, false, () -> EditActions.change(o, "Change arm model", c -> {
				if (model.isEmpty()) {
					c.appearance().put("model", "slim");
				} else if (model.equals("slim")) {
					c.appearance().put("model", "wide");
				} else {
					c.appearance().remove("model");
				}
			}), null);
		}
		for (String[] toggle : APPEARANCE_TOGGLES) {
			if (!toggle[0].equals(o.entityType())) {
				continue;
			}
			String key = toggle[1];
			boolean byDefault = toggle[3].equals("on");
			boolean on = o.appearance().containsKey(key) ? "true".equals(o.appearance().get(key)) : byDefault;
			row(toggle[2], on ? "yes" : "no", false, () -> EditActions.change(o, "Change " + toggle[2].toLowerCase(Locale.ROOT), c -> {
				if (!on == byDefault) {
					c.appearance().remove(key);
				} else {
					c.appearance().put(key, String.valueOf(!on));
				}
			}), null);
		}
		contentRows(o);
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
		for (String effect : List.of("spawn_effect", "despawn_effect")) {
			boolean poof = "poof".equals(o.appearance().get(effect));
			row(effect.equals("spawn_effect") ? "Appears" : "Leaves", poof ? "in a puff of smoke" : "quietly", false,
					() -> EditActions.change(o, "Change " + effect.replace('_', ' '), c -> {
						if (poof) {
							c.appearance().remove(effect);
						} else {
							c.appearance().put(effect, "poof");
						}
					}), null);
		}

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
		List<String> poses = new java.util.ArrayList<>(POSES);
		poses.addAll(MOB_POSES.getOrDefault(o.entityType(), List.of()));
		row("Pose", pose, EditActions.hasKeyNow(o, "pose"), () -> {
			int i = poses.indexOf(pose);
			EditActions.key(o, BuiltInChannels.POSE, poses.get((i + 1) % poses.size()));
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
		List<ChannelSpec<String>> slots = new java.util.ArrayList<>(EQUIPMENT);
		Entity wearer = ClientScene.actor(o.id()).orElse(null);
		if (wearer instanceof net.minecraft.world.entity.animal.equine.AbstractHorse
				|| wearer instanceof net.minecraft.world.entity.animal.wolf.Wolf) {
			// Horse armor, saddles, wolf armor and llama carpets.
			slots.add(BuiltInChannels.BODY);
			slots.add(BuiltInChannels.SADDLE);
		}
		for (ChannelSpec<String> slot : slots) {
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

		if (o.group() != null) {
			var members = scene.objects().stream().filter(m -> o.group().equals(m.group())).toList();
			label("Group of " + members.size() + ":");
			int moveY = rowY;
			row("Move group", "by x y z...", false, () -> editAt(moveY, "0 0 0", v -> {
				String[] c = v.trim().split("\\s+");
				try {
					var by = new Vec3(Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]));
					groupEdit(members, "Move group", m -> io.github.firestormfmd.scenescripter.core.scene.SceneTransform.moveObject(m, by));
				} catch (RuntimeException ex) {
					screen.status("Three numbers, such as \"4 0 -2\"");
				}
			}), null);
			int retimeY = rowY;
			row("Retime group", "by ticks...", false, () -> editAt(retimeY, "20", v -> {
				try {
					int by = Integer.parseInt(v.trim());
					groupEdit(members, "Retime group", m -> io.github.firestormfmd.scenescripter.core.scene.SceneTransform.retimeObject(m, by));
				} catch (NumberFormatException ex) {
					screen.status("A whole number of ticks; negative moves earlier");
				}
			}), null);
		}

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
		if (visible()) {
			Ui ui = screen.ui();
			int bw = (w - 18) / 4;
			if (o.entityType().equals("minecraft:creeper")) {
				ui.button(x + 6, rowY, bw, 11, "defuse", false, () -> EventTools.add(o, "defuse", tick));
			}
			int teleportY = rowY;
			Vec3 here = currentPosition(o);
			ui.button(x + 6 + bw + 2, rowY, bw * 2 + 2, 11, "teleport to...", false, () -> editAt(teleportY,
					String.format(Locale.ROOT, "%.1f %.1f %.1f", here.x(), here.y(), here.z()), v -> {
						String[] c = v.trim().split("\\s+");
						try {
							EventTools.teleport(o, tick, new Vec3(Double.parseDouble(c[0]), Double.parseDouble(c[1]),
									Double.parseDouble(c[2])));
						} catch (RuntimeException ex) {
							screen.status("Three numbers: x y z");
						}
					}));
			ui.button(x + 6 + 3 * (bw + 2), rowY, bw, 11, "throw...", false, () -> editAt(teleportY,
					String.format(Locale.ROOT, "%.1f %.1f %.1f", here.x() + 6, here.y(), here.z()), v -> {
						if (v.trim().split("\\s+").length != 3) {
							screen.status("Where it should land: x y z");
							return;
						}
						ClientScene.scene().ifPresent(sc -> ClientNet.edit(new Edits.AddEvent(o.id(),
								new io.github.firestormfmd.scenescripter.core.scene.SceneEvent(EditActions.freshId(sc, "e"), tick,
										"launch", null, java.util.Map.of("to", v.trim()), null))));
					}));
		}
		rowY += 13;
		var mobEvents = io.github.firestormfmd.scenescripter.actor.MobEvents.forType(o.entityType());
		for (int start = 0; start < mobEvents.size(); start += 3) {
			if (visible()) {
				Ui ui = screen.ui();
				int bw = (w - 16) / 3;
				for (int i = start; i < Math.min(start + 3, mobEvents.size()); i++) {
					var m = mobEvents.get(i);
					ui.button(x + 6 + (i - start) * (bw + 2), rowY, bw, 11, ui.fit(m.label(), bw - 4), false,
							() -> EventTools.mobEvent(o, m.id(), tick));
				}
			}
			rowY += 13;
		}
		String vehicle = EditActions.valueNow(o, BuiltInChannels.VEHICLE);
		row("Riding", vehicle.isEmpty() ? "nothing (mount nearest)" : vehicle + " (dismount)", EditActions.hasKeyNow(o, "vehicle"),
				() -> EventTools.add(o, vehicle.isEmpty() ? "mount" : "dismount", tick), null);
		for (var e : o.events()) {
			eventRow(o, e);
		}

		variables(o);

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
				EditorState.crowdCount, EditorState.crowdSpacing, i -> ids.get(i - 1), 4, 6, 0.1, o.id().hashCode());
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
		} else if (!io.github.firestormfmd.scenescripter.actor.Capabilities.options(spec.name()).isEmpty()) {
			var options = io.github.firestormfmd.scenescripter.actor.Capabilities.options(spec.name());
			String c = String.valueOf(value);
			row(cap.label(), c, keyed, () -> keyCapability(o, spec, options.get((options.indexOf(c) + 1) % options.size())),
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
		variables(t);
		label("Scene variables are read as scene.<name>.");
	}

	private static void addTrackEvent(SceneObject t, int tick, String type, java.util.Map<String, Object> params) {
		ClientScene.scene().ifPresent(scene -> ClientNet.edit(new Edits.AddEvent(t.id(),
				new io.github.firestormfmd.scenescripter.core.scene.SceneEvent(EditActions.freshId(scene, "e"), tick, type,
						null, params, null))));
	}

	// ---- Scene-wide rules ----

	private void sceneRules(Scene scene) {
		heading("Ground for paths");
		var settings0 = scene.settings();
		var filter = settings0.groundFilter();
		row("Preset", filter.preset(), false, () -> setGround(scene, filter.nextPreset()), null);
		int excludeY = rowY;
		String excludes = String.join(", ", filter.exclude());
		row("Not ground", excludes.isEmpty() ? "-" : excludes, false, () -> editAt(excludeY, excludes,
				v -> setGround(scene, filter.withLists(filter.include(), splitList(v)))), null);
		int includeY = rowY;
		String includes = String.join(", ", filter.include());
		row("Also ground", includes.isEmpty() ? "-" : includes, false, () -> editAt(includeY, includes,
				v -> setGround(scene, filter.withLists(splitList(v), filter.exclude()))), null);
		var fluids = io.github.firestormfmd.scenescripter.core.scene.FluidMode.values();
		row("Water", filter.fluids().id(), false, () -> setGround(scene,
				filter.withFluids(fluids[(filter.fluids().ordinal() + 1) % fluids.length])), null);
		label("Blocks by ID, tags with #, separated by commas.");

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
		boolean drops = Boolean.TRUE.equals(ex.dropItems());
		row("Blasts drop items", drops ? "yes" : "no", false, () -> setRules(scene, withExplosions(r,
				new io.github.firestormfmd.scenescripter.core.scene.ExplosionRules(ex.breakBlocks(), ex.damageObjects(),
						ex.damageRealEntities(), !drops, ex.fire()))), null);
	}

	private static io.github.firestormfmd.scenescripter.core.scene.InteractionRules withExplosions(
			io.github.firestormfmd.scenescripter.core.scene.InteractionRules r,
			io.github.firestormfmd.scenescripter.core.scene.ExplosionRules ex) {
		return new io.github.firestormfmd.scenescripter.core.scene.InteractionRules(r.attack(), r.knockback(), r.crits(),
				r.hitCooldown(), r.autoDeath(), r.friendlyFire(), ex);
	}

	private static List<String> splitList(String v) {
		List<String> out = new java.util.ArrayList<>();
		for (String part : v.split(",")) {
			if (!part.isBlank()) {
				out.add(part.trim());
			}
		}
		return out;
	}

	private static void setGround(Scene scene, io.github.firestormfmd.scenescripter.core.scene.GroundFilter filter) {
		var h = Edits.SetSceneHeader.of(scene);
		ClientNet.edit(new Edits.SetSceneHeader(h.name(), h.length(), h.origin(), h.bounds(), h.settings().withGroundFilter(filter)));
	}

	private static void setRules(Scene scene, io.github.firestormfmd.scenescripter.core.scene.InteractionRules rules) {
		var h = Edits.SetSceneHeader.of(scene);
		ClientNet.edit(new Edits.SetSceneHeader(h.name(), h.length(), h.origin(), h.bounds(), h.settings().withRules(rules)));
	}

	/** One event: what it is, its condition (click to edit) and a button to remove it. */
	private void eventRow(SceneObject o, io.github.firestormfmd.scenescripter.core.scene.SceneEvent e) {
		if (!visible()) {
			rowY += Ui.ROW_HEIGHT + 1;
			return;
		}
		Ui ui = screen.ui();
		String what = (e.type().equals("mob_event") ? String.valueOf(e.params().get("event")) : e.type()) + " @" + e.tick()
				+ (e.target() != null ? " > " + e.target() : "");
		ui.text(ui.fit(what, w - 90), x + 6, rowY + 2, e.isGenerated() ? 0xFFB06CFF : Ui.TEXT_DIM);
		if (e.isGenerated()) {
			ui.text("auto", x + w - 30, rowY + 2, Ui.TEXT_DIM);
		} else {
			String cond = e.params().get("if") instanceof String c && !c.isBlank() ? c : "";
			int ry = rowY;
			ui.button(x + w - 80, rowY, 58, 11, cond.isEmpty() ? "always" : ui.fit("if " + cond, 54), false,
					() -> screen.editText(x + 6, ry, w - 12, cond.isEmpty() ? "lives <= 0" : cond, v -> {
						java.util.Map<String, Object> params = new java.util.HashMap<>(e.params());
						if (v.isBlank()) {
							params.remove("if");
						} else {
							params.put("if", v.trim());
						}
						ClientNet.edit(new Edits.Composite("Set event condition", List.of(new Edits.RemoveEvent(o.id(), e.id()),
								new Edits.AddEvent(o.id(), new io.github.firestormfmd.scenescripter.core.scene.SceneEvent(e.id(),
										e.tick(), e.type(), e.target(), params, null)))));
					}));
			ui.button(x + w - 20, rowY, 14, 11, "x", false, () -> ClientNet.edit(new Edits.RemoveEvent(o.id(), e.id())));
		}
		rowY += Ui.ROW_HEIGHT + 1;
	}

	/** Custom variables: numbers and switches of the user's own, usable in event conditions. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private void variables(SceneObject o) {
		heading("Variables");
		java.util.Set<String> capabilityNames = new java.util.HashSet<>();
		ClientScene.actor(o.id()).ifPresent(a -> io.github.firestormfmd.scenescripter.actor.Capabilities.of(a)
				.forEach(c -> capabilityNames.add(c.spec().name())));
		for (var entry : o.channels().entrySet()) {
			String name = entry.getKey();
			if (BuiltInChannels.byName(name).isPresent() || capabilityNames.contains(name)) {
				continue;
			}
			io.github.firestormfmd.scenescripter.core.anim.Channel ch = entry.getValue();
			Object value = ch.valueAt(ClientScene.tick());
			int ry = rowY;
			boolean keyed = EditActions.hasKeyNow(o, name);
			if (value instanceof Boolean b) {
				row(name, b ? "true" : "false", keyed, () -> EditActions.keyRaw(o, name, !b, true),
						() -> EditActions.keyRaw(o, name, b, true));
			} else {
				row(name, String.valueOf(value), keyed, () -> editAt(ry, String.valueOf(value), v -> {
					try {
						Object parsed = ch.type() == io.github.firestormfmd.scenescripter.core.anim.ValueType.INT
								? (Object) Integer.parseInt(v.trim()) : (Object) Double.parseDouble(v.trim());
						EditActions.keyRaw(o, name, parsed, ch.type() == io.github.firestormfmd.scenescripter.core.anim.ValueType.INT);
					} catch (NumberFormatException ex) {
						screen.status(name + " is a number");
					}
				}), () -> EditActions.keyRaw(o, name, value, true));
			}
		}
		int addY = rowY;
		row("Add", "name and type...", false, () -> editAt(addY, "lives int 3", v -> addVariable(o, v)), null);
		label("Types: int, float, bool. Use in event conditions.");
	}

	private void addVariable(SceneObject o, String spec) {
		String[] parts = spec.trim().split("\\s+");
		if (parts.length < 2 || !parts[0].matches("[a-z_][a-z0-9_]*") || BuiltInChannels.byName(parts[0]).isPresent()) {
			screen.status("Try \"lives int 3\": a lowercase name, a type and a starting value");
			return;
		}
		try {
			io.github.firestormfmd.scenescripter.core.anim.Channel<?> ch = switch (parts[1]) {
				case "int" -> new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
						io.github.firestormfmd.scenescripter.core.anim.ValueType.INT, parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
				case "float" -> new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
						io.github.firestormfmd.scenescripter.core.anim.ValueType.FLOAT, parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0);
				case "bool" -> new io.github.firestormfmd.scenescripter.core.anim.Channel<>(
						io.github.firestormfmd.scenescripter.core.anim.ValueType.BOOL, parts.length > 2 && Boolean.parseBoolean(parts[2]));
				default -> throw new IllegalArgumentException(parts[1]);
			};
			EditActions.change(o, "Add variable " + parts[0], c -> c.putChannel(parts[0], ch.copy()));
		} catch (RuntimeException ex) {
			screen.status("Types are int, float and bool");
		}
	}

	/** Changes every object in a group in one undoable step. */
	private static void groupEdit(List<SceneObject> members, String label, Consumer<SceneObject> change) {
		List<io.github.firestormfmd.scenescripter.core.edit.EditOp> ops = new java.util.ArrayList<>();
		for (SceneObject m : members) {
			SceneObject copy = m.copy();
			change.accept(copy);
			ops.add(new Edits.ReplaceObject(copy, label));
		}
		ClientNet.edit(new Edits.Composite(label, ops));
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
					} catch (IllegalArgumentException | IllegalStateException e) {
						screen.status("Jump height must be a number, or empty for vanilla");
					}
				}), null);
		int dropY = rowY;
		row("Warn on drops over", p.maxDrop() == null ? "off" : String.format(Locale.ROOT, "%.1f blocks", p.maxDrop()), false,
				() -> editAt(dropY, p.maxDrop() == null ? "4" : String.valueOf(p.maxDrop()), v -> {
					try {
						Double drop = v.isBlank() ? null : Double.parseDouble(v.trim());
						changePath(p, "Change drop warning", c -> c.setMaxDrop(drop));
					} catch (IllegalArgumentException | IllegalStateException e) {
						screen.status("A height in blocks, or empty for no warning");
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

		heading("Speed keys");
		for (var k : p.speedKeys()) {
			row(Math.round(k.at() * 100) + "%", String.format(Locale.ROOT, "%.2f blocks/s  (remove)", k.speed()), false,
					() -> changePath(p, "Remove speed key", c -> {
						List<io.github.firestormfmd.scenescripter.core.scene.SpeedKey> ks = new java.util.ArrayList<>(c.speedKeys());
						ks.remove(k);
						c.setSpeedKeys(ks);
					}), null);
		}
		int speedKeyY = rowY;
		row("Add", "speed at %...", false, () -> editAt(speedKeyY, "50 5.6", v -> {
			String[] parts = v.trim().split("\\s+");
			try {
				var key = new io.github.firestormfmd.scenescripter.core.scene.SpeedKey(
						Math.clamp(Double.parseDouble(parts[0]) / 100.0, 0, 1), Double.parseDouble(parts[1]));
				changePath(p, "Add speed key", c -> {
					List<io.github.firestormfmd.scenescripter.core.scene.SpeedKey> ks = new java.util.ArrayList<>(c.speedKeys());
					ks.add(key);
					c.setSpeedKeys(ks);
				});
			} catch (RuntimeException e) {
				screen.status("Try \"50 5.6\": 5.6 blocks per second halfway along");
			}
		}), null);
		label("Speed changes smoothly between keys.");

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
