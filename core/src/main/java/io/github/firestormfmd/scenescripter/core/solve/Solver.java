package io.github.firestormfmd.scenescripter.core.solve;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Interpolation;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.BlockPos;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.runtime.ObjectState;
import io.github.firestormfmd.scenescripter.core.runtime.SceneEvaluator;
import io.github.firestormfmd.scenescripter.core.scene.AttackMode;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.CritMode;
import io.github.firestormfmd.scenescripter.core.scene.ExplosionRules;
import io.github.firestormfmd.scenescripter.core.scene.InteractionRules;
import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Turns events into their consequences under the interaction rules: hit tests, damage, hurt flashes, knockback,
 * deaths, explosions with their craters and chain reactions, projectile flights and block changes. Results are
 * written into the scene as generated keyframes, events and objects, marked with the event that caused them, so
 * they show up on the timeline and play back like anything else.
 *
 * <p>Solving is a pure function of the user's content: all generated content is removed first and everything is
 * rebuilt in tick order, so a knockback from one hit is in place before a later hit is tested, and an explosion
 * sees the crater of the one before. Keyframes the user set are never overwritten; editing a generated keyframe
 * detaches it and the solver leaves that tick alone from then on.
 */
public final class Solver {
	/** What happened to one attack or shot. */
	public record AttackResult(String eventId, String attackerId, String targetId, int tick, boolean hit, String reason,
			double damage) {
	}

	/**
	 * One explosion as it will play.
	 *
	 * @param eventId the event that plays it: the user's explode event or a generated one
	 * @param blocks blocks it breaks
	 */
	public record ExplosionResult(String eventId, String ownerId, int tick, Vec3 center, float power,
			List<BlockPos> blocks) {
	}

	/** Everything a solve produced besides what it wrote into the scene. */
	public record Solution(List<AttackResult> attacks, List<ExplosionResult> explosions) {
	}

	/** Longest a projectile is followed before it is dropped. */
	static final int MAX_FLIGHT = 200;
	/** How far past an object's box a projectile still hits it. */
	static final double PROJECTILE_MARGIN = 0.3;

	private record Action(int tick, int priority, int seq, Runnable run) {
	}

	private final CombatModel combat;
	private final Map<String, Integer> lastHitTick = new HashMap<>();
	private final Map<String, Double> lastHitDamage = new HashMap<>();
	private final PriorityQueue<Action> queue = new PriorityQueue<>(Comparator.comparingInt(Action::tick)
			.thenComparingInt(Action::priority).thenComparingInt(Action::seq));
	private int seq;
	private Scene scene;
	private SceneEvaluator eval;
	private BlockWorld world = BlockWorld.EMPTY;
	private List<AttackResult> attacks;
	private List<ExplosionResult> explosions;

	public Solver(CombatModel combat) {
		this.combat = combat;
	}

	/** Solves against an empty world; returns the attack results. */
	public List<AttackResult> solve(Scene scene, SceneEvaluator evaluator) {
		return solve(scene, evaluator, BlockWorld.EMPTY).attacks();
	}

