package io.github.firestormfmd.scenescripter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import io.github.firestormfmd.scenescripter.actor.Actors;

/** Lightning does not set actors on fire or convert them (pigs, villagers, creepers). */
@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;thunderHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LightningBolt;)V"))
	private void scenescripter$spareActors(Entity entity, ServerLevel level, LightningBolt bolt, Operation<Void> original) {
		if (!Actors.isActor(entity)) {
			original.call(entity, level, bolt);
		}
	}
}
