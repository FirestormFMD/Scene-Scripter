package io.github.firestormfmd.scenescripter.server;

import java.util.Map;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import io.github.firestormfmd.scenescripter.core.runtime.ObjectState;

/**
 * Copies an object's evaluated state onto its actor entity. Everything goes through ordinary entity setters, so
 * clients and recorders receive plain vanilla packets.
 */
public final class ActorApplier {
	private static final Map<String, EquipmentSlot> SLOTS = Map.of(
			"mainhand", EquipmentSlot.MAINHAND,
			"offhand", EquipmentSlot.OFFHAND,
			"head", EquipmentSlot.HEAD,
			"chest", EquipmentSlot.CHEST,
			"legs", EquipmentSlot.LEGS,
			"feet", EquipmentSlot.FEET);

	private ActorApplier() {
	}

	/**
	 * @param jump true when the playhead jumped (seek, loop, edit), so clients should snap rather than glide
	 */
	public static void apply(ServerLevel level, Entity entity, ObjectState state, boolean jump) {
		double x = state.position().x();
		double y = state.position().y();
		double z = state.position().z();
		entity.snapTo(x, y, z, state.bodyYaw(), state.headPitch());
		entity.setYHeadRot(state.headYaw());
		entity.setOnGround(state.onGround());
		if (entity instanceof LivingEntity living) {
			living.setYBodyRot(state.bodyYaw());
		}
		if (jump) {
			ClientboundTeleportEntityPacket packet = ClientboundTeleportEntityPacket.teleport(entity.getId(),
					PositionMoveRotation.of(entity), Set.of(), state.onGround());
			level.getChunkSource().sendToTrackingPlayers(entity, packet);
		}

		entity.setPose(pose(state));
		entity.setShiftKeyDown(state.sneaking());
		entity.setSprinting(state.sprinting());
		entity.setSwimming(state.swimming());
		entity.setSharedFlagOnFire(state.onFire());
		entity.setGlowingTag(state.glowing());
		entity.setInvisible(state.invisible());
		entity.setSilent(state.silent());

		Component name = state.customName().isEmpty() ? null : Component.literal(state.customName());
		if (!java.util.Objects.equals(name == null ? null : name.getString(),
				entity.getCustomName() == null ? null : entity.getCustomName().getString())) {
			entity.setCustomName(name);
		}
		entity.setCustomNameVisible(state.nameVisible() && name != null);

		if (entity instanceof LivingEntity living) {
			applyLiving(living, state);
		}
	}

	private static void applyLiving(LivingEntity living, ObjectState state) {
		for (Map.Entry<String, EquipmentSlot> slot : SLOTS.entrySet()) {
			String wanted = state.equipment().getOrDefault(slot.getKey(), "");
			ItemStack current = living.getItemBySlot(slot.getValue());
			String currentId = current.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(current.getItem()).toString();
			if (!wanted.equals(currentId)) {
				living.setItemSlot(slot.getValue(), stack(wanted));
			}
		}

		float health = state.dead() ? 0f : living.getMaxHealth();
		if (living.getHealth() != health) {
			living.setHealth(health);
		}

		AttributeInstance scale = living.getAttribute(Attributes.SCALE);
		if (scale != null && scale.getBaseValue() != state.scale()) {
			scale.setBaseValue(state.scale());
		}
	}

	/** An item stack from an item ID such as {@code minecraft:iron_sword}; empty if unknown. */
	static ItemStack stack(String itemId) {
		if (itemId.isEmpty()) {
			return ItemStack.EMPTY;
		}
		Identifier id = Identifier.tryParse(itemId.contains("[") ? itemId.substring(0, itemId.indexOf('[')) : itemId);
		if (id == null) {
			return ItemStack.EMPTY;
		}
		return BuiltInRegistries.ITEM.get(id).map(ref -> new ItemStack((Item) ref.value())).orElse(ItemStack.EMPTY);
	}

	private static Pose pose(ObjectState state) {
		if (state.dead()) {
			return Pose.DYING;
		}
		if (state.swimming()) {
			return Pose.SWIMMING;
		}
		if (state.sneaking()) {
			return Pose.CROUCHING;
		}
		for (Pose p : Pose.values()) {
			if (p.getSerializedName().equals(state.pose())) {
				return p;
			}
		}
		return Pose.STANDING;
	}
}
