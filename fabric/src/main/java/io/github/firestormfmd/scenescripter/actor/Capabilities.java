package io.github.firestormfmd.scenescripter.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.DyeColor;

import io.github.firestormfmd.scenescripter.core.anim.ValueType;
import io.github.firestormfmd.scenescripter.core.scene.ChannelSpec;

/**
 * Mob-specific channels: things only some entity types can do, such as a zombie raising its arms, a sheep's wool
 * or a wolf sitting. The editor offers them for the types that have them, and actors apply them each tick.
 */
public final class Capabilities {
	/**
	 * One mob-specific channel.
	 *
	 * @param label name shown in the inspector
	 * @param type entity class that has it
	 */
	public record Capability(ChannelSpec<?> spec, String label, Class<? extends Entity> type,
			BiConsumer<Entity, Object> apply) {
	}

	/** Dye color names, for the wool color channel. */
	public static final List<String> COLORS;

	private static final List<Capability> ALL = new ArrayList<>();

	static {
		List<String> colors = new ArrayList<>();
		for (DyeColor c : DyeColor.values()) {
			colors.add(c.getName());
		}
		COLORS = List.copyOf(colors);

		add(new ChannelSpec<>("aggressive", ValueType.BOOL, false), "Aggressive", Mob.class,
				(e, v) -> ((Mob) e).setAggressive((Boolean) v));
		add(new ChannelSpec<>("left_handed", ValueType.BOOL, false), "Left-handed", Mob.class,
				(e, v) -> ((Mob) e).setLeftHanded((Boolean) v));
		add(new ChannelSpec<>("sheared", ValueType.BOOL, false), "Sheared", Sheep.class,
				(e, v) -> ((Sheep) e).setSheared((Boolean) v));
		add(new ChannelSpec<>("wool_color", ValueType.ENUM, "white"), "Wool", Sheep.class,
				(e, v) -> ((Sheep) e).setColor(DyeColor.byName((String) v, DyeColor.WHITE)));
		add(new ChannelSpec<>("sitting", ValueType.BOOL, false), "Sitting", TamableAnimal.class,
				(e, v) -> ((TamableAnimal) e).setInSittingPose((Boolean) v));
		add(new ChannelSpec<>("carried_block", ValueType.TEXT, ""), "Carrying", EnderMan.class, (e, v) -> {
			String s = ((String) v).trim();
			try {
				((EnderMan) e).setCarriedBlock(s.isEmpty() ? null : BlockStateParser.parseForBlock(
						e.level().registryAccess().lookupOrThrow(Registries.BLOCK), s, false).blockState());
			} catch (CommandSyntaxException ignored) {
				// leave the block as it is until the value can be read
			}
		});
	}

	private Capabilities() {
	}

	private static void add(ChannelSpec<?> spec, String label, Class<? extends Entity> type, BiConsumer<Entity, Object> apply) {
		ALL.add(new Capability(spec, label, type, apply));
	}

	/** The mob-specific channels an entity has. */
	public static List<Capability> of(Entity entity) {
		List<Capability> out = new ArrayList<>();
		for (Capability c : ALL) {
			if (c.type().isInstance(entity)) {
				out.add(c);
			}
		}
		return out;
	}

	/** Applies whichever of the entity's mob-specific channels the object has keyed. */
	public static void apply(Entity entity, java.util.Map<String, Object> values) {
		for (Capability c : of(entity)) {
			Object v = values.get(c.spec().name());
			if (v != null) {
				try {
					c.apply().accept(entity, v);
				} catch (ClassCastException ignored) {
					// a custom variable of another type with the same name; not ours to apply
				}
			}
		}
	}
}
