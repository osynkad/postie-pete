package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import net.runelite.client.util.Text;
import org.junit.Test;

public class TombsOfAmascutTest
{
	// As RuneLite's chat commands tests have it
	private static final String WARDENS = "Challenge complete: The Wardens. Duration: <col=ef1020>9:40</col><br>"
		+ "Tombs of Amascut: Expert Mode challenge completion time: <col=ef1020>25:10.20</col>. Personal best: 24:01.80";

	@Test
	public void challengesComeInOrderAndTheWardensCompleteTheRaid()
	{
		TombsOfAmascut toa = new TombsOfAmascut();
		assertTrue(toa.onChallengeMessage(challenge("Path of Het", "2:10.20", "2:10.20"), 300));
		assertTrue(toa.onChallengeMessage(challenge("Akkha", "1:47.40", "3:57.60"), 300));
		assertTrue(toa.onChallengeMessage(challenge("Path of Scabaras", "2:00", "5:57.60"), 300));
		assertTrue(toa.onChallengeMessage(Text.removeTags(WARDENS), 300));
		// A new raid can't start before the chest is opened: later messages don't change the completed one
		assertEquals(Integer.valueOf(300), toa.consumeInvocation());
		assertNull(toa.consumeInvocation());

		List<LootEvent.Split> splits = toa.consumeSplits();
		assertEquals(4, splits.size());
		assertEquals(new LootEvent.Split("Path of Het", 130.2), splits.get(0));
		assertEquals(new LootEvent.Split("Akkha", 107.4), splits.get(1));
		assertEquals(new LootEvent.Split("Path of Scabaras", 120.0), splits.get(2));
		assertEquals(new LootEvent.Split("The Wardens", 580.0), splits.get(3));
	}

	@Test
	public void theFirstChallengeOfARaidClearsAFailedOne()
	{
		TombsOfAmascut toa = new TombsOfAmascut();
		toa.onChallengeMessage(challenge("Path of Apmeken", "2:35", "2:35"), 150);
		toa.onChallengeMessage(challenge("Ba-Ba", "1:30", "4:05"), 150);
		// Wiped, and started again: its total is its own time
		toa.onChallengeMessage(challenge("Path of Crondis", "1:50.40", "1:50.40"), 150);

		List<LootEvent.Split> splits = toa.consumeSplits();
		assertEquals(1, splits.size());
		assertEquals("Path of Crondis", splits.get(0).getName());
	}

	@Test
	public void personalPointsWithoutATotal()
	{
		TombsOfAmascut toa = new TombsOfAmascut();
		assertNull(toa.consumePoints());

		toa.onChallengeMessage(challenge("Path of Het", "2:10.20", "2:10.20"), 300);
		toa.onPersonalPoints(18450);
		assertEquals(new LootEvent.Points(null, 18450), toa.consumePoints());
		assertNull(toa.consumePoints());

		// A raid's points don't carry over to the next one
		toa.onPersonalPoints(9000);
		toa.onChallengeMessage(challenge("Path of Crondis", "1:50.40", "1:50.40"), 300);
		assertNull(toa.consumePoints());
	}

	@Test
	public void raidTimeIsTheCompletionTimeInTheWardensMessage()
	{
		LootEvent.KillTime time = TombsOfAmascut.completionTime(WARDENS);
		assertEquals(1510.2, time.getDuration(), 1e-9);
		assertEquals(1441.8, time.getPersonalBest(), 1e-9);
		assertFalse(time.isNewPersonalBest());

		time = TombsOfAmascut.completionTime("Challenge complete: The Wardens. Duration: <col=ef1020>8:30</col><br>"
			+ "Tombs of Amascut challenge completion time: <col=ef1020>8:31</col> (new personal best)");
		assertEquals(511.0, time.getDuration(), 1e-9);
		assertTrue(time.isNewPersonalBest());

		assertNull(TombsOfAmascut.completionTime(
			"Tombs of Amascut total completion time: <col=ef1020>27:01</col> (new personal best)"));
		assertNull(TombsOfAmascut.completionTime(challenge("Akkha", "1:47.40", "3:57.60")));
	}

	@Test
	public void fracturedArchiveBossesAreNotChallenges()
	{
		TombsOfAmascut toa = new TombsOfAmascut();
		assertFalse(toa.onChallengeMessage(Text.removeTags(
			"Challenge complete: Ocellus. Duration: <col=ef1020>2:25.80</col>. Total: <col=ef1020>2:25.80</col>"
				+ " (new personal best)"), 0));
		assertFalse(toa.onChallengeMessage("Challenge started: Archive Guardian.", 0));
		assertTrue(toa.consumeSplits().isEmpty());
	}

	private static String challenge(String room, String duration, String total)
	{
		return Text.removeTags("Challenge complete: " + room + ". Duration: <col=ef1020>" + duration + "</col>. Total: "
			+ "<col=ef1020>" + total + "</col>");
	}
}
