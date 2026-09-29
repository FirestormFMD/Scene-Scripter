package io.github.firestormfmd.scenescripter.core.path;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JumpPhysicsTest {
	@Test
	void vanillaJumpPeaksAtAboutOneAndAQuarterBlocks() {
		assertEquals(1.2522, JumpPhysics.apexHeight(JumpPhysics.VANILLA_JUMP_VELOCITY), 1e-3);
	}

	@Test
	void velocityForHeightInvertsApex() {
		assertEquals(JumpPhysics.VANILLA_JUMP_VELOCITY, JumpPhysics.velocityForHeight(1.2522), 1e-3);
		assertEquals(2.5, JumpPhysics.apexHeight(JumpPhysics.velocityForHeight(2.5)), 0.05);
	}
}
