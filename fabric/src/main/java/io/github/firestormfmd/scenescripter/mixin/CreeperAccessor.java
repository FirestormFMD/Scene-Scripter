package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.monster.Creeper;

/** Lets the editor show a creeper's swell at the playhead when scrubbing into the middle of it. */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("swell")
	void scenescripter$setSwell(int swell);

	@Accessor("oldSwell")
	void scenescripter$setOldSwell(int oldSwell);
}
