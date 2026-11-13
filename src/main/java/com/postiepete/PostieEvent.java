package com.postiepete;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * Every event sent to the API has this same shape. Fields that don't apply to an
 * event type are sent as null.
 */
@Value
@Builder
class PostieEvent
{
	static final String LOOT = "loot";
	static final String COLLECTION_LOG = "collection_log";
	static final String COMBAT_ACHIEVEMENT = "combat_achievement";

	/**
	 * "loot", "collection_log" or "combat_achievement".
	 */
	String eventType;
	String timestamp;
	String rsn;

	/**
	 * RuneLite account hash: stable per account, unaffected by name changes. Sent as a
	 * string because it is a 64-bit value that JavaScript numbers can't represent exactly.
	 */
	String accountHash;

	/**
	 * Where the loot came from: the NPC name for kills (e.g. "Vorkath"), or the Loot
	 * Tracker's name for other sources (e.g. "Fractured Archive", "Clue Scroll (Hard)").
	 */
	String source;

	/**
	 * Kill / completion duration in seconds (e.g. 83.4), or null if the game didn't report one.
	 */
	Double duration;
	Boolean personalBest;
	List<Drop> drops;
	PlayerItems playerItems;

	/**
	 * Collection log: item name as shown in the chat message, e.g. "Abyssal whip".
	 */
	String itemName;

	/**
	 * Combat achievement: easy, medium, hard, elite, master or grandmaster.
	 */
	String tier;

	/**
	 * Combat achievement: task name, e.g. "Noxious Foe".
	 */
	String task;

	@Value
	static class Drop
	{
		int itemId;
		int quantity;
	}
}
