package io.github.firestormfmd.scenescripter.mixin;

import net.minecraft.world.entity.Entity;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.firestormfmd.scenescripter.actor.ActorAccess;
import io.github.firestormfmd.scenescripter.actor.Actors;

/**
 * Stores the actor mark and keeps actors out of everything the scene did not set up.
 */
@Mixin(Entity.class)
public abstract class EntityMixin implements ActorAccess {
	@Unique
	private @Nullable String scenescripter$objectId;

	@Override
	public @Nullable String scenescripter$objectId() {
		return scenescripter$objectId;
	}

	@Override
	public void scenescripter$setObjectId(@Nullable String objectId) {
		scenescripter$objectId = objectId;
	}

	@Unique
	private boolean scenescripter$sceneDrop;

	@Override
	public boolean scenescripter$isSceneDrop() {
		return scenescripter$sceneDrop;
	}

	@Override
	public void scenescripter$setSceneDrop(boolean sceneDrop) {
		scenescripter$sceneDrop = sceneDrop;
	}

	@Unique
	private boolean scenescripter$isActor() {
		return scenescripter$objectId != null;
	}

	/** Actors are recreated from the scene on load, so they must never end up in the world save. */
	@Inject(method = "shouldBeSaved", at = @At("HEAD"), cancellable = true)
	private void scenescripter$neverSaveActors(CallbackInfoReturnable<Boolean> cir) {
		if (scenescripter$isActor() || scenescripter$sceneDrop) {
			cir.setReturnValue(false);
		}
	}

	/** Pressure plates, tripwires and sculk sensors ignore actors. */
	@Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
	private void scenescripter$actorsTriggerNothing(CallbackInfoReturnable<Boolean> cir) {
		if (scenescripter$isActor()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
	private void scenescripter$noPushingActors(Entity other, CallbackInfo ci) {
		if (scenescripter$isActor() || Actors.isActor(other)) {
			ci.cancel();
		}
	}

	@Inject(method = {"isPickable", "isPushable", "isAttackable"}, at = @At("HEAD"), cancellable = true)
	private void scenescripter$actorsAreNotTargets(CallbackInfoReturnable<Boolean> cir) {
		if (scenescripter$isActor()) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "canBeCollidedWith", at = @At("HEAD"), cancellable = true)
	private void scenescripter$actorsHaveNoCollision(@Nullable Entity other, CallbackInfoReturnable<Boolean> cir) {
		if (scenescripter$isActor()) {
			cir.setReturnValue(false);
		}
	}

	/** Only the scene may mount actors or mount something onto them. */
	@Inject(method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z", at = @At("HEAD"), cancellable = true)
	private void scenescripter$onlySceneMountsActors(Entity vehicle, boolean force, boolean sendEventAndTriggers,
			CallbackInfoReturnable<Boolean> cir) {
		if ((scenescripter$isActor() || Actors.isActor(vehicle)) && !Actors.inSceneAction()) {
			cir.setReturnValue(false);
		}
	}
}
