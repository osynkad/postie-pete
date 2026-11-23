package com.postiepete;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows a Tombs of Amascut raid, every mode: each challenge's time (the paths and the bosses), the raid's
 * invocation level and the player's points, so they can be sent with the reward chest's loot.
 * <p>
 * The raid has no start message, and its paths can be done in any order, so a challenge whose total is its own
 * duration (the raid's first) starts the run, and clears what's left of one that was failed.
 */
class TombsOfAmascut
{
	// "Challenge complete: Path of Het. Duration: 2:10.20. Total: 2:10.20", tags removed. The Wardens' message has
	// the raid's completion time instead of a total: "Challenge complete: The Wardens. Duration: 9:40Tombs of
	// Amascut: Expert Mode challenge completion time: 9:40. Personal best: 8:31" (RuneLite's chat commands tests).
	// The Fractured Archive's bosses use the same message, so the challenge's name is checked too.
	static final Pattern CHALLENGE_COMPLETE_PATTERN = Pattern.compile(
		"Challenge complete: (?<room>.+?)\\. Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)"
			+ "(?:\\. Total: (?<total>[0-9:]+(?:\\.[0-9]+)?))?");

	/**
	 * The raid's last challenge, which completes it.
	 */
	static final String WARDENS = "The Wardens";

	/**
	 * The challenges as the game names them in their completion message.
	 */
	static final Set<String> CHALLENGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		"Path of Apmeken", "Ba-Ba",
		"Path of Crondis", "Zebak",
		"Path of Het", "Akkha",
		"Path of Scabaras", "Kephri",
		WARDENS)));

	private final RaidSplits splits = new RaidSplits();
	private Integer invocation;
	private Integer personalPoints;

	/**
	 * Called on a game message, tags removed, with the raid level the game keeps at that time. Returns true if it
	 * was a challenge's completion, which is then added to the run's splits. The Wardens' completes the raid.
	 */
	boolean onChallengeMessage(String message, int raidLevel)
	{
		Matcher matcher = CHALLENGE_COMPLETE_PATTERN.matcher(message);
		if (!matcher.lookingAt() || !CHALLENGES.contains(matcher.group("room")))
		{
			return false;
		}
		String room = matcher.group("room");
		double duration = PostiePetePlugin.parseDuration(matcher.group("time"));
		String total = matcher.group("total");
		// Totals leave out the time between challenges, so the first one's is its own time
		if (total != null && Math.abs(PostiePetePlugin.parseDuration(total) - duration) < 0.05)
		{
			splits.onRunStarted();
			personalPoints = null;
		}
		splits.onBossCompleted(room, duration);
		if (room.equals(WARDENS))
		{
			splits.onRaidCompleted();
			invocation = raidLevel;
		}
		return true;
	}

	/**
	 * The raid's completion time in the Wardens' message, tags kept, or null if it has none.
	 */
	static LootEvent.KillTime completionTime(String message)
	{
		return PostiePetePlugin.completionTimeAfterRoom(message);
	}

	/**
	 * The completed raid's challenges, or the raid so far if its completion was missed. Consumed.
	 */
	List<LootEvent.Split> consumeSplits()
	{
		return splits.consumeSplits();
	}

	/**
	 * The completed raid's invocation level, e.g. 300, or null if its completion wasn't seen. Consumed.
	 */
	Integer consumeInvocation()
	{
		Integer result = invocation;
		invocation = null;
		return result;
	}

	/**
	 * Called when the game sends the player's points, which it does at the end of the raid only.
	 */
	void onPersonalPoints(int points)
	{
		personalPoints = points;
	}

	/**
	 * The player's points for the completed raid, or null if the game didn't send them. The game doesn't send the
	 * party's total, so it is always null. Consumed.
	 */
	LootEvent.Points consumePoints()
	{
		LootEvent.Points result = personalPoints != null ? new LootEvent.Points(null, personalPoints) : null;
		personalPoints = null;
		return result;
	}

	void reset()
	{
		splits.reset();
		invocation = null;
		personalPoints = null;
	}
}