	public Solution solve(Scene scene, SceneEvaluator evaluator, BlockWorld blockWorld) {
		this.scene = scene;
		this.eval = evaluator;
		this.world = blockWorld;
		lastHitTick.clear();
		lastHitDamage.clear();
		queue.clear();
		seq = 0;
		attacks = new ArrayList<>();
		explosions = new ArrayList<>();
		for (String removed : clearGenerated(scene)) {
			evaluator.invalidate(removed);
		}
		world.reset();

		for (SceneObject o : List.copyOf(scene.objects())) {
			for (SceneEvent e : o.events()) {
				switch (e.type()) {
					case "place_block", "break_block", "use_block" -> scheduleIf(o, e, 0, () -> blockEvent(o, e));
					case "attack" -> scheduleIf(o, e, 1, () -> attacks.add(attack(o, e, rulesFor(scene, o, e))));
					case "hurt" -> scheduleIf(o, e, 1, () -> manualHurt(o, e, rulesFor(scene, o, e)));
					case "shoot" -> scheduleIf(o, e, 1, () -> shoot(o, e));
					case "launch" -> scheduleIf(o, e, 1, () -> launch(o, e));
					case "ignite" -> scheduleIf(o, e, 2, () -> ignite(o, e));
					case "defuse" -> scheduleIf(o, e, 2, () -> putGenerated(o.channel(BuiltInChannels.IGNITED), e.tick(), false, e.id()));
					case "explode" -> scheduleIf(o, e, 3, () -> explode(o, e.id(), e, e.tick(), null));
					default -> {
					}
				}
			}
			if (isTnt(o) && o.events().stream().noneMatch(e -> e.type().equals("explode"))) {
				int at = o.spawnTick() + fuse(o, Explosions.TNT_FUSE);
				schedule(at, 3, () -> explode(o, null, null, at, null));
			}
		}

		while (!queue.isEmpty()) {
			queue.poll().run().run();
		}
		attacks.sort(Comparator.comparingInt(AttackResult::tick));
		return new Solution(List.copyOf(attacks), List.copyOf(explosions));
	}

	/**
	 * Schedules an event unless its {@code if} condition fails. The condition is checked when the event's turn
	 * comes, so it sees what earlier events did, such as health lost to an earlier hit.
	 */
	private void scheduleIf(SceneObject owner, SceneEvent e, int priority, Runnable run) {
		schedule(e.tick(), priority, () -> {
			if (io.github.firestormfmd.scenescripter.core.scene.EventCondition.holds(e, owner, scene)) {
				run.run();
			}
		});
	}

	private void schedule(int tick, int priority, Runnable run) {
		queue.add(new Action(tick, priority, seq++, run));
	}

	/**
	 * Removes everything a previous solve generated.
	 *
	 * @return IDs of the generated objects that were removed
	 */
	public static List<String> clearGenerated(Scene scene) {
		List<String> removed = new ArrayList<>();
		for (SceneObject o : List.copyOf(scene.objects())) {
			if (o.isGenerated()) {
				scene.removeObject(o.id());
				removed.add(o.id());
				continue;
			}
			for (Channel<?> ch : o.channels().values()) {
				ch.removeIf(Keyframe::isGenerated);
			}
			for (SceneEvent e : List.copyOf(o.events())) {
				if (e.isGenerated()) {
					o.removeEvent(e.id());
				}
			}
		}
		return removed;
	}

	/** Scene rules, overridden by the object's, overridden by the event's own parameters. */
	static InteractionRules rulesFor(Scene scene, SceneObject owner, SceneEvent event) {
		InteractionRules fromEvent = InteractionRules.INHERIT;
		Object mode = event == null ? null : event.params().get("mode");
		if (mode instanceof String s) {
			try {
				fromEvent = fromEvent.withAttack(AttackMode.byId(s));
			} catch (IllegalArgumentException ignored) {
				// a mistyped mode falls back to the object's and scene's rules
			}
		}
		Map<String, Object> p = event == null ? Map.of() : event.params();
		ExplosionRules blast = new ExplosionRules(bool(p, "breakBlocks"), bool(p, "damageObjects"), null, null,
				bool(p, "fire"));
		InteractionRules r = new InteractionRules(fromEvent.attack(),
				p.get("knockback") instanceof Number n ? n.doubleValue() : null,
				p.get("crit") instanceof String c ? critMode(c) : null,
				null, null, null, blast);
		return r.withFallback(owner.rules()).withFallback(scene.settings().rules()).withFallback(InteractionRules.DEFAULTS);
	}

