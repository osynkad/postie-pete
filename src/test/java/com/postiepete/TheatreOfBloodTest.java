package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.regex.Matcher;
import net.runelite.client.util.Text;
import org.junit.Test;

public class TheatreOfBloodTest
{
	@Test
	public void roomWithItsTime()
	{
		Matcher matcher = TheatreOfBlood.ROOM_COMPLETE_PATTERN.matcher(Text.removeTags(
			"Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>2:05.40</col><br>"
				+ "Total: <col=ff0000>2:05.40</col>"));
		assertTrue(matcher.lookingAt());
		assertEquals("The Maiden of Sugadinti", matcher.group("room"));
		assertEquals("2:05.40", matcher.group("time"));
	}

	@Test
	public void verzikRoomInHardMode()
	{
		Matcher matcher = TheatreOfBlood.ROOM_COMPLETE_PATTERN.matcher(
			"Wave 'The Final Challenge' (Hard Mode) complete!Duration: 4:12Total: 24:30");
		assertTrue(matcher.lookingAt());
		assertEquals("The Final Challenge", matcher.group("room"));
		assertEquals("4:12", matcher.group("time"));
	}

	@Test
	public void colosseumWaveIsNotARoom()
	{
		assertFalse(new TheatreOfBlood().onRoomMessage("Wave 2 completed! Wave duration: 1:09.60"));
	}

	@Test
	public void theFirstRoomStartsANewRun()
	{
		TheatreOfBlood tob = new TheatreOfBlood();
		assertTrue(tob.onRoomMessage("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!Duration: 2:30Total: 2:30"));
		assertTrue(tob.onRoomMessage("Wave 'The Pestilent Bloat' (Normal Mode) complete!Duration: 1:00Total: 3:30"));
		// Wiped, and started again
		tob.onRoomMessage("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!Duration: 2:05.40Total: 2:05.40");
		tob.onRoomMessage("Wave 'Xarpus' (Normal Mode) complete!Duration: 3:06.60Total: 5:12");

		List<LootEvent.Split> splits = tob.consumeSplits();
		assertEquals(2, splits.size());
		assertEquals(new LootEvent.Split("The Maiden of Sugadinti", 125.4), splits.get(0));
		assertEquals(new LootEvent.Split("Xarpus", 186.6), splits.get(1));
	}

	@Test
	public void raidTimeIsTheCompletionTimeInVerziksRoomMessage()
	{
		LootEvent.KillTime time = TheatreOfBlood.completionTime(
			"Wave 'The Final Challenge' (Normal Mode) complete!<br>Duration: <col=ff0000>5:18.00</col><br>"
				+ "Theatre of Blood completion time: <col=ff0000>18:23.40</col>. Personal best: 16:00.60");
		assertEquals(1103.4, time.getDuration(), 1e-9);
		assertEquals(960.6, time.getPersonalBest(), 1e-9);
		assertFalse(time.isNewPersonalBest());
	}

	@Test
	public void raidTimeNewPersonalBest()
	{
		LootEvent.KillTime time = TheatreOfBlood.completionTime(
			"Wave 'The Final Challenge' (Hard Mode) complete!<br>Duration: <col=ff0000>5:18.00</col><br>"
				+ "Theatre of Blood: Hard Mode completion time: <col=ff0000>24:10.20</col> (new personal best)");
		assertEquals(1450.2, time.getDuration(), 1e-9);
		assertTrue(time.isNewPersonalBest());
	}

	@Test
	public void roomsBeforeVerzikHaveNoRaidTime()
	{
		assertNull(TheatreOfBlood.completionTime(
			"Wave 'Xarpus' (Normal Mode) complete!<br>Duration: <col=ff0000>3:06.60</col><br>"
				+ "Total: <col=ff0000>13:05.40</col>"));
		assertNull(TheatreOfBlood.completionTime(
			"Theatre of Blood total completion time: <col=ff0000>20:52.80</col>. Personal best: 17:53.40"));
	}
}
