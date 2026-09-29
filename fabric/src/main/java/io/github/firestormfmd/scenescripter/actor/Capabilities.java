package io.github.firestormfmd.scenescripter.actor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.DyeColor;

import io.github.firestormfmd.scenescripter.compat.Compat;
import io.github.firestormfmd.scenescripter.core.anim.ValueType;
import io.github.firestormfmd.scenescripter.core.scene.ChannelSpec;
import io.github.firestormfmd.scenescripter.mixin.EnderManAccessor;
import io.github.firestormfmd.scenescripter.mixin.WolfAccessor;

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

	/** Dye color names, for the wool and collar color channels. */
	public static final List<String> COLORS;
	/** Villager professions and biome types, as their registry IDs without the namespace. */
	public static final List<String> PROFESSIONS = List.of("none", "armorer", "butcher", "cartographer", "cleric", "farmer",
			"fisherman", "fletcher", "leatherworker", "librarian", "mason", "nitwit", "shepherd", "toolsmith", "weaponsmith");
	public static final List<String> VILLAGER_TYPES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");

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
		add(new ChannelSpec<>("tame", ValueType.BOOL, false), "Tame", TamableAnimal.class,
				(e, v) -> ((TamableAnimal) e).setTame((Boolean) v, false));
		add(new ChannelSpec<>("angry", ValueType.BOOL, false), "Angry", Wolf.class,
				(e, v) -> ((Wolf) e).setPersistentAngerEndTime((Boolean) v ? Long.MAX_VALUE : 0L));
		add(new ChannelSpec<>("collar_color", ValueType.ENUM, "red"), "Collar (when tame)", Wolf.class,
				(e, v) -> ((WolfAccessor) e).scenescripter$setCollarColor(DyeColor.byName((String) v, DyeColor.RED)));
		add(new ChannelSpec<>("rearing", ValueType.BOOL, false), "Rearing", AbstractHorse.class, (e, v) -> {
			AbstractHorse horse = (AbstractHorse) e;
			if ((Boolean) v) {
				horse.setStanding(20);
			} else {
				horse.clearStanding();
			}
		});
		add(new ChannelSpec<>("eating", ValueType.BOOL, false), "Grazing", AbstractHorse.class,
				(e, v) -> ((AbstractHorse) e).setEating((Boolean) v));
		add(new ChannelSpec<>("screaming", ValueType.BOOL, false), "Screaming", Compat.ENDERMAN,
				(e, v) -> e.getEntityData().set(EnderManAccessor.scenescripter$creepyData(), (Boolean) v));
		add(new ChannelSpec<>("profession", ValueType.ENUM, "none"), "Profession", Villager.class, (e, v) -> {
			Villager villager = (Villager) e;
			villager.setVillagerData(villager.getVillagerData().withProfession(e.level().registryAccess(),
					ResourceKey.create(Registries.VILLAGER_PROFESSION, Identifier.fromNamespaceAndPath("minecraft", (String) v))));
		});
		add(new ChannelSpec<>("villager_type", ValueType.ENUM, "plains"), "Biome", Villager.class, (e, v) -> {
			Villager villager = (Villager) e;
			villager.setVillagerData(villager.getVillagerData().withType(e.level().registryAccess(),
					ResourceKey.create(Registries.VILLAGER_TYPE, Identifier.fromNamespaceAndPath("minecraft", (String) v))));
		});
		add(new ChannelSpec<>("head_shake", ValueType.BOOL, false), "Shaking head", AbstractVillager.class,
				(e, v) -> ((AbstractVillager) e).setUnhappyCounter((Boolean) v ? 40 : 0));
		add(new ChannelSpec<>("carried_block", ValueType.TEXT, ""), "Carrying", Compat.ENDERMAN, (e, v) -> {
			String s = ((String) v).trim();
			try {
				Compat.setCarriedBlock(e, s.isEmpty() ? null : BlockStateParser.parseForBlock(
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

	/** The values an enum channel cycles through in the inspector, or an empty list for free text. */
	public static List<String> options(String channel) {
		return switch (channel) {
			case "wool_color", "collar_color" -> COLORS;
			case "profession" -> PROFESSIONS;
			case "villager_type" -> VILLAGER_TYPES;
			default -> List.of();
		};
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
				} catch (RuntimeException ignored) {
					// a value the game doesn't know, such as a misspelt profession; leave the actor as it is
				}
			}
		}
	}
}
