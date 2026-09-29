package io.github.firestormfmd.scenescripter.core.scene;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.anim.ValueType;
import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * Channels every object understands. Mob-specific channels come from capability descriptors on the Minecraft
 * side; anything else is a user-defined custom variable.
 */
public final class BuiltInChannels {
	/** Keyframed position, used when the object is not on a motion clip. */
	public static final ChannelSpec<Vec3> POSITION = new ChannelSpec<>("position", ValueType.VEC3, Vec3.ZERO);
	/** Added to the position from paths and keys. The solver writes knockback here; users can nudge objects with it. */
	public static final ChannelSpec<Vec3> OFFSET = new ChannelSpec<>("offset", ValueType.VEC3, Vec3.ZERO);
	public static final ChannelSpec<Double> BODY_YAW = new ChannelSpec<>("body_yaw", ValueType.FLOAT, 0.0);
	public static final ChannelSpec<Double> HEAD_YAW = new ChannelSpec<>("head_yaw", ValueType.FLOAT, 0.0);
	public static final ChannelSpec<Double> HEAD_PITCH = new ChannelSpec<>("head_pitch", ValueType.FLOAT, 0.0);
	/** Object ID or "x y z" the head tracks; empty for none. */
	public static final ChannelSpec<String> LOOK_AT = new ChannelSpec<>("look_at", ValueType.TEXT, "");
	/** Not real health: a number that auto damage subtracts from and that can trigger death at 0. */
	public static final ChannelSpec<Double> HEALTH = new ChannelSpec<>("health", ValueType.FLOAT, 20.0);
	public static final ChannelSpec<Boolean> DEAD = new ChannelSpec<>("dead", ValueType.BOOL, false);
	public static final ChannelSpec<String> POSE = new ChannelSpec<>("pose", ValueType.ENUM, "standing");
	public static final ChannelSpec<Boolean> SNEAKING = new ChannelSpec<>("sneaking", ValueType.BOOL, false);
	public static final ChannelSpec<Boolean> SPRINTING = new ChannelSpec<>("sprinting", ValueType.BOOL, false);
	public static final ChannelSpec<Boolean> ON_FIRE = new ChannelSpec<>("on_fire", ValueType.BOOL, false);
	public static final ChannelSpec<Boolean> GLOWING = new ChannelSpec<>("glowing", ValueType.BOOL, false);
	public static final ChannelSpec<Boolean> INVISIBLE = new ChannelSpec<>("invisible", ValueType.BOOL, false);
	/** The vanilla {@code scale} attribute. */
	public static final ChannelSpec<Double> SCALE = new ChannelSpec<>("scale", ValueType.FLOAT, 1.0);
	public static final ChannelSpec<String> CUSTOM_NAME = new ChannelSpec<>("custom_name", ValueType.TEXT, "");
	public static final ChannelSpec<Boolean> NAME_VISIBLE = new ChannelSpec<>("name_visible", ValueType.BOOL, false);
	public static final ChannelSpec<String> MAINHAND = new ChannelSpec<>("equipment.mainhand", ValueType.ITEM, "");
	public static final ChannelSpec<String> OFFHAND = new ChannelSpec<>("equipment.offhand", ValueType.ITEM, "");
	public static final ChannelSpec<String> HEAD = new ChannelSpec<>("equipment.head", ValueType.ITEM, "");
	public static final ChannelSpec<String> CHEST = new ChannelSpec<>("equipment.chest", ValueType.ITEM, "");
	public static final ChannelSpec<String> LEGS = new ChannelSpec<>("equipment.legs", ValueType.ITEM, "");
	public static final ChannelSpec<String> FEET = new ChannelSpec<>("equipment.feet", ValueType.ITEM, "");
	public static final ChannelSpec<Boolean> AMBIENT_SOUNDS = new ChannelSpec<>("ambient_sounds", ValueType.BOOL, true);
	public static final ChannelSpec<Boolean> SILENT = new ChannelSpec<>("silent", ValueType.BOOL, false);
	/**
	 * Takes the object out of the scene without a death, such as a TNT that has exploded or an arrow that hit
	 * something. The solver keys it for explosions and projectiles.
	 */
	public static final ChannelSpec<Boolean> REMOVED = new ChannelSpec<>("removed", ValueType.BOOL, false);
	/** ID of the object this one rides, or empty. Keyed by mount and dismount. */
	public static final ChannelSpec<String> VEHICLE = new ChannelSpec<>("vehicle", ValueType.TEXT, "");
	/** Creeper swelling towards an explosion; keyed by ignite events. */
	public static final ChannelSpec<Boolean> IGNITED = new ChannelSpec<>("ignited", ValueType.BOOL, false);

	/** Item use in progress (bow draw, eating, shield): {@code main}, {@code off}, or empty for none. */
	public static final ChannelSpec<String> USE_ITEM = new ChannelSpec<>("use_item", ValueType.TEXT, "");

	// ---- Scene track channels ----

	/** Time of day in ticks, 0 to 24000 (6000 is noon). Only applied once keyed. */
	public static final ChannelSpec<Integer> TIME_OF_DAY = new ChannelSpec<>("time_of_day", ValueType.INT, 6000);
	/** {@code clear}, {@code rain} or {@code thunder}. Only applied once keyed. */
	public static final ChannelSpec<String> WEATHER = new ChannelSpec<>("weather", ValueType.ENUM, "clear");

	private static final Map<String, ChannelSpec<?>> BY_NAME = new LinkedHashMap<>();

	static {
		for (ChannelSpec<?> spec : new ChannelSpec<?>[] {POSITION, OFFSET, BODY_YAW, HEAD_YAW, HEAD_PITCH, LOOK_AT, HEALTH, DEAD,
				POSE, SNEAKING, SPRINTING, ON_FIRE, GLOWING, INVISIBLE, SCALE, CUSTOM_NAME, NAME_VISIBLE, MAINHAND,
				OFFHAND, HEAD, CHEST, LEGS, FEET, AMBIENT_SOUNDS, SILENT, REMOVED, VEHICLE, IGNITED, USE_ITEM, TIME_OF_DAY, WEATHER}) {
			BY_NAME.put(spec.name(), spec);
		}
	}

	private BuiltInChannels() {
	}

	public static Optional<ChannelSpec<?>> byName(String name) {
		return Optional.ofNullable(BY_NAME.get(name));
	}

	public static Iterable<ChannelSpec<?>> all() {
		return BY_NAME.values();
	}
}
