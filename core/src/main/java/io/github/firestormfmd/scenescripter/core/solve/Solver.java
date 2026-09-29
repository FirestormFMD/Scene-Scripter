package io.github.firestormfmd.scenescripter.core.solve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.ObjectState;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.AttackMode;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.CritMode;
import io.github.firestormfmd.scenescripter.core.scene.InteractionRules;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Turns events into their consequences under the interaction rules: hit tests, damage, hurt flashes, knockback and
 * deaths. Results are written into the scene as generated keyframes and events, marked with the event that caused
 * them, so they show up on the timeline and play back like anything else.
 *
 * <p>Solving is a pure function of the user's content: all generated content is removed first and rebuilt in tick
 * order, so a knockback from one hit is in place before a later hit is tested. Keyframes the user set are never
 * overwritten; editing a generated keyframe detaches it and the solver leaves that tick alone from then on.
 */
public final class Solver {
	/** What happened to one attack. */
	public record AttackResult(String eventId, String attackerId, String targetId, int tick, boolean hit, String reason,
			double damage) {
	}

	private final CombatModel combat;
	private final Map<String, Integer> lastHitTick = new HashMap<>();
	private final Map<String, Double> lastHitDamage = new HashMap<>();

	public Solver(CombatModel combat) {
		this.combat = combat;
	}

	public List<AttackResult> solve(Scene scene, SceneEvaluator evaluator) {
		lastHitTick.clear();
		lastHitDamage.clear();
		clearGenerated(scene);

		record Pending(SceneObject owner, SceneEvent event) {
		}
		List<Pending> events = new ArrayList<>();
		for (SceneObject o : scene.objects()) {
			for (SceneEvent e : o.events()) {
				if (e.type().equals("attack") || e.type().equals("hurt")) {
					events.add(new Pending(o, e));
				}
			}
		}
		events.sort(Comparator.comparingInt(p -> p.event().tick()));

		List<AttackResult> results = new ArrayList<>();
		for (Pending p : events) {
			InteractionRules rules = rulesFor(scene, p.owner(), p.event());
			if (p.event().type().equals("attack")) {
				results.add(attack(scene, evaluator, p.owner(), p.event(), rules));
			} else {
				manualHurt(scene, evaluator, p.owner(), p.event(), rules);
			}
		}
		return results;
	}

	/** Removes everything a previous solve generated. */
	public static void clearGenerated(Scene scene) {
		for (SceneObject o : scene.objects()) {
			for (Channel<?> ch : o.channels().values()) {
				ch.removeIf(Keyframe::isGenerated);
			}
			for (SceneEvent e : List.copyOf(o.events())) {
				if (e.isGenerated()) {
					o.removeEvent(e.id());
				}
			}
		}
	}

	/** Scene rules, overridden by the object's, overridden by the event's own parameters. */
	static InteractionRules rulesFor(Scene scene, SceneObject owner, SceneEvent event) {
		InteractionRules fromEvent = InteractionRules.INHERIT;
		Object mode = event.params().get("mode");
		if (mode instanceof String s) {
			fromEvent = fromEvent.withAttack(AttackMode.byId(s));
		}
		InteractionRules r = new InteractionRules(fromEvent.attack(),
				event.params().get("knockback") instanceof Number n ? n.doubleValue() : null,
				event.params().get("crit") instanceof String c ? CritMode.byId(c) : null,
				null, null, null, null);
		return r.withFallback(owner.rules()).withFallback(scene.settings().rules()).withFallback(InteractionRules.DEFAULTS);
	}

