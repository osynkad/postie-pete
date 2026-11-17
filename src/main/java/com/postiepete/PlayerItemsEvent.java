package com.postiepete;

/**
 * An event that also carries the player's gear and inventory when it happened.
 */
abstract class PlayerItemsEvent extends PostieEvent
{
	final PlayerItems playerItems;

	PlayerItemsEvent(String eventType, Common common)
	{
		super(eventType, common);
		this.playerItems = common.getPlayerItems();
	}
}
