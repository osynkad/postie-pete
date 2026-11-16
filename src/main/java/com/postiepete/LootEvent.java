package com.postiepete;

import java.util.List;
import lombok.Value;

/**
 * Loot from an NPC kill, a raid or reward chest, a clue casket etc.
 */
class LootEvent extends PostieEvent
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
	final List<Drop> drops;

	LootEvent(Common common, String source, Integer npcId, Double duration, boolean personalBest, Integer stage,
		List<Drop> drops)
	{
		super(LOOT, common);
		this.source = source;
		this.npcId = npcId;
		this.duration = duration;
		this.personalBest = personalBest;
		this.stage = stage;
		this.drops = drops;
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
