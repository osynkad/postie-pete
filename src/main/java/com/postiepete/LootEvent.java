package com.postiepete;

import java.util.List;
import lombok.Value;

/**
 * Loot from an NPC kill, a raid or reward chest, a clue casket etc.
 */
class LootEvent extends PlayerItemsEvent
{
	/**
	 * Where the loot came from: the NPC name for kills (e.g. "Vorkath"), or the Loot
	 * Tracker's name for other sources (e.g. "Fractured Archive", "Clue Scroll (Hard)").
	 */
	final String source;

	/**
	 * The NPC's ID for kills (e.g. 8061 for Vorkath), or null for non-NPC sources.
	 */
	final Integer npcId;

	/**
	 * Kill / completion duration in seconds (e.g. 83.4), or null if the game didn't report one.
	 */
	final Double duration;
	final boolean personalBest;

	/**
	 * How far the run got when the loot was claimed, for activities where loot can be claimed
	 * early: the Fortis Colosseum wave (1-12) or the Doom of Mokhaiotl delve level. Null otherwise.
	 */
	final Integer stage;

	/**
	 * Team and path details for raid reward chests (currently the Fractured Archive). Null otherwise.
	 */
	final Raid raid;

	/**
	 * The game's kill count message that came with the loot, e.g. "Theatre of Blood: Hard Mode" 40. Names the
	 * boss or raid (with its mode) where the source can't: raid chests are named without their mode, and some
	 * bosses' loot comes from another NPC. Null when no kill count message came with the loot.
	 */
	final Kill kill;
	final List<Drop> drops;

	LootEvent(Common common, String source, Integer npcId, Double duration, boolean personalBest, Integer stage,
		Raid raid, Kill kill, List<Drop> drops)
	{
		super(LOOT, common);
		this.source = source;
		this.npcId = npcId;
		this.duration = duration;
		this.personalBest = personalBest;
		this.stage = stage;
		this.raid = raid;
		this.kill = kill;
		this.drops = drops;
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
		 * Fractured Archive: the boss the team picked at each of the three path choices, in order:
		 * the 2nd boss ("Ezekiel" or "Bork and the Smorks"), the 3rd ("Moia" or "Dreadnoth the Jellied")
		 * and the 4th ("The Dagon'hai" or "Xarth"). Always 3 entries, an entry is null if the boss
		 * wasn't seen being fought (e.g. the plugin was enabled mid-raid).
		 */
		List<String> path;

		/**
		 * Each boss's duration, in the order fought, e.g. Archive Guardian, Bork, Moia, Xarth, Ocellus.
		 * Empty if none were seen (e.g. the plugin was enabled mid-raid).
		 */
		List<Split> splits;

		/**
		 * Every purple (unique) announced in the raid with "found something special", the player's
		 * own included, in the order announced. Empty when nobody got one.
		 */
		List<Purple> purples;
	}

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
		 * Boss name as the game writes it in its completion message, e.g. "Bork". Can differ from the
		 * path's names, which are the OSRS Wiki's (e.g. "Bork and the Smorks").
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
