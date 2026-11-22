package com.postiepete;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import java.util.Collections;
import org.junit.Test;

public class TripGearTest
{
	private static PlayerItems items(int weaponId)
	{
		return new PlayerItems(
			Collections.singletonMap("WEAPON", new PlayerItems.SlotItem(weaponId, 1)),
			Collections.emptyMap(), null, null);
	}

	@Test
	public void keepsTheFirstHitsItemsForTheWholeTrip()
	{
		TripGear trip = new TripGear();
		PlayerItems start = items(20997);
		trip.onHit(100, () -> start);
		trip.onHit(500, () -> items(4151));

		assertSame(start, trip.get(600));
	}

	@Test
	public void noTripBeforeTheFirstHit()
	{
		assertNull(new TripGear().get(100));
	}

	@Test
	public void bankingStartsANewTrip()
	{
		TripGear trip = new TripGear();
		trip.onHit(100, () -> items(20997));
		trip.end();

		assertNull(trip.get(110));
		PlayerItems next = items(4151);
		trip.onHit(200, () -> next);
		assertSame(next, trip.get(210));
	}

	@Test
	public void aLongBreakStartsANewTrip()
	{
		TripGear trip = new TripGear();
		trip.onHit(100, () -> items(20997));

		assertNull(trip.get(101 + TripGear.IDLE_TICKS));
		PlayerItems next = items(4151);
		trip.onHit(101 + TripGear.IDLE_TICKS, () -> next);
		assertSame(next, trip.get(200 + TripGear.IDLE_TICKS));
	}
}
