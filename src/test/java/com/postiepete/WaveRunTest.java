package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class WaveRunTest
{
	@Test
	public void printedWavesStartTheRunAtTheStartOfWaveOne()
	{
		WaveRun run = new WaveRun("Wave");
		// Wave 1 took 34.2 seconds (57 ticks)
		run.onWaveCompleted(1, 34.2, 1057);
		run.onWaveCompleted(2, 69.6, 1200);

		assertEquals(Integer.valueOf(2), run.lastWave());
		WaveRun.Run result = run.consume();
		assertEquals(Integer.valueOf(1000), result.getStartTick());
		assertEquals(Arrays.asList(new LootEvent.Split("Wave 1", 34.2), new LootEvent.Split("Wave 2", 69.6)), result.getSplits());
	}

	@Test
	public void waveOneStartsANewRun()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveCompleted(1, 30.0, 100);
		run.onWaveCompleted(2, 60.0, 300);

		run.onWaveCompleted(1, 31.2, 2052);

		WaveRun.Run result = run.consume();
		assertEquals(Integer.valueOf(2000), result.getStartTick());
		assertEquals(Collections.singletonList(new LootEvent.Split("Wave 1", 31.2)), result.getSplits());
	}

	@Test
	public void splitsAreNamedByWhatTheGameCallsAWave()
	{
		WaveRun run = new WaveRun("Level");
		run.onWaveCompleted(1, 79.8, 200);
		run.onWaveCompleted(22, 68.4, 400);

		assertEquals(Arrays.asList(new LootEvent.Split("Level 1", 79.8), new LootEvent.Split("Level 22", 68.4)),
			run.consume().getSplits());
	}

	@Test
	public void aWaveShowingTheRunTimeGetsItsOwnTime()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveCompleted(1, 34.2, 100);
		run.onWaveCompleted(2, 69.6, 300);

		// Wave 3 shows the run time, 3:05.00
		assertEquals(Double.valueOf(81.2), run.ownTime(3, 185.0));
	}

	@Test
	public void noOwnTimeWhenAnEarlierWaveIsMissing()
	{
		WaveRun run = new WaveRun("Wave");
		// Turned on after wave 1
		run.onWaveCompleted(2, 69.6, 300);

		assertNull(run.ownTime(3, 185.0));
	}

	@Test
	public void aWaveWithoutATimeStillCountsAsReached()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveCompleted(1, null, 100);

		assertEquals(Integer.valueOf(1), run.lastWave());
		WaveRun.Run result = run.consume();
		assertNull(result.getStartTick());
		assertTrue(result.getSplits().isEmpty());
	}

	@Test
	public void startedWavesAreTimedFromOneStartToTheNext()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveStarted(1, 1000);
		run.onWaveStarted(2, 1037);
		run.onWaveStarted(3, 1080);
		// The kill ends the last wave
		run.finish(1600);

		WaveRun.Run result = run.consume();
		assertEquals(Integer.valueOf(1000), result.getStartTick());
		assertEquals(Arrays.asList(
			new LootEvent.Split("Wave 1", 22.2),
			new LootEvent.Split("Wave 2", 25.8),
			new LootEvent.Split("Wave 3", 312.0)), result.getSplits());
	}

	@Test
	public void aRestartedWaveIsOnlyTimedFromItsRestart()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveStarted(1, 1000);
		run.onWaveStarted(2, 1050);
		// Logged out mid-wave: the wave starts again on login
		run.onWaveStarted(2, 3000);
		run.onWaveStarted(3, 3040);
		run.finish(3100);

		assertEquals(Arrays.asList(
			new LootEvent.Split("Wave 1", 30.0),
			new LootEvent.Split("Wave 2", 24.0),
			new LootEvent.Split("Wave 3", 36.0)), run.consume().getSplits());
	}

	@Test
	public void logoutForgetsTheStartButKeepsTheWaves()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveStarted(1, 1000);
		run.onWaveStarted(2, 1050);
		run.onLogout();
		run.onWaveStarted(2, 20);
		run.finish(60);

		WaveRun.Run result = run.consume();
		assertNull(result.getStartTick());
		assertEquals(Arrays.asList(new LootEvent.Split("Wave 1", 30.0), new LootEvent.Split("Wave 2", 24.0)), result.getSplits());
	}

	@Test
	public void consumedOnce()
	{
		WaveRun run = new WaveRun("Wave");
		run.onWaveCompleted(1, 30.0, 100);
		run.consume();

		WaveRun.Run result = run.consume();
		assertNull(result.getStartTick());
		assertTrue(result.getSplits().isEmpty());
		assertNull(run.lastWave());
	}
}
