package io.github.firestormfmd.scenescripter.core.scene;

/**
 * What explosions affect. Null fields inherit from the next level up (event, then object, then scene).
 */
public record ExplosionRules(Boolean breakBlocks, Boolean damageObjects, Boolean damageRealEntities,
		Boolean dropItems, Boolean fire) {
	public static final ExplosionRules INHERIT = new ExplosionRules(null, null, null, null, null);
	public static final ExplosionRules DEFAULTS = new ExplosionRules(true, true, false, false, false);

	/** Fills unset fields from {@code parent}. */
	public ExplosionRules withFallback(ExplosionRules parent) {
		return new ExplosionRules(
				breakBlocks != null ? breakBlocks : parent.breakBlocks,
				damageObjects != null ? damageObjects : parent.damageObjects,
				damageRealEntities != null ? damageRealEntities : parent.damageRealEntities,
				dropItems != null ? dropItems : parent.dropItems,
				fire != null ? fire : parent.fire);
	}
}
