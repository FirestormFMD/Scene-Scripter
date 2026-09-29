package io.github.firestormfmd.scenescripter.core.anim;

import java.util.List;
import java.util.function.Function;

import io.github.firestormfmd.scenescripter.core.math.Vec3;

/**
 * The type of value a channel holds. Numeric types interpolate component by component; the rest only step.
 *
 * @param id stable name used in scene files
 * @param components number of numeric components, or 0 for types that can only step
 */
public record ValueType<T>(String id, Class<T> javaType, int components,
		Function<T, double[]> toComponents, Function<double[], T> fromComponents) {
	public static final ValueType<Double> FLOAT = numeric("float", Double.class, 1,
			v -> new double[] {v}, c -> c[0]);
	/** Integers interpolate as real numbers and round to the nearest whole value. */
	public static final ValueType<Integer> INT = numeric("int", Integer.class, 1,
			v -> new double[] {v}, c -> (int) Math.round(c[0]));
	public static final ValueType<Vec3> VEC3 = numeric("vec3", Vec3.class, 3,
			v -> new double[] {v.x(), v.y(), v.z()}, c -> new Vec3(c[0], c[1], c[2]));
	public static final ValueType<Boolean> BOOL = stepped("bool", Boolean.class);
	/** One of a fixed set of names, such as a pose. */
	public static final ValueType<String> ENUM = stepped("enum", String.class);
	public static final ValueType<String> TEXT = stepped("text", String.class);
	/** An item stack, stored as an item ID with optional components in SNBT, such as {@code minecraft:iron_sword}. */
	public static final ValueType<String> ITEM = stepped("item", String.class);

	public static final List<ValueType<?>> ALL = List.of(FLOAT, INT, VEC3, BOOL, ENUM, TEXT, ITEM);

	private static <T> ValueType<T> numeric(String id, Class<T> type, int components,
			Function<T, double[]> to, Function<double[], T> from) {
		return new ValueType<>(id, type, components, to, from);
	}

	private static <T> ValueType<T> stepped(String id, Class<T> type) {
		return new ValueType<>(id, type, 0, null, null);
	}

	public static ValueType<?> byId(String id) {
		for (ValueType<?> type : ALL) {
			if (type.id.equals(id)) {
				return type;
			}
		}
		throw new IllegalArgumentException("Unknown value type: " + id);
	}

	public boolean interpolates() {
		return components > 0;
	}

	/** Casts {@code value} to this type, failing with a clear message if it does not match. */
	public T cast(Object value) {
		if (!javaType.isInstance(value)) {
			throw new IllegalArgumentException("Expected " + id + " value but got " + value);
		}
		return javaType.cast(value);
	}

	@Override
	public String toString() {
		return id;
	}
}
