package io.github.firestormfmd.scenescripter.core.path;

import io.github.firestormfmd.scenescripter.core.math.Vec3;
import io.github.firestormfmd.scenescripter.core.scene.Gait;

/**
 * Where an object is on one tick of a motion clip.
 *
 * @param yaw body facing in Minecraft degrees
 * @param onGround standing on a block or swimming at a surface, rather than jumping or falling
 * @param moving travelling along the path this tick, rather than waiting
 * @param swimming at a fluid surface
 */
public record MotionSample(Vec3 pos, float yaw, boolean onGround, boolean moving, boolean swimming, Gait gait) {
}
