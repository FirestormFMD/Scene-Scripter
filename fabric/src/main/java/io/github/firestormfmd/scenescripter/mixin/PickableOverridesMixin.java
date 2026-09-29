package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.firestormfmd.scenescripter.actor.Actors;

/** Classes that override {@code isPickable()}: actors are never picked by the crosshair or by attacks. */
@Mixin({LivingEntity.class, AbstractMinecart.class, AbstractBoat.class, EndCrystal.class, FallingBlockEntity.class,
		PrimedTnt.class, AbstractArrow.class, Projectile.class, ShulkerBullet.class, ArmorStand.class, Interaction.class})
public abstract class PickableOverridesMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void scenescripter$actorsAreNotPickable(CallbackInfoReturnable<Boolean> cir) {
		if (Actors.isActor((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
