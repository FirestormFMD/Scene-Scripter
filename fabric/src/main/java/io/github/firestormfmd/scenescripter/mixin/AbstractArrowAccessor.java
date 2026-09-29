package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;

/** Lets scene arrows show as stuck in a block, which vanilla only sets from the arrow's own physics. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
	@Invoker("setInGround")
	void scenescripter$setInGround(boolean inGround);
}
