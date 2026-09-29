package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.item.DyeColor;

/** Lets a wolf actor wear any collar color, which vanilla only changes when a player dyes it. */
@Mixin(Wolf.class)
public interface WolfAccessor {
	@Invoker("setCollarColor")
	void scenescripter$setCollarColor(DyeColor color);
}
