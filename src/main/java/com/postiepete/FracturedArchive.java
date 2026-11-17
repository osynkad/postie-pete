package com.postiepete;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows a Fractured Archive run: each boss's duration, the team when the raid was completed, and
 * every purple (unique) announced in the raid, so they can be sent with the raid's loot. The path
 * (the boss picked at each of the three choices) is the 2nd to 4th boss in the splits.
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

	// "Dear no one found something special: Obligator (uncharged)" (tags removed), the same wording as
	// Theatre of Blood and Tombs of Amascut. Purples can be announced after the completion message.
	private static final Pattern PURPLE_PATTERN = Pattern.compile(
		"(?<rsn>.+?) found something special: (?<item>.+?)\\.?");

	private final List<LootEvent.Split> splits = new ArrayList<>();
	private final List<LootEvent.Purple> purples = new ArrayList<>();
	private LootEvent.Raid completedRaid;

	// True from a run's first boss (or first completed boss) until its loot is claimed, so purples
	// announced in other content aren't picked up
	private boolean inRun;

	/**
	 * Called on the first boss's start message. Clears what's left of an earlier, failed run.
	 */
	void onRunStarted()
	{
		clearRun();
		purples.clear();
		inRun = true;
	}

	/**
	 * Called on a boss's completion message, with the boss name as the game writes it and its duration in seconds.
	 */
	void onBossCompleted(String boss, double duration)
	{
		splits.add(new LootEvent.Split(boss, duration));
		inRun = true;
	}

	/**
	 * Called for every game message (tags removed). Returns true if it announced a purple in the run.
	 * "You" is replaced with the given local player name.
	 */
	boolean onMessage(String message, String localName)
	{
		LootEvent.Purple purple = parsePurple(message, localName);
		if (purple == null || !(inRun || completedRaid != null))
		{
			return false;
		}
		purples.add(purple);
		return true;
	}

	/**
	 * Parses a "found something special" message (tags removed) into who got which item, or null.
	 */
	static LootEvent.Purple parsePurple(String message, String localName)
	{
		Matcher matcher = PURPLE_PATTERN.matcher(message);
		if (!matcher.matches())
		{
			return null;
		}
		String rsn = matcher.group("rsn");
		if (rsn.equals("You") && localName != null)
		{
			rsn = localName;
		}
		return new LootEvent.Purple(rsn, matcher.group("item"));
	}

	/**
	 * Keeps the run's details for the reward chest, which is opened some time later.
	 */
	void onRaidCompleted(Integer teamSize, List<String> team)
	{
		completedRaid = currentRaid(teamSize, team);
		clearRun();
	}

	/**
	 * The completed run's details, for the reward chest's loot. Uses the given team and the run so
	 * far if the completion message was missed. Consumed, so they can't be attached to later loot.
	 */
	LootEvent.Raid consumeRaid(Integer currentTeamSize, List<String> currentTeam)
	{
		LootEvent.Raid raid = completedRaid != null ? completedRaid : currentRaid(currentTeamSize, currentTeam);
		// Purples announced after the completion message are included too
		raid = new LootEvent.Raid(raid.getTeamSize(), raid.getTeam(), raid.getSplits(),
			new ArrayList<>(purples));
		reset();
		return raid;
	}

	void reset()
	{
		completedRaid = null;
		clearRun();
		purples.clear();
		inRun = false;
	}

	private void clearRun()
	{
		splits.clear();
	}

	private LootEvent.Raid currentRaid(Integer teamSize, List<String> team)
	{
		return new LootEvent.Raid(teamSize, team, new ArrayList<>(splits), new ArrayList<>(purples));
	}
}