	private AttackResult attack(Scene scene, SceneEvaluator eval, SceneObject attacker, SceneEvent event,
			InteractionRules rules) {
		int tick = event.tick();
		if (rules.attack() == AttackMode.ANIMATION_ONLY) {
			return new AttackResult(event.id(), attacker.id(), event.target(), tick, false, "Animation only", 0);
		}
		ObjectState a = eval.evaluate(attacker, tick);
		if (!a.exists() || a.dead()) {
			return miss(event, attacker, event.target(), "Attacker is not there");
		}
		SceneObject target = event.target() == null
				? nearestInFront(scene, eval, attacker, a, tick)
				: scene.object(event.target()).orElse(null);
		if (target == null) {
			return miss(event, attacker, null, "No target");
		}
		ObjectState t = eval.evaluate(target, tick);
		if (!t.exists() || t.dead()) {
			return miss(event, attacker, target.id(), "Target is not there");
		}
		if (!rules.friendlyFire() && attacker.group() != null && attacker.group().equals(target.group())) {
			return miss(event, attacker, target.id(), "Same group");
		}
		if (rules.attack() == AttackMode.AUTO) {
			Optional<String> why = hitTest(attacker, a, target, t);
			if (why.isPresent()) {
				return miss(event, attacker, target.id(), why.get());
			}
		}

		double damage;
		if (event.params().get("damage") instanceof Number n) {
			damage = n.doubleValue();
		} else {
			damage = combat.baseAttackDamage(attacker) + VanillaCombat.weaponBonus(a.equipment().getOrDefault("mainhand", ""));
			boolean crit = switch (rules.crits()) {
				case ALWAYS -> true;
				case NEVER -> false;
				case AUTO -> !a.onGround() && !a.sprinting() && !a.swimming();
			};
			if (crit) {
				damage *= VanillaCombat.CRIT_MULTIPLIER;
			}
			double[] armor = VanillaCombat.armor(List.of(t.equipment().getOrDefault("head", ""),
					t.equipment().getOrDefault("chest", ""), t.equipment().getOrDefault("legs", ""),
					t.equipment().getOrDefault("feet", "")));
			damage = VanillaCombat.afterArmor(damage, armor[0], armor[1]);
		}

		if (rules.hitCooldown()) {
			Integer last = lastHitTick.get(target.id());
			if (last != null && tick - last < VanillaCombat.HIT_COOLDOWN) {
				double previous = lastHitDamage.getOrDefault(target.id(), 0.0);
				if (damage <= previous) {
					return miss(event, attacker, target.id(), "Target is still recovering from the last hit");
				}
				damage -= previous;
			}
		}

		Vec3 push = t.position().subtract(a.position());
		double strength = VanillaCombat.BASE_KNOCKBACK + (a.sprinting() ? 0.5 : 0);
		applyHit(scene, eval, target, t, tick, damage, push, strength * rules.knockback(), rules, event.id(), attacker.id());
		return new AttackResult(event.id(), attacker.id(), target.id(), tick, true, "Hit", damage);
	}

	/** A hurt event added by hand, with an optional {@code damage} and a knockback direction {@code from}. */
	private void manualHurt(Scene scene, SceneEvaluator eval, SceneObject target, SceneEvent event, InteractionRules rules) {
		ObjectState t = eval.evaluate(target, event.tick());
		if (!t.exists() || t.dead()) {
			return;
		}
		double damage = event.params().get("damage") instanceof Number n ? n.doubleValue() : 0;
		Vec3 push = Vec3.ZERO;
		if (event.target() != null) {
			Optional<SceneObject> from = scene.object(event.target());
			if (from.isPresent()) {
				push = t.position().subtract(eval.evaluate(from.get(), event.tick()).position());
			}
		}
		double strength = push.horizontalLength() > 0 ? VanillaCombat.BASE_KNOCKBACK * rules.knockback() : 0;
		applyHitWithoutEvent(scene, eval, target, t, event.tick(), damage, push, strength, rules, event.id());
	}

	private void applyHit(Scene scene, SceneEvaluator eval, SceneObject target, ObjectState t, int tick, double damage,
			Vec3 push, double strength, InteractionRules rules, String cause, String attackerId) {
		target.addEvent(new SceneEvent(cause + ":hurt", tick, "hurt", attackerId, java.util.Map.of(), cause));
		applyHitWithoutEvent(scene, eval, target, t, tick, damage, push, strength, rules, cause);
	}

	private void applyHitWithoutEvent(Scene scene, SceneEvaluator eval, SceneObject target, ObjectState t, int tick,
			double damage, Vec3 push, double strength, InteractionRules rules, String cause) {
		lastHitTick.put(target.id(), tick);
		lastHitDamage.put(target.id(), damage);

		if (damage > 0) {
			Channel<Double> health = target.channel(BuiltInChannels.HEALTH);
			if (health.isEmpty() && health.defaultValue() == (double) BuiltInChannels.HEALTH.defaultValue()) {
				health.setDefaultValue(combat.maxHealth(target));
			}
			double before = health.valueAt(tick);
			double after = before - damage;
			if (health.keys().stream().noneMatch(k -> k.tick() < tick)) {
				putGenerated(health, 0, before, cause);
			}
			putGenerated(health, tick, after, cause);
			if (rules.autoDeath() && after <= 0 && !t.dead()) {
				putGenerated(target.channel(BuiltInChannels.DEAD), tick, true, cause);
			}
		}

		double resisted = strength * (1 - combat.knockbackResistance(target));
		if (resisted > 0 && push.horizontalLength() > 1.0e-6) {
			Channel<Vec3> offset = target.channel(BuiltInChannels.OFFSET);
			Vec3 base = offset.valueAt(tick);
			Vec3[] path = VanillaCombat.knockbackPath(push, resisted);
			for (int i = 0; i < path.length; i++) {
				putGenerated(offset, tick + i, base.add(path[i]), cause);
			}
		}
	}

