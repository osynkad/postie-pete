package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import org.junit.Test;

public class KillTimeTest
{
	@Test
	public void newPersonalBestIsTheDuration()
	{
		LootEvent.KillTime time = parse("Fight duration: <col=ff0000>1:19.80</col> (new personal best)");
		assertEquals(79.8, time.getDuration(), 1e-9);
		assertEquals(79.8, time.getPersonalBest(), 1e-9);
		assertTrue(time.isNewPersonalBest());
	}

	@Test
	public void sendsThePersonalBestTheMessageShows()
	{
		LootEvent.KillTime time = parse("Fight duration: <col=ff0000>1:23.40</col>. Personal best: 1:20.00");
		assertEquals(83.4, time.getDuration(), 1e-9);
		assertEquals(80.0, time.getPersonalBest(), 1e-9);
		assertFalse(time.isNewPersonalBest());
	}

	@Test
	public void readsTheColosseumRunTime()
	{
		// As seen in game, after "Wave 12 completed!"
		LootEvent.KillTime time = parse("Colosseum duration: <col=ef1020>30:37</col> (new personal best)");
		assertEquals(1837.0, time.getDuration(), 1e-9);
		assertTrue(time.isNewPersonalBest());

		time = parse("Colosseum duration: @mes_hl_red@31:02.40</col>. Personal best: @mes_hl_red@30:37.00</col>");
		assertEquals(1862.4, time.getDuration(), 1e-9);
		assertEquals(1837.0, time.getPersonalBest(), 1e-9);
	}

	@Test
	public void aTimeThatTiesThePersonalBestIsNotANewOne()
	{
		LootEvent.KillTime time = parse("Fight duration: <col=ff0000>1:20.00</col>. Personal best: 1:20.00");
		assertEquals(80.0, time.getDuration(), 1e-9);
		assertEquals(80.0, time.getPersonalBest(), 1e-9);
		assertFalse(time.isNewPersonalBest());
	}

	@Test
	public void readsAColouredPersonalBest()
	{
		LootEvent.KillTime time = parse(
			"Theatre of Blood completion time: <col=ff0000>25:01.20</col>. Personal best: <col=ff0000>24:10.80</col>");
		assertEquals(1501.2, time.getDuration(), 1e-9);
		assertEquals(1450.8, time.getPersonalBest(), 1e-9);
	}

	@Test
	public void readsHighlightCodes()
	{
		LootEvent.KillTime time = parse("Fight duration: @mes_hl_red@0:48.60</col>. Personal best: @mes_hl_red@0:41.20");
		assertEquals(48.6, time.getDuration(), 1e-9);
		assertEquals(41.2, time.getPersonalBest(), 1e-9);
	}

	@Test
	public void noPersonalBestWhenTheMessageShowsNone()
	{
		LootEvent.KillTime time = parse("The Fractured Archive: Total completion time: <col=ef1020>18:05.80</col>");
		assertEquals(1085.8, time.getDuration(), 1e-9);
		assertNull(time.getPersonalBest());
		assertFalse(time.isNewPersonalBest());
	}

	private static LootEvent.KillTime parse(String message)
	{
		Matcher matcher = PostiePetePlugin.KILL_DURATION_PATTERN.matcher(message);
		assertTrue(message, matcher.find());
		return PostiePetePlugin.parseKillTime(matcher);
	}
}
