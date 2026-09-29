package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.firestormfmd.scenescripter.actor.Actors;

/** Projectiles that are not part of the scene pass through actors. */
@Mixin({Projectile.class, AbstractArrow.class, ShulkerBullet.class, FishingHook.class, AbstractHurtingProjectile.class,
		AbstractWindCharge.class})
public abstract class ProjectileHitMixin {
	@Inject(method = "canHitEntity", at = @At("HEAD"), cancellable = true)
	private void scenescripter$passThroughActors(Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (Actors.isActor(target)) {
			cir.setReturnValue(false);
		}
	}
}