	private static CritMode critMode(String id) {
		try {
			return CritMode.byId(id);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private AttackResult attack(SceneObject attacker, SceneEvent event, InteractionRules rules) {
		int tick = event.tick();
		if (rules.attack() == AttackMode.ANIMATION_ONLY) {
			return new AttackResult(event.id(), attacker.id(), event.target(), tick, false, "Animation only", 0);
		}
		ObjectState a = eval.evaluate(attacker, tick);
		if (!a.exists() || a.dead()) {
			return miss(event, attacker, event.target(), "Attacker is not there");
		}
		SceneObject target = event.target() == null
				? nearestInFront(attacker, a, tick)
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

		damage = afterCooldown(target, tick, damage, rules);
		if (damage < 0) {
			return miss(event, attacker, target.id(), "Target is still recovering from the last hit");
		}

		Vec3 push = t.position().subtract(a.position());
		double strength = VanillaCombat.BASE_KNOCKBACK + (a.sprinting() ? 0.5 : 0);
		applyHit(target, t, tick, damage, push, strength * rules.knockback(), rules, event.id(), attacker.id());
		return new AttackResult(event.id(), attacker.id(), target.id(), tick, true, "Hit", damage);
	}

	/** A hurt event added by hand, with an optional {@code damage} and a knockback direction {@code from}. */
	private void manualHurt(SceneObject target, SceneEvent event, InteractionRules rules) {
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
		lastHitTick.put(target.id(), event.tick());
		lastHitDamage.put(target.id(), damage);
		applyHitWithoutEvent(target, t, event.tick(), damage, push, strength, rules, event.id());
	}

	/**
	 * Damage left after the vanilla hit cooldown: within 10 ticks of a hit only damage above the last hit's gets
	 * through. Returns a negative number if nothing does. Records this hit.
	 */
	private double afterCooldown(SceneObject target, int tick, double damage, InteractionRules rules) {
		double dealt = damage;
		if (rules.hitCooldown()) {
			Integer last = lastHitTick.get(target.id());
			if (last != null && tick - last < VanillaCombat.HIT_COOLDOWN) {
				double previous = lastHitDamage.getOrDefault(target.id(), 0.0);
				if (damage <= previous) {
					return -1;
				}
				dealt = damage - previous;
			}
		}
		lastHitTick.put(target.id(), tick);
		lastHitDamage.put(target.id(), damage);
		return dealt;
	}

	private void applyHit(SceneObject target, ObjectState t, int tick, double damage, Vec3 push, double strength,
			InteractionRules rules, String cause, String attackerId) {
		addGeneratedEvent(target, new SceneEvent(cause + ":hurt", tick, "hurt", attackerId, Map.of(), cause));
		applyHitWithoutEvent(target, t, tick, damage, push, strength, rules, cause);
	}

	private static void addGeneratedEvent(SceneObject owner, SceneEvent event) {
		if (owner.findEvent(event.id()).isEmpty()) {
			owner.addEvent(event);
		}
	}

	private void applyHitWithoutEvent(SceneObject target, ObjectState t, int tick, double damage, Vec3 push,
			double strength, InteractionRules rules, String cause) {
		applyDamage(target, t, tick, damage, rules, cause);
		double resisted = strength * (1 - combat.knockbackResistance(target));
		if (resisted > 0 && push.horizontalLength() > 1.0e-6) {
			applyPath(target, tick, VanillaCombat.knockbackPath(push, resisted), cause);
		}
	}

	private void applyDamage(SceneObject target, ObjectState t, int tick, double damage, InteractionRules rules,
			String cause) {
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

	}

	/**
	 * Throws an object: {@code to} ("x y z") aims it to come to rest there, or {@code velocity} ("x y z", blocks per
	 * tick) sets the throw directly. TNT flies with TNT physics, anything else like a knocked-back mob.
	 */
	private void launch(SceneObject o, SceneEvent event) {
		ObjectState s = eval.evaluate(o, event.tick());
		if (!s.exists()) {
			return;
		}
		Vec3 velocity = null;
		Vec3 to = parseVec(event.params().get("to"));
		if (to != null) {
			Vec3 flat = new Vec3(to.x() - s.position().x(), 0, to.z() - s.position().z());
			double upward = event.params().get("up") instanceof Number n ? n.doubleValue() : 0.35;
			velocity = flat.horizontalLength() < 1.0e-6 ? new Vec3(0, upward, 0)
					: VanillaCombat.throwVelocity(flat, flat.horizontalLength(), upward, isTnt(o));
		} else {
			velocity = parseVec(event.params().get("velocity"));
		}
		if (velocity != null) {
			applyPath(o, event.tick(), isTnt(o) ? VanillaCombat.tntPath(velocity) : VanillaCombat.launchPath(velocity), event.id());
		}
	}

	private static Vec3 parseVec(Object value) {
		if (!(value instanceof String text)) {
			return null;
		}
		String[] c = text.trim().split("\\s+");
		try {
			return c.length == 3 ? new Vec3(Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2])) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** Lays a displacement path, tick by tick from {@code tick}, into the offset channel. */
	private static void applyPath(SceneObject target, int tick, Vec3[] path, String cause) {
		if (path.length <= 1) {
			return;
		}
		Channel<Vec3> offset = target.channel(BuiltInChannels.OFFSET);
		Vec3 base = offset.valueAt(tick);
		// A new push replaces the rest of an earlier one, like a new velocity would.
		offset.removeIf(k -> k.isGenerated() && k.tick() > tick);
		for (int i = 0; i < path.length; i++) {
			putGenerated(offset, tick + i, base.add(path[i]), cause);
		}
	}

	// ---- Explosions ----

	/**
	 * Explodes an object at a tick: removes it, breaks blocks, sets off TNT in the blast, lights fires, and hurts and
	 * pushes the objects around it.
	 *
	 * @param eventId the user's explode event, or null to generate one (fuses, ignitions, fireballs)
	 * @param at where it explodes, or null for the object's position
	 */
	private void explode(SceneObject owner, String eventId, SceneEvent event, int tick, Vec3 at) {
		ObjectState s = eval.evaluate(owner, tick);
		if (!s.exists() && at == null) {
			return;
		}
		String id = eventId;
		if (id == null) {
			id = owner.id() + ":explode";
			addGeneratedEvent(owner, new SceneEvent(id, tick, "explode", null, Map.of(), owner.id()));
		}
		Map<String, Object> params = event == null ? Map.of() : event.params();
		Vec3 center = at != null ? at : s.position().add(0, centerHeight(owner), 0);
		float power = params.get("power") instanceof Number n ? n.floatValue() : power(owner);
		ExplosionRules rules = rulesFor(scene, owner, event).explosions();
		putGenerated(owner.channel(BuiltInChannels.REMOVED), tick, true, id);

		Random random = new Random(params.get("seed") instanceof Number n ? n.longValue() : seed(id));
		List<BlockPos> broken = new ArrayList<>();
		if (Boolean.TRUE.equals(rules.breakBlocks())) {
			Set<BlockPos> protect = parseBlocks(params.get("protect"));
			int chain = 0;
			for (BlockPos pos : Explosions.affectedBlocks(world, center, power, random)) {
				if (protect.contains(pos)) {
					continue;
				}
				if (world.isTnt(pos)) {
					primeTnt(pos, tick, id + ":tnt" + chain++, id, Explosions.chainFuse(random));
				}
				world.destroy(pos, id, tick);
				broken.add(pos);
			}
			if (Boolean.TRUE.equals(rules.fire())) {
				for (BlockPos pos : broken) {
					if (random.nextInt(3) == 0 && world.canLightFire(pos)) {
						world.lightFire(pos, id, tick);
					}
				}
			}
		}
		explosions.add(new ExplosionResult(id, owner.id(), tick, center, power, List.copyOf(broken)));

		if (Boolean.TRUE.equals(rules.damageObjects())) {
			InteractionRules hitRules = rulesFor(scene, owner, event);
			for (SceneObject o : List.copyOf(scene.objects())) {
				if (o == owner) {
					continue;
				}
				ObjectState t = eval.evaluate(o, tick);
				if (!t.exists() || t.dead()) {
					continue;
				}
				boolean living = combat.isLiving(o);
				Explosions.Impact impact = Explosions.impact(world, center, power, t.position(), combat.width(o),
						combat.height(o), isTnt(o), living ? combat.explosionKnockbackResistance(o) : 0);
				if (impact == null) {
					continue;
				}
				if (living) {
					double damage = afterCooldown(o, tick, impact.damage(), hitRules);
					if (damage < 0) {
						continue;
					}
					addGeneratedEvent(o, new SceneEvent(id + ":hurt", tick, "hurt", owner.id(), Map.of(), id));
					applyDamage(o, t, tick, damage, hitRules, id);
				}
				applyPath(o, tick, VanillaCombat.launchPath(impact.velocity()), id);
			}
		}
	}

	/** A TNT block set off by an explosion becomes a generated TNT object that explodes after its fuse. */
	private void primeTnt(BlockPos pos, int tick, String id, String cause, int fuse) {
		if (scene.object(id).isPresent()) {
			return;
		}
		SceneObject tnt = new SceneObject(id, "TNT", "minecraft:tnt");
		tnt.setGeneratedBy(cause);
		tnt.setLifetime(tick, -1);
		tnt.appearance().put("fuse", Integer.toString(fuse));
		tnt.channel(BuiltInChannels.POSITION).setDefaultValue(new Vec3(pos.x() + 0.5, pos.y(), pos.z() + 0.5));
		scene.addObject(tnt);
		int at = tick + fuse;
		schedule(at, 3, () -> explode(tnt, null, null, at, null));
	}

	/** An ignite event starts a creeper's swell; it explodes when the fuse runs out unless an explode event comes first. */
	private void ignite(SceneObject owner, SceneEvent event) {
		ObjectState s = eval.evaluate(owner, event.tick());
		if (!s.exists() || s.dead()) {
			return;
		}
		putGenerated(owner.channel(BuiltInChannels.IGNITED), event.tick(), true, event.id());
		boolean manual = owner.events().stream()
				.anyMatch(e -> e.type().equals("explode") && !e.isGenerated() && e.tick() >= event.tick());
		int at = event.tick() + (event.params().get("fuse") instanceof Number n ? n.intValue()
				: fuse(owner, Explosions.CREEPER_FUSE));
		// A defuse before the fuse runs out stops the swell, and the creeper doesn't go off.
		boolean defused = owner.events().stream().anyMatch(e -> e.type().equals("defuse") && e.tick() > event.tick()
				&& e.tick() < at && io.github.firestormfmd.scenescripter.core.scene.EventCondition.holds(e, owner, scene));
		if (!manual && !defused) {
			schedule(at, 3, () -> explode(owner, null, event, at, null));
		}
	}

	static boolean isTnt(SceneObject o) {
		return o.entityType().equals("minecraft:tnt") || o.entityType().equals("minecraft:tnt_minecart");
	}

	private static int fuse(SceneObject o, int fallback) {
		try {
			return Integer.parseInt(o.appearance().getOrDefault("fuse", Integer.toString(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Explosion power of an object: its {@code power} appearance setting, or the vanilla value for its type. */
	static float power(SceneObject o) {
		String set = o.appearance().get("power");
		if (set != null) {
			try {
				return Float.parseFloat(set.trim());
			} catch (NumberFormatException ignored) {
				// fall through to the type's default
			}
		}
		return switch (o.entityType()) {
			case "minecraft:creeper" -> "true".equals(o.appearance().get("powered"))
					? Explosions.CHARGED_CREEPER_POWER : Explosions.CREEPER_POWER;
			case "minecraft:end_crystal" -> Explosions.END_CRYSTAL_POWER;
			case "minecraft:fireball" -> Explosions.FIREBALL_POWER;
			default -> Explosions.TNT_POWER;
		};
	}

	/** Height above an object's feet where it explodes: vanilla TNT goes off 1/16 of its height up. */
	private double centerHeight(SceneObject o) {
		return isTnt(o) ? combat.height(o) * 0.0625 : 0;
	}

	private static long seed(String id) {
		return id.hashCode() * 0x9E3779B97F4A7C15L;
	}

	/** Blocks from a list like {@code "1 2 3; 4 5 6"}. */
	static Set<BlockPos> parseBlocks(Object value) {
		Set<BlockPos> out = new HashSet<>();
		if (!(value instanceof String s) || s.isBlank()) {
			return out;
		}
		for (String part : s.split(";")) {
			String[] c = part.trim().split("[\\s,]+");
			if (c.length == 3) {
				try {
					out.add(new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])));
				} catch (NumberFormatException ignored) {
					// skip entries that are not three whole numbers
				}
			}
		}
		return out;
	}

	private static Boolean bool(Map<String, Object> params, String key) {
		return params.get(key) instanceof Boolean b ? b : null;
	}

	// ---- Block events ----

	private void blockEvent(SceneObject owner, SceneEvent e) {
		Optional<BlockPos> pos = blockPos(e.params());
		if (pos.isEmpty()) {
			return;
		}
		switch (e.type()) {
			case "place_block" -> {
				if (e.params().get("block") instanceof String block) {
					world.place(pos.get(), block, e.id(), e.tick());
				}
			}
			case "break_block" -> world.destroy(pos.get(), e.id(), e.tick());
			case "use_block" -> world.use(pos.get(), e.id(), e.tick());
			default -> {
			}
		}
	}

	/** The block an event acts on, from its {@code x}, {@code y} and {@code z} parameters. */
	public static Optional<BlockPos> blockPos(Map<String, Object> params) {
		if (params.get("x") instanceof Number x && params.get("y") instanceof Number y && params.get("z") instanceof Number z) {
			return Optional.of(new BlockPos(x.intValue(), y.intValue(), z.intValue()));
		}
		return Optional.empty();
	}

	// ---- Projectiles ----

	/** Projectile a shooter uses when the event does not say. */
	static String defaultProjectile(SceneObject shooter) {
		return switch (shooter.entityType()) {
			case "minecraft:snow_golem" -> "minecraft:snowball";
			case "minecraft:blaze" -> "minecraft:small_fireball";
			case "minecraft:ghast", "minecraft:happy_ghast" -> "minecraft:fireball";
			case "minecraft:witch" -> "minecraft:splash_potion";
			case "minecraft:drowned" -> "minecraft:trident";
			case "minecraft:breeze" -> "minecraft:wind_charge";
			default -> "minecraft:arrow";
		};
	}

	/**
	 * A shot: spawns a generated projectile object and follows it tick by tick, aimed at a target object (leading
	 * it), at a point, or straight along the shooter's gaze.
	 */
	private void shoot(SceneObject shooter, SceneEvent event) {
		int tick = event.tick();
		ObjectState a = eval.evaluate(shooter, tick);
		String targetId = event.target();
		if (!a.exists() || a.dead()) {
			attacks.add(miss(event, shooter, targetId, "Shooter is not there"));
			return;
		}
		Map<String, Object> p = event.params();
		Ballistics.Kind kind = Ballistics.kind(p.get("projectile") instanceof String t ? t : defaultProjectile(shooter));
		double power = p.get("power") instanceof Number n ? Math.clamp(n.doubleValue(), 0.05, 1.0) : 1.0;
		double speed = p.get("speed") instanceof Number n ? n.doubleValue() : kind.speed() * power;
		Vec3 from = a.position().add(0, combat.height(shooter) * 0.85 - 0.1, 0);

		Vec3 aimAt = null;
		SceneObject target = targetId == null ? null : scene.object(targetId).orElse(null);
		if (target != null) {
			ObjectState t = eval.evaluate(target, tick);
			aimAt = t.position().add(0, combat.height(target) / 3, 0);
			// Lead a moving target: aim where it will be when the shot arrives.
			for (int i = 0; i < 3; i++) {
				int flight = (int) Math.round(aimAt.subtract(from).length() / Math.max(speed, 0.1));
				aimAt = eval.evaluate(target, tick + flight).position().add(0, combat.height(target) / 3, 0);
			}
		} else if (p.get("at") instanceof String point) {
			String[] c = point.trim().split("[\\s,]+");
			if (c.length == 3) {
				try {
					aimAt = new Vec3(Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]));
				} catch (NumberFormatException ignored) {
					// shoot along the gaze instead
				}
			}
		}
		float yaw;
		float pitch;
		boolean reachable = true;
		if (aimAt != null) {
			float[] aim = Ballistics.aim(kind, from, aimAt, speed);
			yaw = aim[0];
			pitch = aim[1];
			reachable = aim[2] > 0;
		} else {
			yaw = a.headYaw();
			pitch = a.headPitch();
		}

		SceneObject projectile = new SceneObject(event.id() + ":projectile", "Projectile", kind.entityType());
		projectile.setGeneratedBy(event.id());
		projectile.setLifetime(tick, -1);
		if (kind.scalesWithSpeed() && power >= 1.0) {
			projectile.appearance().put("crit", "true");
		}
		projectile.appearance().put("owner", shooter.id());
		scene.addObject(projectile);
		Vec3 velocity = Ballistics.velocity(yaw, pitch, speed);
		keyFlight(projectile, tick, from, velocity, event.id());
		InteractionRules rules = rulesFor(scene, shooter, event);
		Random random = new Random(seed(event.id()));
		String why = reachable ? null : "Out of range";
		schedule(tick + 1, 1, () -> fly(projectile, shooter, target, event, kind, rules, random, tick, from, velocity, why));
	}

	/** One tick of a projectile's flight; schedules the next unless it hit something. */
	private void fly(SceneObject projectile, SceneObject shooter, SceneObject target, SceneEvent event,
			Ballistics.Kind kind, InteractionRules rules, Random random, int startTick, Vec3 pos, Vec3 vel,
			String outOfRange) {
		int tick = startTick + 1;
		Vec3[] next = Ballistics.step(kind, pos, vel);
		Vec3 to = next[0];

		double blockT = 2;
		Optional<Vec3> blockHit = world.clip(pos, to);
		if (blockHit.isPresent()) {
			double len = to.subtract(pos).length();
			blockT = len < 1.0e-9 ? 0 : blockHit.get().subtract(pos).length() / len;
		}
		SceneObject hitObject = null;
		ObjectState hitState = null;
		double objectT = 2;
		for (SceneObject o : scene.objects()) {
			if (o == projectile || o == shooter || o.isGenerated() && !combat.isLiving(o)) {
				continue;
			}
			ObjectState s = eval.evaluate(o, tick);
			if (!s.exists() || s.dead() || !combat.isLiving(o)) {
				continue;
			}
			double w = combat.width(o) / 2 + PROJECTILE_MARGIN;
			Vec3 min = s.position().add(-w, -PROJECTILE_MARGIN, -w);
			Vec3 max = s.position().add(w, combat.height(o) + PROJECTILE_MARGIN, w);
			double t = Ballistics.segmentHitsBox(pos, to, min, max);
			if (t >= 0 && t < objectT) {
				objectT = t;
				hitObject = o;
				hitState = s;
			}
		}

		String cause = event.id();
		if (hitObject != null && objectT <= blockT) {
			Vec3 at = Vec3.lerp(pos, to, objectT);
			keyFlight(projectile, tick, at, vel, cause);
			putGenerated(projectile.channel(BuiltInChannels.REMOVED), tick, true, cause);
			if (kind.explodes()) {
				explode(projectile, null, event, tick, at);
				return;
			}
			if (rules.attack() == AttackMode.ANIMATION_ONLY) {
				attacks.add(new AttackResult(event.id(), shooter.id(), hitObject.id(), tick, false, "Animation only", 0));
				return;
			}
			if (!rules.friendlyFire() && shooter.group() != null && shooter.group().equals(hitObject.group())) {
				attacks.add(new AttackResult(event.id(), shooter.id(), hitObject.id(), tick, false, "Same group", 0));
				return;
			}
			double damage = event.params().get("damage") instanceof Number n ? n.doubleValue() : Ballistics.damage(kind, vel);
			if (!(event.params().get("damage") instanceof Number) && "true".equals(projectile.appearance().get("crit"))) {
				damage += random.nextInt((int) damage / 2 + 2);
			}
			double[] armor = VanillaCombat.armor(List.of(hitState.equipment().getOrDefault("head", ""),
					hitState.equipment().getOrDefault("chest", ""), hitState.equipment().getOrDefault("legs", ""),
					hitState.equipment().getOrDefault("feet", "")));
			damage = VanillaCombat.afterArmor(damage, armor[0], armor[1]);
			double dealt = afterCooldown(hitObject, tick, damage, rules);
			if (dealt < 0) {
				attacks.add(new AttackResult(event.id(), shooter.id(), hitObject.id(), tick, false,
						"Target is still recovering from the last hit", 0));
				return;
			}
			applyHit(hitObject, hitState, tick, dealt, vel, VanillaCombat.BASE_KNOCKBACK * rules.knockback(), rules,
					cause, shooter.id());
			attacks.add(new AttackResult(event.id(), shooter.id(), hitObject.id(), tick, true, "Hit", dealt));
			return;
		}
		if (blockHit.isPresent()) {
			Vec3 at = blockHit.get().subtract(vel.normalize().scale(0.05));
			keyFlight(projectile, tick, at, vel, cause);
			if (kind.explodes()) {
				putGenerated(projectile.channel(BuiltInChannels.REMOVED), tick, true, cause);
				explode(projectile, null, event, tick, at);
			} else if (!kind.sticks()) {
				putGenerated(projectile.channel(BuiltInChannels.REMOVED), tick, true, cause);
			}
			if (target != null) {
				attacks.add(new AttackResult(event.id(), shooter.id(), target.id(), tick, false,
						outOfRange != null ? outOfRange : "Hit a block", 0));
			}
			return;
		}
		keyFlight(projectile, tick, to, vel, cause);
		if (tick - projectile.spawnTick() >= MAX_FLIGHT || !world.inBuildHeight((int) Math.floor(to.y()))) {
			putGenerated(projectile.channel(BuiltInChannels.REMOVED), tick, true, cause);
			if (target != null) {
				attacks.add(new AttackResult(event.id(), shooter.id(), target.id(), tick, false,
						outOfRange != null ? outOfRange : "Missed", 0));
			}
			return;
		}
		Vec3 nextVel = next[1];
		schedule(tick + 1, 1, () -> fly(projectile, shooter, target, event, kind, rules, random, tick, to, nextVel,
				outOfRange));
	}

	/** Keys a projectile's position and facing, which vanilla points along its velocity. */
	private static void keyFlight(SceneObject projectile, int tick, Vec3 pos, Vec3 vel, String cause) {
		putGenerated(projectile.channel(BuiltInChannels.POSITION), tick, pos, cause);
		double horizontal = Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z());
		putGenerated(projectile.channel(BuiltInChannels.BODY_YAW), tick, Math.toDegrees(Math.atan2(vel.x(), vel.z())), cause);
		putGenerated(projectile.channel(BuiltInChannels.HEAD_PITCH), tick, Math.toDegrees(Math.atan2(vel.y(), horizontal)), cause);
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
			Vec3 aim = t.position().add(0, th / 2, 0);
			if (!combat.lineOfSight(eye, aim) || world.clip(eye, aim).isPresent()) {
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
	private SceneObject nearestInFront(SceneObject attacker, ObjectState a, int tick) {
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
