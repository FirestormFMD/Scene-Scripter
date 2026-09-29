package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Lets an actor drop its vanilla death loot when the scene kills it with drops turned on. */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {
	@Invoker("dropAllDeathLoot")
	void scenescripter$dropAllDeathLoot(ServerLevel level, DamageSource source);
}
