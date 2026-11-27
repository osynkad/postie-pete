package com.postiepete;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows a Theatre of Blood raid, every mode: each room's time, so they can be sent with the reward chest's loot.
 * <p>
 * The raid has no start message, so the first room's completion starts the run, and clears what's left of one that
 * was wiped.
 */
class TheatreOfBlood
{
	// "Wave 'The Maiden of Sugadinti' (Normal Mode) complete!Duration: 2:05.40Total: 2:05.40", tags removed: a
	// room's completion. Verzik's room is "The Final Challenge".
	static final Pattern ROOM_COMPLETE_PATTERN = Pattern.compile(
		"Wave '(?<room>.+?)' \\([^)]*\\) complete!Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	/**
	 * The first room. The raid has no start message, so its completion starts the run.
	 */
	static final String FIRST_ROOM = "The Maiden of Sugadinti";

	private final RaidSplits splits = new RaidSplits();

	/**
	 * Called on a game message, tags removed, with the tick it came on. Returns true if it was a room's
	 * completion, which is then added to the run's splits. The first room's starts the run, which started that
	 * room's time before.
	 */
	boolean onRoomMessage(String message, int tick)
	{
		Matcher matcher = ROOM_COMPLETE_PATTERN.matcher(message);
		if (!matcher.lookingAt())
		{
			return false;
		}
		String room = matcher.group("room");
		double duration = PostiePetePlugin.parseDuration(matcher.group("time"));
		if (room.equals(FIRST_ROOM))
		{
			splits.onRunStarted(tick - WaveRun.secondsToTicks(duration));
		}
		splits.onBossCompleted(room, duration);
		return true;
	}

	/**
	 * The raid's completion time in a room message, tags kept, or null if it has none. Verzik's room message ends
	 * with it, after the room's own duration (seen in game): "Wave 'The Final Challenge' (Normal Mode)
	 * complete!&lt;br&gt;Duration: &lt;col=ff0000&gt;5:18.00&lt;/col&gt;&lt;br&gt;Theatre of Blood completion time:
	 * &lt;col=ff0000&gt;18:23.40&lt;/col&gt;. Personal best: 16:00.60".
	 */
	static LootEvent.KillTime completionTime(String message)
	{
		return PostiePetePlugin.completionTimeAfterRoom(message);
	}

	/**
	 * The run so far. The chest is in Verzik's room, so no new run can start before it's opened. Consumed.
	 */
	List<LootEvent.Split> consumeSplits()
	{
		return splits.consumeSplits();
	}

	/**
	 * The tick the run started, or null if its first room wasn't seen. Read before {@link #consumeSplits}.
	 */
	Integer startTick()
	{
		return splits.startTick();
	}

	void onLogout()
	{
		splits.onLogout();
	}

	void reset()
	{
		splits.reset();
	}
}
