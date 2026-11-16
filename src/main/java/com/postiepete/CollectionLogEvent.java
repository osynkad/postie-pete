package com.postiepete;

/**
 * A new item added to the collection log.
 */
class CollectionLogEvent extends PostieEvent
{
	/**
	 * Item name from the game's item data, e.g. "Abyssal whip". Not unique: several collection
	 * log items can share a name (e.g. the chompy bird hats), use itemId to tell them apart.
	 */
	final String itemName;

	/**
	 * Exact item ID of the new collection log item.
	 */
	final int itemId;

	CollectionLogEvent(Common common, String itemName, int itemId)
	{
		super(COLLECTION_LOG, common);
		this.itemName = itemName;
		this.itemId = itemId;
	}
}
