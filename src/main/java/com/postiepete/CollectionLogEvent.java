package com.postiepete;

/**
 * A new item added to the collection log.
 */
class CollectionLogEvent extends PostieEvent
{
	/**
	 * Item name as shown in the chat message, e.g. "Abyssal whip".
	 */
	final String itemName;

	CollectionLogEvent(Common common, String itemName)
	{
		super(COLLECTION_LOG, common);
		this.itemName = itemName;
	}
}
