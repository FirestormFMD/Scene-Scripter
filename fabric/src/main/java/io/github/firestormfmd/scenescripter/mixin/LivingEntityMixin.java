package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.firestormfmd.scenescripter.actor.Actors;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Real mobs never see actors as enemies. */
	@Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
	private void scenescripter$notAnEnemy(CallbackInfoReturnable<Boolean> cir) {
		if (Actors.isActor((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}

	/** Potions and effects from outside the scene do nothing to actors. */
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At("HEAD"), cancellable = true)
	private void scenescripter$noEffects(MobEffectInstance effect, @Nullable Entity source,
			CallbackInfoReturnable<Boolean> cir) {
		if (Actors.isActor((Entity) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
