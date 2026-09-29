package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.creaking.Creaking;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.firestormfmd.scenescripter.actor.Actors;

/** Classes that override {@code isPushable()}: nothing pushes actors. */
@Mixin({LivingEntity.class, AbstractHorse.class, Parrot.class, AbstractMinecart.class, AbstractBoat.class, Bat.class,
		ArmorStand.class, Warden.class, Creaking.class})
public abstract class PushableOverridesMixin {
	@Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
	private void scenescripter$actorsAreNotPushable(CallbackInfoReturnable<Boolean> cir) {
		if (Actors.isActor((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
