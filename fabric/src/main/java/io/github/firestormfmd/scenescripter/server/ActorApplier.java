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
			"feet", EquipmentSlot.FEET,
			"body", EquipmentSlot.BODY,
			"saddle", EquipmentSlot.SADDLE);

	private ActorApplier() {
	}

	/**
	 * @param jump true when the playhead jumped (seek, loop, edit), so clients should snap rather than glide
	 * @param settled whether clients have had the actor for long enough to know its equipment (see below)
	 * @return true if an item use was held back and needs another apply on a later tick
	 */
	public static boolean apply(ServerLevel level, Entity entity, ObjectState state, boolean jump, boolean settled) {
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

		boolean deferred = false;
		if (entity instanceof LivingEntity living) {
			deferred = applyLiving(level, living, state, settled);
		}
		io.github.firestormfmd.scenescripter.actor.Capabilities.apply(entity, state.extra());
		return deferred;
	}

	private static boolean applyLiving(ServerLevel level, LivingEntity living, ObjectState state, boolean settled) {
		String use = state.useItem();
		EquipmentSlot usedSlot = use.equals("off") ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
		boolean usedHandChanged = false;
		java.util.List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> changed = new java.util.ArrayList<>();
		for (Map.Entry<String, EquipmentSlot> slot : SLOTS.entrySet()) {
			String wanted = state.equipment().getOrDefault(slot.getKey(), "");
			ItemStack current = living.getItemBySlot(slot.getValue());
			String currentId = current.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(current.getItem()).toString();
			if (!wanted.equals(currentId)) {
				ItemStack stack = stack(wanted);
				living.setItemSlot(slot.getValue(), stack);
				changed.add(com.mojang.datafixers.util.Pair.of(slot.getValue(), stack.copy()));
				usedHandChanged |= slot.getValue() == usedSlot;
			}
		}
		// Vanilla sends equipment changes while an entity ticks, which actors never do, so they are sent here.
		if (!changed.isEmpty()) {
			level.getChunkSource().sendToTrackingPlayers(living,
					new net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket(living.getId(), changed));
		}

		float health = state.dead() ? 0f : living.getMaxHealth();
		if (living.getHealth() != health) {
			living.setHealth(health);
		}

		// Bow draws, eating and raised shields, from the use_item channel. A client picks up the used item when the
		// use starts, so the use waits until the client has the item: until a new actor has been sent with its
		// equipment, and for a tick after the item in that hand changes (stopping and starting again).
		boolean deferred = false;
		if (!use.isEmpty() && !state.dead()) {
			net.minecraft.world.InteractionHand hand = use.equals("off")
					? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND;
			if (!settled || usedHandChanged) {
				if (living.isUsingItem()) {
					living.stopUsingItem();
				}
				deferred = true;
			} else if (!living.isUsingItem() || living.getUsedItemHand() != hand) {
				living.startUsingItem(hand);
			}
		} else if (living.isUsingItem()) {
			living.stopUsingItem();
		}

		AttributeInstance scale = living.getAttribute(Attributes.SCALE);
		if (scale != null && scale.getBaseValue() != state.scale()) {
			scale.setBaseValue(state.scale());
		}
		return deferred;
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
