package com.postiepete;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * Follows a run of a wave activity: each wave's time, and the tick the run started, so they can be sent with the
 * run's loot or kill. Waves the game prints a time for (Fortis Colosseum waves, Doom of Mokhaiotl delve levels)
 * use {@link #onWaveCompleted}. Waves it only announces (Fight Caves and Inferno "Wave: N") use
 * {@link #onWaveStarted} and are timed in game ticks from one wave's start to the next.
 */
class WaveRun
{
	private final List<LootEvent.Wave> waves = new ArrayList<>();
	private Integer startTick;
	private Integer lastWave;

	// The wave being timed in ticks, which ends when the next one starts or the run is finished
	private Integer openWave;
	private int openWaveTick;

	/**
	 * Called on a wave's completion message, with the time the game printed in seconds if it could be read.
	 * Wave 1 starts a new run, which started that wave's duration before the message.
	 */
	void onWaveCompleted(int wave, Double duration, int tick)
	{
		if (wave == 1)
		{
			reset();
			if (duration != null)
			{
				startTick = tick - secondsToTicks(duration);
			}
		}
		if (duration != null)
		{
			waves.add(new LootEvent.Wave(wave, duration));
		}
		lastWave = wave;
	}

	/**
	 * Called on a wave's start message. Ends the wave before it. Wave 1 starts a new run. The same wave again
	 * (it restarts after logging out mid-wave) only restarts that wave's time.
	 */
	void onWaveStarted(int wave, int tick)
	{
		if (openWave != null && openWave == wave)
		{
			openWaveTick = tick;
			return;
		}

		if (wave == 1)
		{
			reset();
			startTick = tick;
		}
		else
		{
			finish(tick);
		}
		openWave = wave;
		openWaveTick = tick;
		lastWave = wave;
	}

	/**
	 * Ends the wave being timed in ticks, e.g. on the run's kill count message.
	 */
	void finish(int tick)
	{
		if (openWave != null)
		{
			waves.add(new LootEvent.Wave(openWave, ticksToSeconds(tick - openWaveTick)));
			openWave = null;
		}
	}

	/**
	 * Forgets the run's start tick after logging out: the game tick count isn't kept across logins, so it can't
	 * be compared with ticks after logging back in. The wave times so far are kept.
	 */
	void onLogout()
	{
		startTick = null;
	}

	/**
	 * A wave's own time, for a wave whose message shows the whole run's time (the Colosseum's wave 12): the run
	 * time less the waves before it. Null unless every wave before it was recorded, in order.
	 */
	Double ownTime(int wave, double runTime)
	{
		if (waves.size() != wave - 1)
		{
			return null;
		}
		double earlier = 0;
		for (int i = 0; i < waves.size(); i++)
		{
			if (waves.get(i).getWave() != i + 1)
			{
				return null;
			}
			earlier += waves.get(i).getDuration();
		}
		// Rounded to the hundredths the game prints, without floating point leftovers
		return Math.round((runTime - earlier) * 100) / 100.0;
	}

	/**
	 * The last wave completed or started, or null if none was seen.
	 */
	Integer lastWave()
	{
		return lastWave;
	}

	/**
	 * The run's waves and start tick, for its loot or kill. Consumed, so they can't be attached to a later one.
	 */
	Run consume()
	{
		Run run = new Run(new ArrayList<>(waves), startTick);
		reset();
		return run;
	}

	void reset()
	{
		waves.clear();
		startTick = null;
		lastWave = null;
		openWave = null;
	}

	static int secondsToTicks(double seconds)
	{
		return (int) Math.round(seconds / 0.6);
	}

	static double ticksToSeconds(int ticks)
	{
		return ticks * 6 / 10.0;
	}

	@Value
	static class Run
	{
		/**
		 * The waves seen, in order. Empty if none were seen (e.g. the plugin was turned on mid-run).
		 */
		List<LootEvent.Wave> waves;

		/**
		 * The tick the run started, or null if wave 1 wasn't seen or a logout came in between.
		 */
		Integer startTick;
	}
}
