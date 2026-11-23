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
	public void onlyChestsOpenedLongAfterTheirTimeTakeAnOldKillTime()
	{
		int shortWindow = PostiePetePlugin.chestKillTimeMaxAge("Barrows");
		for (String chest : new String[]{"Theatre of Blood", "Tombs of Amascut", "Chambers of Xeric", "Fractured Archive",
			"Fortis Colosseum", "Doom of Mokhaiotl"})
		{
			assertTrue(chest, PostiePetePlugin.chestKillTimeMaxAge(chest) > shortWindow);
		}
		assertEquals(shortWindow, PostiePetePlugin.chestKillTimeMaxAge("Clue Scroll (Hard)"));
		assertEquals(shortWindow, PostiePetePlugin.chestKillTimeMaxAge("Reward cart (Wintertodt)"));
		assertEquals(shortWindow, PostiePetePlugin.chestKillTimeMaxAge(null));
	}

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

	@Test
	public void theatreOfBloodTimeIsTheCompletionTimeInVerziksRoomMessage()
	{
		LootEvent.KillTime time = PostiePetePlugin.tobCompletionTime(
			"Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>5:18.00</col><br>"
				+ "Theatre of Blood completion time: <col=ff0000>18:23.40</col>. Personal best: 16:00.60");
		assertEquals(1103.4, time.getDuration(), 1e-9);
		assertEquals(960.6, time.getPersonalBest(), 1e-9);
		assertFalse(time.isNewPersonalBest());
	}

	@Test
	public void theatreOfBloodNewPersonalBest()
	{
		LootEvent.KillTime time = PostiePetePlugin.tobCompletionTime(
			"Wave 'The Final Challenge' (Hard Mode) complete!<br>Duration: <col=ff0000>5:18.00</col><br>"
				+ "Theatre of Blood: Hard Mode completion time: <col=ff0000>24:10.20</col> (new personal best)");
		assertEquals(1450.2, time.getDuration(), 1e-9);
		assertTrue(time.isNewPersonalBest());
	}

	@Test
	public void theatreOfBloodRoomsBeforeVerzikHaveNoCompletionTime()
	{
		assertNull(PostiePetePlugin.tobCompletionTime(
			"Wave 'Xarpus' (Normal Mode) complete!<br>Duration: <col=ff0000>3:06.60</col><br>"
				+ "Total: <col=ff0000>13:05.40</col>"));
		assertNull(PostiePetePlugin.tobCompletionTime(
			"Theatre of Blood total completion time: <col=ff0000>20:52.80</col>. Personal best: 17:53.40"));
	}

	@Test
	public void raidCompletionTimesBelongToTheirChest()
	{
		assertEquals("Theatre of Blood", PostiePetePlugin.killTimeRaid(
			"Theatre of Blood completion time: 17:29.40. Personal best: 17:22.00"));
		assertEquals("Tombs of Amascut", PostiePetePlugin.killTimeRaid(
			"Tombs of Amascut: Expert Mode challenge completion time: 25:10.20. Personal best: 24:01.80"));
		assertEquals("Fractured Archive", PostiePetePlugin.killTimeRaid(
			"The Fractured Archive: Total completion time: 18:05.80"));
		assertNull(PostiePetePlugin.killTimeRaid("Fight duration: 1:23.40. Personal best: 1:20.00"));
	}

	private static LootEvent.KillTime parse(String message)
	{
		Matcher matcher = PostiePetePlugin.KILL_DURATION_PATTERN.matcher(message);
		assertTrue(message, matcher.find());
		return PostiePetePlugin.parseKillTime(matcher);
	}
}
