package io.github.firestormfmd.scenescripter.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Lets the editor show a creeper's swell at the playhead when scrubbing into the middle of it, and lets actors be
 * charged creepers without a lightning strike.
 */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("swell")
	void scenescripter$setSwell(int swell);

	@Accessor("oldSwell")
	void scenescripter$setOldSwell(int oldSwell);

	@Accessor("DATA_IS_POWERED")
	static EntityDataAccessor<Boolean> scenescripter$poweredData() {
		throw new AssertionError();
	}
}
