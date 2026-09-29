package io.github.firestormfmd.scenescripter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import io.github.firestormfmd.scenescripter.actor.Actors;

/**
 * Actors never tick: no AI, physics, despawning, aging, burning, drowning or conversions. The scene sets their
 * state every tick instead.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
	@WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
	private void scenescripter$skipActorTick(Entity entity, Operation<Void> original) {
		if (!Actors.isActor(entity)) {
			original.call(entity);
		}
	}

	@WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
	private void scenescripter$skipActorRideTick(Entity entity, Operation<Void> original) {
		if (!Actors.isActor(entity)) {
			original.call(entity);
		} else if (entity.getVehicle() != null) {
			entity.getVehicle().positionRider(entity);
		}
	}
}
