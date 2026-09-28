package io.github.firestormfmd.scenescripter.core.scene;

import java.util.Objects;

/**
 * Settings that decide what events do to other objects. Rules are layered: scene defaults, then per-object
 * overrides, then per-event overrides. Null fields inherit from the level above.
 *
 * @param knockback knockback strength multiplier; 0 turns knockback off
 * @param hitCooldown respect the vanilla 10-tick invulnerability window after a hit
 * @param autoDeath generate a death when {@code health} reaches 0 or less
 * @param friendlyFire allow objects in the same group to hurt each other
 */
public record InteractionRules(AttackMode attack, Double knockback, CritMode crits, Boolean hitCooldown,
		Boolean autoDeath, Boolean friendlyFire, ExplosionRules explosions) {
	public static final InteractionRules INHERIT =
			new InteractionRules(null, null, null, null, null, null, ExplosionRules.INHERIT);
	public static final InteractionRules DEFAULTS =
			new InteractionRules(AttackMode.AUTO, 1.0, CritMode.AUTO, true, true, false, ExplosionRules.DEFAULTS);

	public InteractionRules {
		explosions = Objects.requireNonNullElse(explosions, ExplosionRules.INHERIT);
	}

	/** Fills unset fields from {@code parent}. */
	public InteractionRules withFallback(InteractionRules parent) {
		return new InteractionRules(
				attack != null ? attack : parent.attack,
				knockback != null ? knockback : parent.knockback,
				crits != null ? crits : parent.crits,
				hitCooldown != null ? hitCooldown : parent.hitCooldown,
				autoDeath != null ? autoDeath : parent.autoDeath,
				friendlyFire != null ? friendlyFire : parent.friendlyFire,
				explosions.withFallback(parent.explosions));
	}

	public InteractionRules withAttack(AttackMode mode) {
		return new InteractionRules(mode, knockback, crits, hitCooldown, autoDeath, friendlyFire, explosions);
	}
}
