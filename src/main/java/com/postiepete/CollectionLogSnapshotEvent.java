package com.postiepete;

import java.util.List;
import lombok.Value;

/**
 * Every item in the player's collection log, read when the collection log is opened. Like every
 * snapshot, it carries no player items.
 */
class CollectionLogSnapshotEvent extends PostieEvent
{
	/**
	 * Obtained items with the obtained count shown in the collection log, in no particular order.
	 */
	final List<Item> items;

	/**
	 * Unique slots obtained according to the game's own counter. An item that the collection log
	 * lists on two pages under different IDs (e.g. Prospector helmet) counts once here.
	 */
	final int obtainedSlots;

	CollectionLogSnapshotEvent(Common common, List<Item> items, int obtainedSlots)
	{
		super(COLLECTION_LOG_SNAPSHOT, common);
		this.items = items;
		this.obtainedSlots = obtainedSlots;
	}

	@Value
	static class Item
	{
		int itemId;
		int quantity;
	}
}
