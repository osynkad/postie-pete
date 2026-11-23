package com.postiepete;

import java.util.ArrayList;
import java.util.List;

/**
 * Follows a raid run: each boss's or room's duration, so they can be sent with the raid's loot. Used for the
 * Fractured Archive and the Theatre of Blood.
 */
class RaidSplits
{
	private final List<LootEvent.Split> splits = new ArrayList<>();
	private List<LootEvent.Split> completedSplits;

	/**
	 * Called at the start of every run. Clears what's left of an earlier, failed run.
	 */
	void onRunStarted()
	{
		splits.clear();
	}

	/**
	 * Called on a boss's or room's completion message, with its name as the game writes it and its duration in
	 * seconds.
	 */
	void onBossCompleted(String boss, double duration)
	{
		splits.add(new LootEvent.Split(boss, duration));
	}

	/**
	 * Keeps the run's splits for the reward chest, which is opened some time later.
	 */
	void onRaidCompleted()
	{
		completedSplits = new ArrayList<>(splits);
		splits.clear();
	}

	/**
	 * The completed run's splits, for the reward chest's loot. Uses the run so far if the completion wasn't
	 * seen. Empty if none were seen. Consumed, so they can't be attached to later loot.
	 */
	List<LootEvent.Split> consumeSplits()
	{
		List<LootEvent.Split> result = completedSplits != null ? completedSplits : new ArrayList<>(splits);
		reset();
		return result;
	}

	void reset()
	{
		completedSplits = null;
		splits.clear();
	}
}
