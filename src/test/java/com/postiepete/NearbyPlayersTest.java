package com.postiepete;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class NearbyPlayersTest
{
	@Test
	public void countsEachPlayerNearDuringTheFightOnce()
	{
		NearbyPlayers nearby = new NearbyPlayers();
		for (int tick = 100; tick <= 200; tick++)
		{
			nearby.onNear("Alice", tick);
		}
		nearby.onNear("Bob", 150);
		nearby.onNear("Bob", 151);
		// Bob comes back later
		nearby.onNear("Bob", 190);

		assertEquals(2, nearby.count(100, 200));
	}

	@Test
	public void countsAPlayerWhoLeftMidFight()
	{
		NearbyPlayers nearby = new NearbyPlayers();
		nearby.onNear("Alice", 100);
		nearby.onNear("Alice", 101);

		assertEquals(1, nearby.count(90, 300));
	}

	@Test
	public void ignoresPlayersOnlyNearOutsideTheFight()
	{
		NearbyPlayers nearby = new NearbyPlayers();
		// Near before the fight started
		nearby.onNear("Alice", 50);
		// Arrived after the kill
		nearby.onNear("Bob", 400);

		assertEquals(0, nearby.count(100, 300));
	}

	@Test
	public void countsAPlayerNearFromBeforeTheFight()
	{
		NearbyPlayers nearby = new NearbyPlayers();
		for (int tick = 80; tick <= 120; tick++)
		{
			nearby.onNear("Alice", tick);
		}

		assertEquals(1, nearby.count(100, 300));
	}

	@Test
	public void pruningDropsOldRanges()
	{
		NearbyPlayers nearby = new NearbyPlayers();
		nearby.onNear("Alice", 100);
		nearby.onNear("Bob", 100 + NearbyPlayers.MAX_AGE_TICKS);

		nearby.prune(100 + NearbyPlayers.MAX_AGE_TICKS + 1);

		assertEquals(1, nearby.count(0, 100 + NearbyPlayers.MAX_AGE_TICKS));
	}
}
