package com.postiepete;

import java.util.function.Supplier;

/**
 * The player's items at the start of the current trip: one snapshot, taken on the player's first landed hit of
 * the trip, so it shows the gear setup they brought before any supplies were used. Every boss kill in the trip
 * is sent with it. A trip ends when the bank is opened, after logging out, or after a long break from fighting.
 */
class TripGear
{
	/**
	 * A trip with no hit for this long is over, so the next hit starts a new one: 30 minutes, longer than the
	 * gaps between fights in a raid or at Barrows.
	 */
	static final int IDLE_TICKS = 3000;

	private PlayerItems items;
	private int lastHitTick;

	/**
	 * Called on each of the player's own hitsplats on an NPC. The first hit of a trip takes the snapshot.
	 */
	void onHit(int tick, Supplier<PlayerItems> capture)
	{
		if (items == null || tick - lastHitTick > IDLE_TICKS)
		{
			items = capture.get();
		}
		lastHitTick = tick;
	}

	/**
	 * The items at the start of the current trip, or null if there is no trip going on.
	 */
	PlayerItems get(int tick)
	{
		return items != null && tick - lastHitTick <= IDLE_TICKS ? items : null;
	}

	/**
	 * Ends the trip, e.g. when the bank is opened to gear up for the next one.
	 */
	void end()
	{
		items = null;
	}
}
