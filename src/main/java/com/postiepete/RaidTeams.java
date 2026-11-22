package com.postiepete;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The team size of each raid, read when the raid is completed and kept until its reward chest is opened, and
 * the Chambers of Xeric's start, which the game doesn't otherwise report with the chest. Keyed by the Loot
 * Tracker's name for the raid's chest.
 */
class RaidTeams
{
	static final String CHAMBERS_OF_XERIC = "Chambers of Xeric";
	static final String THEATRE_OF_BLOOD = "Theatre of Blood";
	static final String TOMBS_OF_AMASCUT = "Tombs of Amascut";

	/**
	 * Sent to the raid's party when a Chambers of Xeric raid starts and when it is completed.
	 */
	static final String COX_START_MESSAGE = "The raid has begun!";
	static final String COX_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";

	// "Congratulations - your raid is complete!Team size: 3 players Duration: 21:30 ...", tags removed: the same
	// team sizes RuneLite's chat commands plugin reads. Large teams can show as e.g. "24+ players".
	static final Pattern COX_TEAM_SIZE_PATTERN = Pattern.compile("Team size: (?:(?<size>\\d+)\\+? players?|(?<solo>Solo))");

	private final Map<String, Integer> sizes = new HashMap<>();
	private Integer coxStartTick;

	/**
	 * The Loot Tracker's chest name for a raid's kill count message name, e.g. "Theatre of Blood" for "Theatre of
	 * Blood: Hard Mode", or null if the name isn't a raid with a party.
	 */
	static String chestName(String killName)
	{
		if (killName == null)
		{
			return null;
		}
		for (String raid : new String[]{CHAMBERS_OF_XERIC, THEATRE_OF_BLOOD, TOMBS_OF_AMASCUT, FracturedArchive.LOOT_SOURCE})
		{
			if (killName.startsWith(raid))
			{
				return raid;
			}
		}
		return null;
	}

	/**
	 * The team size in a Chambers of Xeric completion message, tags removed, or null if it shows none.
	 */
	static Integer coxTeamSize(String message)
	{
		Matcher matcher = COX_TEAM_SIZE_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return null;
		}
		return matcher.group("solo") != null ? 1 : Integer.valueOf(matcher.group("size"));
	}

	/**
	 * The number of occupied party slots, from the game's per-slot values (non-zero when a player is in it).
	 */
	static int occupiedSlots(int... slots)
	{
		int count = 0;
		for (int slot : slots)
		{
			if (slot > 0)
			{
				count++;
			}
		}
		return count;
	}

	void onCoxStarted(int tick)
	{
		coxStartTick = tick;
		sizes.remove(CHAMBERS_OF_XERIC);
	}

	/**
	 * Called when a raid is completed, with its chest's name and team size (ignored when unknown or 0).
	 */
	void onCompleted(String chestName, Integer size)
	{
		if (chestName != null && size != null && size > 0)
		{
			sizes.put(chestName, size);
		}
	}

	/**
	 * The team size for the raid's chest, or null if the raid's completion wasn't seen. Consumed.
	 */
	Integer takeSize(String chestName)
	{
		return chestName != null ? sizes.remove(chestName) : null;
	}

	/**
	 * The tick the Chambers of Xeric raid started, or null if not seen. Consumed by the raid's chest.
	 */
	Integer takeCoxStartTick()
	{
		Integer tick = coxStartTick;
		coxStartTick = null;
		return tick;
	}

	/**
	 * Forgets the start tick after logging out: the game tick count isn't kept across logins.
	 */
	void onLogout()
	{
		coxStartTick = null;
	}

	void reset()
	{
		sizes.clear();
		coxStartTick = null;
	}
}
