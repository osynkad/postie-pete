package com.postiepete;

import java.util.HashMap;
import java.util.Map;

/**
 * The team of each raid, read when the raid is completed and kept until its reward chest is opened. Keyed
 * by the Loot Tracker's name for the raid's chest.
 */
class RaidTeams
{
	static final String CHAMBERS_OF_XERIC = "Chambers of Xeric";
	static final String THEATRE_OF_BLOOD = "Theatre of Blood";
	static final String TOMBS_OF_AMASCUT = "Tombs of Amascut";

	/**
	 * Sent to the raid's party when a Chambers of Xeric raid is completed.
	 */
	static final String COX_COMPLETE_MESSAGE = "Congratulations - your raid is complete!";

	private final Map<String, LootEvent.Team> teams = new HashMap<>();

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

	/**
	 * Called when a raid is completed, with its chest's name and team size (ignored when unknown or 0).
	 */
	void onCompleted(String chestName, Integer size)
	{
		onCompleted(chestName, size, null);
	}

	/**
	 * Called when a raid is completed, with its chest's name, team size (ignored when unknown or 0) and the
	 * party's scaling setting, for the Chambers of Xeric.
	 */
	void onCompleted(String chestName, Integer size, Integer scaling)
	{
		if (chestName != null && size != null && size > 0)
		{
			teams.put(chestName, new LootEvent.Team(size, scaling));
		}
	}

	/**
	 * The team for the raid's chest, or null if the raid's completion wasn't seen. Consumed.
	 */
	LootEvent.Team takeTeam(String chestName)
	{
		return chestName != null ? teams.remove(chestName) : null;
	}

	void reset()
	{
		teams.clear();
	}
}
