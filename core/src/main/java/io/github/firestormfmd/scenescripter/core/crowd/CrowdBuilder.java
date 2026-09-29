package io.github.firestormfmd.scenescripter.core.crowd;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import io.github.firestormfmd.scenescripter.core.anim.Channel;
import io.github.firestormfmd.scenescripter.core.anim.Keyframe;
import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.BuiltInChannels;
import io.github.firestormfmd.scenescripter.core.scene.MotionClip;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

/**
 * Turns one object into a crowd: copies laid out in a formation that do everything the original does. Keyed
 * positions are moved by each member's offset; motion clips keep the path but walk it side by side (a lateral
 * offset) and start later the further back a member stands, so a block of soldiers marches in step.
 */
public final class CrowdBuilder {
	private CrowdBuilder() {
	}

	/**
	 * @param ids new object IDs by member index
	 * @param ticksPerBlockBack start delay for each block a member stands behind the leader on motion paths
	 * @param jitterTicks random extra delay of up to this many ticks, so a crowd does not move like one
	 * @return the new members; the leader itself is not included
	 */
	public static List<SceneObject> build(SceneObject leader, Formation formation, int count, double spacing,
			IntFunction<String> ids, double ticksPerBlockBack, int jitterTicks, long seed) {
		return build(leader, formation, count, spacing, ids, ticksPerBlockBack, jitterTicks, 0, seed);
	}

	/**
	 * @param speedVariation how much each member's walking speed may differ from the leader's, as a fraction (0.1
	 *                       is up to 10% faster or slower)
	 */
	public static List<SceneObject> build(SceneObject leader, Formation formation, int count, double spacing,
			IntFunction<String> ids, double ticksPerBlockBack, int jitterTicks, double speedVariation, long seed) {
		List<Vec3> offsets = formation.offsets(count + 1, spacing, seed);
		// The member nearest the leader's own place is the leader.
		int leaderIndex = 0;
		for (int i = 1; i < offsets.size(); i++) {
			if (offsets.get(i).length() < offsets.get(leaderIndex).length()) {
				leaderIndex = i;
			}
		}
		Vec3 leaderOffset = offsets.get(leaderIndex);
		double yaw = Math.toRadians(facing(leader));
		Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
		Random random = new Random(seed);

		List<SceneObject> members = new ArrayList<>();
		int n = 0;
		for (int i = 0; i < offsets.size(); i++) {
			if (i == leaderIndex) {
				continue;
			}
			n++;
			Vec3 local = offsets.get(i).subtract(leaderOffset);
			Vec3 world = right.scale(local.x()).add(back.scale(local.z()));
			String id = ids.apply(n);
			SceneObject m = leader.copyAs(id, leader.name() + " " + (n + 1));
			if (leader.group() == null) {
				m.setGroup(leader.id());
			}
			shiftPositions(m, world);
			int delay = (int) Math.round(Math.max(0, local.z()) * ticksPerBlockBack)
					+ (jitterTicks > 0 ? random.nextInt(jitterTicks + 1) : 0);
			for (MotionClip c : List.copyOf(m.motion())) {
				m.removeMotion(c);
				m.addMotion(c.shifted(delay).withLateralOffset(c.lateralOffset() + local.x())
						.withSpeedScale(c.speedScale() * (1 + (random.nextDouble() * 2 - 1) * speedVariation)));
			}
			for (SceneEvent e : List.copyOf(m.events())) {
				m.removeEvent(e.id());
				if (!e.isGenerated()) {
					m.addEvent(new SceneEvent(id + "_" + e.id(), e.tick() + (m.motion().isEmpty() ? 0 : delay), e.type(),
							e.target(), e.params(), null));
				}
			}
			for (Channel<?> ch : m.channels().values()) {
				ch.removeIf(Keyframe::isGenerated);
			}
			members.add(m);
		}
		return members;
	}

	private static void shiftPositions(SceneObject m, Vec3 by) {
		m.channel(BuiltInChannels.POSITION.name()).ifPresent(raw -> {
			Channel<Vec3> ch = m.channel(BuiltInChannels.POSITION);
			ch.setDefaultValue(ch.defaultValue().add(by));
			List<Keyframe<Vec3>> keys = List.copyOf(ch.keys());
			for (Keyframe<Vec3> k : keys) {
				ch.put(new Keyframe<>(k.tick(), k.value().add(by), k.interpolation(), k.handles(), k.generatedBy()));
			}
		});
	}

	/** The leader's facing: its first body-yaw key, or its default. */
	private static double facing(SceneObject leader) {
		return leader.channel(BuiltInChannels.BODY_YAW.name()).map(raw -> {
			Channel<Double> ch = leader.channel(BuiltInChannels.BODY_YAW);
			return ch.keys().isEmpty() ? ch.defaultValue() : ch.keys().getFirst().value();
		}).orElse(0.0);
	}
}
