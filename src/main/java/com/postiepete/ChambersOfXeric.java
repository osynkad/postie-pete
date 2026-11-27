package com.postiepete;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows a Chambers of Xeric raid, Challenge Mode included: each room's and level's time, the Great Olm's,
 * and the raid's points, so they can be sent with the reward chest's loot, which is opened some time later.
 * <p>
 * The game tells the party the time since the raid began at the end of each level, and the raid's time on its
 * completion, so each part's own time is the difference from the one before.
 */
class ChambersOfXeric
{
	/**
	 * Sent to the raid's party when the raid starts.
	 */
	static final String RAID_START_MESSAGE = "The raid has begun!";

	/**
	 * Sent to the raid's party when the raid is completed.
	 */
	static final String RAID_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";

	// "Upper level complete! Duration: 7:01", tags removed, sent to the raid's party: the time since the raid
	// began. The CoX timers hub plugin reads the same message, adding the level's own time to it.
	static final Pattern LEVEL_COMPLETE_PATTERN = Pattern.compile(
		"(?<level>\\w+) level complete! Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	// The raid's time in its completion message, tags removed: "...Team size: 3 players Duration: 21:30 Personal
	// best: 20:00" or "... Duration: 19:45 (new personal best)". Same as RuneLite's chat commands plugin reads.
	// The groups are the ones PostiePetePlugin.parseKillTime reads.
	static final Pattern COMPLETION_TIME_PATTERN = Pattern.compile(
		"Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)(?:(?<newpb> \\(new personal best\\))"
			+ "|\\.? ?Personal best: (?<pb>[0-9:]+(?:\\.[0-9]+)?))?");

	/**
	 * The split after the last level: the time from its end to the raid's completion.
	 */
	static final String OLM = "Great Olm";

	private final RaidSplits splits = new RaidSplits();

	// The raid's time at the end of the last level, 0 at the start. Null when unknown: the raid's start wasn't
	// seen (e.g. the plugin was turned on mid-raid), so a level's own time can't be worked out.
	private Double lastLevelEnd;
	private boolean levelSeen;
	private LootEvent.Points points;

	// The raid timer's ticks when the next room's time started: at the raid's or the level's start, or the last
	// room's completion. Null when unknown.
	private Integer roomStartTicks;

	/**
	 * The raid's time and personal best in its completion message, tags removed, or null if it shows none.
	 */
	static LootEvent.KillTime completionTime(String message)
	{
		Matcher matcher = COMPLETION_TIME_PATTERN.matcher(message);
		return matcher.find() ? PostiePetePlugin.parseKillTime(matcher) : null;
	}

	/**
	 * Called on the raid's start message, with the tick it came on.
	 */
	void onRaidStarted(int tick)
	{
		splits.onRunStarted(tick);
		lastLevelEnd = 0.0;
		levelSeen = false;
		roomStartTicks = 0;
	}

	/**
	 * Called when a room is completed, with the raid timer's ticks at that time. Added to the splits in the order
	 * completed, before its level's. The room's time runs from the last room's completion or the level's start, so
	 * it includes getting to the room.
	 */
	void onRoomCompleted(String room, int raidTicks)
	{
		if (roomStartTicks != null && raidTicks >= roomStartTicks)
		{
			splits.onBossCompleted(room, (raidTicks - roomStartTicks) * 6 / 10.0);
		}
		roomStartTicks = raidTicks;
	}

	/**
	 * Called on a level's completion message, with the level as the game names it (e.g. "Upper"), the time since
	 * the raid began in seconds as the message shows it, and the raid timer's ticks.
	 */
	void onLevelCompleted(String level, double raidTime, int raidTicks)
	{
		if (lastLevelEnd != null && raidTime >= lastLevelEnd)
		{
			splits.onBossCompleted(level + " level", raidTime - lastLevelEnd);
		}
		lastLevelEnd = raidTime;
		levelSeen = true;
		roomStartTicks = raidTicks;
	}

	/**
	 * Called on the raid's completion message, with the raid's time in seconds if it showed one, and the party's
	 * and the player's points.
	 */
	void onRaidCompleted(Double raidTime, int totalPoints, int personalPoints)
	{
		if (raidTime != null && levelSeen && lastLevelEnd != null && raidTime >= lastLevelEnd)
		{
			splits.onBossCompleted(OLM, raidTime - lastLevelEnd);
		}
		splits.onRaidCompleted();
		points = totalPoints > 0 ? new LootEvent.Points(totalPoints, personalPoints) : null;
		lastLevelEnd = null;
		levelSeen = false;
		roomStartTicks = null;
	}

	/**
	 * The completed raid's splits, or the raid so far if its completion was missed. Consumed.
	 */
	List<LootEvent.Split> consumeSplits()
	{
		return splits.consumeSplits();
	}

	/**
	 * The tick the raid started, or null if its start wasn't seen. Read before {@link #consumeSplits}.
	 */
	Integer startTick()
	{
		return splits.startTick();
	}

	void onLogout()
	{
		splits.onLogout();
	}

	/**
	 * The completed raid's points, or null if its completion wasn't seen. Consumed.
	 */
	LootEvent.Points consumePoints()
	{
		LootEvent.Points result = points;
		points = null;
		return result;
	}

	void reset()
	{
		splits.reset();
		lastLevelEnd = null;
		levelSeen = false;
		points = null;
		roomStartTicks = null;
	}
}
