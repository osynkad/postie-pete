package com.postiepete;

import java.util.ArrayList;
import java.util.List;

/**
 * Follows a raid run: each boss's or room's duration, so they can be sent with the raid's loot, and the tick the
 * run started, to add up the player's damage in it. Used for the Fractured Archive, the Theatre of Blood, the
 * Tombs of Amascut and the Chambers of Xeric.
 */
class RaidSplits
{
	private final List<LootEvent.Split> splits = new ArrayList<>();
	private List<LootEvent.Split> completedSplits;
	private Integer startTick;
	private Integer completedStartTick;

	/**
	 * Called at the start of every run, with the tick it started if known. Clears what's left of an earlier,
	 * failed run.
	 */
	void onRunStarted(Integer startTick)
	{
		splits.clear();
		this.startTick = startTick;
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
	 * Keeps the run's splits and start for the reward chest, which is opened some time later.
	 */
	void onRaidCompleted()
	{
		completedSplits = new ArrayList<>(splits);
		completedStartTick = startTick;
		splits.clear();
		startTick = null;
	}

	/**
	 * The tick the completed run started, or the run so far's if the completion wasn't seen. Null if the start
	 * wasn't seen. Read before {@link #consumeSplits}, which forgets it.
	 */
	Integer startTick()
	{
		return completedSplits != null ? completedStartTick : startTick;
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

	/**
	 * Forgets the run's start after logging out: the game tick count isn't kept across logins. The splits are kept.
	 */
	void onLogout()
	{
		startTick = null;
		completedStartTick = null;
	}

	void reset()
	{
		completedSplits = null;
		splits.clear();
		startTick = null;
		completedStartTick = null;
	}
}
