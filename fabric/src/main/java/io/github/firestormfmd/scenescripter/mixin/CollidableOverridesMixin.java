package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.firestormfmd.scenescripter.actor.Actors;

/** Classes that override {@code canBeCollidedWith}: players and mobs walk through actors. */
@Mixin({HappyGhast.class, AbstractBoat.class, Shulker.class})
public abstract class CollidableOverridesMixin {
	@Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
	private void scenescripter$actorsAreNotSolid(@Nullable Entity other, CallbackInfoReturnable<Boolean> cir) {
		if (Actors.isActor((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
