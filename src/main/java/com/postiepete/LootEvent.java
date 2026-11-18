package com.postiepete;

import java.util.List;
import lombok.Value;

/**
 * Loot from an NPC kill, a raid or reward chest, a clue casket etc.
 */
class LootEvent extends PlayerItemsEvent
{
	/**
	 * What the loot came from: its name, kill count, kill time and personal best, and whether it was a new one.
	 */
	final Source source;

	/**
	 * Team, room times and purples for raid reward chests (currently the Fractured Archive). Null otherwise.
	 */
	final Raid raid;
	final List<Drop> drops;

	LootEvent(Common common, Source source, Raid raid, List<Drop> drops)
	{
		super(LOOT, common);
		this.source = source;
		this.raid = raid;
		this.drops = drops;
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
		 * Kill / completion duration in seconds (e.g. 83.4), or null if the game didn't report one.
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
		 * Builds the source from the Loot Tracker's name and the kill count message that came with the loot, if any.
		 */
		static Source of(String lootTrackerName, Kill kill, Integer npcId, KillTime killTime, Integer wave)
		{
			String name = kill != null ? kill.getName() : lootTrackerName;
			String lootName = lootTrackerName != null && !lootTrackerName.equals(name) ? lootTrackerName : null;
			return new Source(name, lootName, npcId, kill != null ? kill.getCount() : null,
				killTime != null ? killTime.getDuration() : null,
				killTime != null ? killTime.getPersonalBest() : null,
				killTime != null && killTime.isNewPersonalBest(),
				wave);
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
	static class Raid
	{
		/**
		 * The raid's scale: the number of players in the raid when it was completed (1 = solo,
		 * 2 = duo etc.), including the player. Null if the game didn't report the party.
		 */
		Integer teamSize;

		/**
		 * Names of everyone in the raid, including the player, in party slot order. Null if the
		 * game didn't report the party.
		 */
		List<String> team;

		/**
		 * Each boss's duration, in the order fought, e.g. Archive Guardian, Bork, Moia, Xarth, Ocellus. The
		 * Fractured Archive's path (the boss picked at each choice) is the 2nd to 4th boss. Empty if none were
		 * seen (e.g. the plugin was enabled mid-raid).
		 */
		List<Split> splits;

		/**
		 * Every purple (unique) announced in the raid with "found something special", the player's
		 * own included, in the order announced. Empty when nobody got one.
		 */
		List<Purple> purples;
	}

	@Value
	static class Purple
	{
		/**
		 * Display name of the player who got it, as the game announced it.
		 */
		String rsn;

		/**
		 * The item's name as announced, e.g. "Obligator (uncharged)".
		 */
		String item;
	}

	@Value
	static class Split
	{
		/**
		 * Boss name as the game writes it in its completion message, e.g. "Bork".
		 */
		String boss;

		/**
		 * The boss's duration in seconds, e.g. 141.0.
		 */
		double duration;
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
