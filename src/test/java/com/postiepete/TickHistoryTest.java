package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Collections;
import org.junit.Test;

public class TickHistoryTest
{
	private static final PlayerItems WHIP = items(4151);
	private static final PlayerItems BOW = items(20997);
	private static final PlayerItems BLOWPIPE = items(12926);

	@Test
	public void returnsTheItemsInEffectOnTheTick()
	{
		TickHistory<PlayerItems> history = new TickHistory<>();
		history.record(100, WHIP);
		history.record(200, BOW);

		assertEquals(WHIP, history.at(100));
		assertEquals(WHIP, history.at(199));
		assertEquals(BOW, history.at(200));
		assertEquals(BOW, history.at(5000));
	}

	@Test
	public void returnsNothingBeforeTheFirstSnapshot()
	{
		TickHistory<PlayerItems> history = new TickHistory<>();
		assertNull(history.at(100));

		history.record(100, WHIP);
		assertNull(history.at(99));
	}

	@Test
	public void keepsTheFirstTickOfUnchangedItems()
	{
		TickHistory<PlayerItems> history = new TickHistory<>();
		history.record(100, WHIP);
		history.record(150, items(4151));
		history.record(200, BOW);

		assertEquals(WHIP, history.at(120));
		assertEquals(WHIP, history.at(100));
	}

	@Test
	public void aLaterChangeOnTheSameTickReplacesTheEarlierOne()
	{
		TickHistory<PlayerItems> history = new TickHistory<>();
		history.record(100, WHIP);
		history.record(200, BOW);
		history.record(200, BLOWPIPE);

		assertEquals(BLOWPIPE, history.at(200));
		assertEquals(WHIP, history.at(199));
	}

	@Test
	public void pruningKeepsTheSnapshotInEffectAtTheCutoff()
	{
		TickHistory<PlayerItems> history = new TickHistory<>();
		history.record(100, WHIP);
		history.record(200, BOW);
		history.record(TickHistory.MAX_AGE_TICKS + 500, BLOWPIPE);

		history.prune(TickHistory.MAX_AGE_TICKS + 300);

		// Cutoff is tick 300: the bow was still worn then, the whip is dropped
		assertNull(history.at(150));
		assertEquals(BOW, history.at(300));
		assertEquals(BLOWPIPE, history.at(TickHistory.MAX_AGE_TICKS + 500));
	}

	@Test
	public void pruningNeverDropsTheLatestSnapshot()
	{
		TickHistory<PlayerItems> history = new TickHistory<>();
		history.record(100, WHIP);

		history.prune(TickHistory.MAX_AGE_TICKS * 10);

		assertEquals(WHIP, history.at(TickHistory.MAX_AGE_TICKS * 10));
	}

	private static PlayerItems items(int weaponId)
	{
		return new PlayerItems(null,
			Collections.singletonMap("WEAPON", new PlayerItems.SlotItem(weaponId, 1)),
			Collections.emptyMap(), null, null);
	}
}
