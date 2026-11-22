package com.postiepete;

import java.util.List;
import lombok.Value;

/**
 * Loot from an NPC kill, a raid or reward chest, a clue casket etc.
 */
class LootEvent extends PostieEvent
{
	/**
	 * What the loot came from and how the kill went: its name, kill count, kill time, personal best and splits.
	 */
	final Source source;
	final List<Drop> drops;

	/**
	 * The player's items when the fight started, for boss kills. Null for other loot, or when sending gear is
	 * turned off.
	 */
	final PlayerItems playerItems;

	/**
	 * How many players were in the fight, for boss kills. Null for other loot and skilling bosses.
	 */
	final Team team;

	LootEvent(Common common, Source source, List<Drop> drops, PlayerItems playerItems, Team team)
	{
		super(LOOT, common);
		this.source = source;
		this.drops = drops;
		this.playerItems = playerItems;
		this.team = team;
	}

	@Value
	static class Source
	{
		/**
		 * The boss, raid or activity: the kill count message's name when one came with the loot (e.g. "Theatre of
		 * Blood: Hard Mode", "Leviathan", "Barrows chest"), otherwise the Loot Tracker's name (e.g. "Vorkath",
		 * "Reward cart (Wintertodt)", "Clue Scroll (Hard)").
		 */
		String name;

		/**
		 * The Loot Tracker's name when it differs from name, e.g. "Theatre of Blood" for every raid mode, or
		 * "The Leviathan" for "Leviathan". Null when they are the same, or when name already is the Loot
		 * Tracker's name (no kill count message came with the loot).
		 */
		String lootName;

		/**
		 * The NPC's ID for kills (e.g. 8061 for Vorkath), or null for non-NPC sources.
		 */
		Integer npcId;

		/**
		 * The kill count after this kill, from the kill count message. Null when no kill count message came
		 * with the loot; name is then the Loot Tracker's name.
		 */
		Integer killCount;

		/**
		 * Kill / completion duration in seconds (e.g. 83.4): the game's, or for bosses it prints none for, the
		 * plugin's own time from the first hit. Null if neither is known.
		 */
		Double duration;

		/**
		 * The personal best in seconds as the game reported it with the duration: the duration itself for a new
		 * personal best, otherwise the "Personal best:" time. Null when the game didn't report one.
		 */
		Double personalBest;

		/**
		 * True when the game's kill time message said "(new personal best)", false otherwise. A time that ties the
		 * personal best is not a new one.
		 */
		boolean newPersonalBest;

		/**
		 * How far the run got when the loot was claimed, for activities where loot can be claimed early: the
		 * Fortis Colosseum wave (1-12) or the Doom of Mokhaiotl delve level. Null otherwise.
		 */
		Integer wave;

		/**
		 * The run's parts in the order played, for the Fortis Colosseum, Doom of Mokhaiotl, the Fight Caves, the
		 * Inferno and the Fractured Archive. Null for all other loot, and when none were seen (e.g. the plugin
		 * was turned on mid-run).
		 */
		List<Split> splits;

		/**
		 * Builds the source from the Loot Tracker's name and the kill count message that came with the loot, if
		 * any. Empty splits are sent as null.
		 */
		static Source of(String lootTrackerName, Kill kill, Integer npcId, KillTime killTime, Integer wave,
			List<Split> splits)
		{
			String name = kill != null ? kill.getName() : lootTrackerName;
			String lootName = lootTrackerName != null && !lootTrackerName.equals(name) ? lootTrackerName : null;
			return new Source(name, lootName, npcId, kill != null ? kill.getCount() : null,
				killTime != null ? killTime.getDuration() : null,
				killTime != null ? killTime.getPersonalBest() : null,
				killTime != null && killTime.isNewPersonalBest(),
				wave,
				splits != null && !splits.isEmpty() ? splits : null);
		}
	}

	/**
	 * A kill count message: the boss or raid as it names it and the count. Sent as the source's name and killCount.
	 */
	@Value
	static class Kill
	{
		/**
		 * The boss or raid as the kill count message names it, e.g. "TzKal-Zuk", "Theatre of Blood: Hard Mode",
		 * "Chambers of Xeric Challenge Mode", "Wintertodt" or "Barrows chest".
		 */
		String name;

		/**
		 * The kill count after this kill, e.g. 40.
		 */
		int count;
	}

	/**
	 * A kill or completion time message: the duration, the personal best it reported and whether it was a new one.
	 */
	@Value
	static class KillTime
	{
		double duration;

		/**
		 * The duration for a new personal best, the "Personal best:" time otherwise, or null if neither was shown.
		 */
		Double personalBest;

		/**
		 * True when the message said "(new personal best)".
		 */
		boolean newPersonalBest;
	}

	@Value
	static class Split
	{
		/**
		 * The part of the run: "Wave 3" (Colosseum, Fight Caves, Inferno), "Level 22" (Doom delve level), or the
		 * boss as the game writes it in its completion message, e.g. "Bork" (Fractured Archive).
		 */
		String name;

		/**
		 * The part's duration in seconds, e.g. 141.0. What it covers depends on the content: see the README.
		 */
		double duration;
	}

	@Value
	static class Team
	{
		/**
		 * Players in the team, including the sender (1 = solo). For raids, the game's party size at the raid's
		 * start (CoX: its completion message). For other
		 * bosses, the sender plus every other player who was near them at some point during the fight.
		 */
		int size;
	}

	@Value
	static class Drop
	{
		int itemId;

		/**
		 * Item name from the game's item data, e.g. "Dragon bones". Not unique, use itemId as the key.
		 */
		String itemName;
		int quantity;

		/**
		 * GE value of one item at the time of the drop, from the OSRS Wiki real-time prices
		 * that RuneLite loads. Untradeable variants RuneLite knows about (e.g. Sanguine Torva) are
		 * valued as their tradeable item. 0 for other untradeable items, or if prices haven't loaded.
		 */
		long value;
	}
}
