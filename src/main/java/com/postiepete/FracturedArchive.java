package com.postiepete;

import java.util.ArrayList;
import java.util.List;

/**
 * Follows a Fractured Archive run: each boss's duration, so they can be sent with the raid's loot.
 * The path (the boss picked at each of the three choices) is the 2nd to 4th boss in the splits.
 */
class FracturedArchive
{
	/**
	 * The Loot Tracker's name for the raid's reward chest.
	 */
	static final String LOOT_SOURCE = "Fractured Archive";

	/**
	 * Start of the kill count message sent when the raid is completed, before the chest is opened.
	 */
	static final String COMPLETION_MESSAGE = "Your completed Fractured Archive count is";

	/**
	 * Sent when the first boss starts, at the start of every run.
	 */
	static final String RUN_START_MESSAGE = "Challenge started: Archive Guardian.";

	private final List<LootEvent.Split> splits = new ArrayList<>();
	private List<LootEvent.Split> completedSplits;
	private Integer startTick;
	private Integer completedStartTick;

	/**
	 * Called on the first boss's start message. Clears what's left of an earlier, failed run.
	 */
	void onRunStarted(int tick)
	{
		splits.clear();
		startTick = tick;
	}

	/**
	 * Called on a boss's completion message, with the boss name as the game writes it and its duration in seconds.
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
		completedStartTick = startTick;
		splits.clear();
		startTick = null;
	}

	/**
	 * The tick the completed run started, or the current run if the completion message was missed. Null if the
	 * start wasn't seen. Read before {@link #consumeSplits}.
	 */
	Integer startTick()
	{
		return completedSplits != null ? completedStartTick : startTick;
	}

	/**
	 * Forgets the start tick after logging out: the game tick count isn't kept across logins.
	 */
	void onLogout()
	{
		startTick = null;
		completedStartTick = null;
	}

	/**
	 * The completed run's boss splits, for the reward chest's loot. Uses the run so far if the completion
	 * message was missed. Empty if none were seen. Consumed, so they can't be attached to later loot.
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
		startTick = null;
		completedStartTick = null;
	}
}
