package io.github.firestormfmd.scenescripter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import io.github.firestormfmd.scenescripter.actor.Actors;

/**
 * Actors send their position every tick, so recordings show smooth motion, and are tracked from further away, so
 * distant fighters still reach the client and the recorder.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
	@WrapOperation(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;updateInterval()I"))
	private int scenescripter$actorUpdateEveryTick(EntityType<?> type, Operation<Integer> original,
			@Local(argsOnly = true) Entity entity) {
		return Actors.isActor(entity) ? 1 : original.call(type);
	}

	@WrapOperation(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;clientTrackingRange()I"))
	private int scenescripter$actorTrackingRange(EntityType<?> type, Operation<Integer> original,
			@Local(argsOnly = true) Entity entity) {
		// The caller multiplies by 16 to get blocks.
		return Actors.isActor(entity) ? Math.max(original.call(type), Actors.trackingRange() / 16) : original.call(type);
	}
}