	/** Adds a generated keyframe unless the user has keyed that tick. */
	private static <T> void putGenerated(Channel<T> channel, int tick, T value, String cause) {
		Optional<Keyframe<T>> existing = channel.keyAt(tick);
		if (existing.isPresent() && !existing.get().isGenerated()) {
			return;
		}
		Interpolation interp = channel.type().interpolates() && channel.type().components() == 3
				? Interpolation.LINEAR : Interpolation.STEP;
		channel.put(new Keyframe<>(tick, value, interp, null, cause));
	}

	private AttackResult miss(SceneEvent event, SceneObject attacker, String targetId, String reason) {
		return new AttackResult(event.id(), attacker.id(), targetId, event.tick(), false, reason, 0);
	}

	/** Vanilla-style hit test: reach, facing (for players) and line of sight. Empty when the attack lands. */
	Optional<String> hitTest(SceneObject attacker, ObjectState a, SceneObject target, ObjectState t) {
		double tw = combat.width(target) / 2;
		double th = combat.height(target);
		Vec3 tMin = t.position().add(-tw, 0, -tw);
		Vec3 tMax = t.position().add(tw, th, tw);
		if (combat.isPlayerLike(attacker)) {
			Vec3 eye = a.position().add(0, combat.height(attacker) * 0.85, 0);
			Vec3 closest = new Vec3(Math.clamp(eye.x(), tMin.x(), tMax.x()), Math.clamp(eye.y(), tMin.y(), tMax.y()),
					Math.clamp(eye.z(), tMin.z(), tMax.z()));
			if (eye.distanceTo(closest) > VanillaCombat.PLAYER_REACH) {
				return Optional.of("Out of reach");
			}
			Vec3 toTarget = t.position().add(0, th / 2, 0).subtract(eye);
			double yawToTarget = toTarget.yaw();
			double diff = Math.abs(io.github.firestormfmd.scenescripter.core.path.LocomotionPlanner.wrapDegrees(yawToTarget - a.headYaw()));
			if (diff > 90) {
				return Optional.of("Not facing the target");
			}
			if (!combat.lineOfSight(eye, t.position().add(0, th / 2, 0))) {
				return Optional.of("Something is in the way");
			}
		} else {
			double aw = combat.width(attacker) / 2 + VanillaCombat.MOB_MELEE_REACH;
			double dx = Math.max(0, Math.abs(a.position().x() - t.position().x()) - aw - tw);
			double dz = Math.max(0, Math.abs(a.position().z() - t.position().z()) - aw - tw);
			double dy = Math.max(0, Math.max(t.position().y() - (a.position().y() + combat.height(attacker)),
					a.position().y() - (t.position().y() + th)));
			if (dx > 0 || dz > 0 || dy > 0) {
				return Optional.of("Out of reach");
			}
		}
		return Optional.empty();
	}

	/** The closest living object in front of the attacker, for attacks with no target set. */
	private static SceneObject nearestInFront(Scene scene, SceneEvaluator eval, SceneObject attacker, ObjectState a, int tick) {
		SceneObject best = null;
		double bestDist = Double.MAX_VALUE;
		for (SceneObject o : scene.objects()) {
			if (o == attacker) {
				continue;
			}
			ObjectState s = eval.evaluate(o, tick);
			if (!s.exists() || s.dead()) {
				continue;
			}
			Vec3 d = s.position().subtract(a.position());
			double diff = Math.abs(io.github.firestormfmd.scenescripter.core.path.LocomotionPlanner.wrapDegrees(d.yaw() - a.headYaw()));
			if (diff <= 90 && d.length() < bestDist) {
				bestDist = d.length();
				best = o;
			}
		}
		return best;
	}
}
