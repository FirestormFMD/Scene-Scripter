package io.github.firestormfmd.scenescripter.core.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.firestormfmd.scenescripter.core.scene.Scene;
import io.github.firestormfmd.scenescripter.core.scene.SceneEvent;
import io.github.firestormfmd.scenescripter.core.scene.SceneObject;

class PlaybackClockTest {
	@Test
	void playsOneTickPerServerTick() {
		PlaybackClock c = new PlaybackClock(100);
		c.play();
		assertEquals(new PlaybackClock.Step(0, 1, true), c.advance().orElseThrow());
		assertEquals(new PlaybackClock.Step(1, 2, true), c.advance().orElseThrow());
	}

	@Test
	void pausedClockDoesNotMove() {
		PlaybackClock c = new PlaybackClock(100);
		assertTrue(c.advance().isEmpty());
	}

	@Test
	void halfSpeedMovesEveryOtherTick() {
		PlaybackClock c = new PlaybackClock(100);
		c.setSpeed(0.5);
		c.play();
		assertTrue(c.advance().isEmpty());
		assertEquals(1, c.advance().orElseThrow().to());
	}

	@Test
	void doubleSpeedSkipsAheadContiguously() {
		PlaybackClock c = new PlaybackClock(100);
		c.setSpeed(2);
		c.play();
		assertEquals(new PlaybackClock.Step(0, 2, true), c.advance().orElseThrow());
	}

	@Test
	void stopsAtTheEnd() {
		PlaybackClock c = new PlaybackClock(3);
		c.play();
		c.advance();
		c.advance();
		assertEquals(3, c.advance().orElseThrow().to());
		assertFalse(c.isPlaying());
	}

	@Test
	void loopsJumpBack() {
		PlaybackClock c = new PlaybackClock(100);
		c.setLoop(10, 12);
		c.seek(11);
		c.play();
		assertEquals(12, c.advance().orElseThrow().to());
		PlaybackClock.Step wrap = c.advance().orElseThrow();
		assertEquals(10, wrap.to());
		assertFalse(wrap.contiguous());
	}

	@Test
	void eventWindowFindsPassedEvents() {
		Scene scene = new Scene("t", 100);
		SceneObject o = new SceneObject("o1", "A", "minecraft:zombie");
		o.addEvent(new SceneEvent("e1", 5, "attack", null, Map.of(), null));
		o.addEvent(new SceneEvent("e2", 6, "hurt", null, Map.of(), null));
		o.addEvent(new SceneEvent("e3", 9, "die", null, Map.of(), null));
		scene.addObject(o);
		List<EventWindow.Fired> fired = EventWindow.between(scene, 5, 8);
		assertEquals(1, fired.size());
		assertEquals("e2", fired.getFirst().event().id());
	}
}
