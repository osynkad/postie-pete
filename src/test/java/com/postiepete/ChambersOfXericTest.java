package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import java.util.regex.Matcher;
import net.runelite.client.util.Text;
import org.junit.Test;

public class ChambersOfXericTest
{
	@Test
	public void levelSplitsAreTheTimeSinceTheLevelBefore()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onRaidStarted(0);
		cox.onLevelCompleted("Upper", 421.0, ticks(421.0));
		cox.onLevelCompleted("Middle", 790.2, ticks(790.2));
		cox.onLevelCompleted("Lower", 1100.0, ticks(1100.0));
		cox.onRaidCompleted(1290.0, 31250, 12800);

		assertSplits(cox.consumeSplits(),
			new LootEvent.Split("Upper level", 421.0),
			new LootEvent.Split("Middle level", 369.2),
			new LootEvent.Split("Lower level", 309.8),
			new LootEvent.Split("Great Olm", 190.0));
		assertEquals(new LootEvent.Points(31250, 12800), cox.consumePoints());
	}

	@Test
	public void withoutTheRaidStartTheFirstLevelSeenHasNoSplit()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onLevelCompleted("Upper", 421.0, ticks(421.0));
		cox.onLevelCompleted("Lower", 800.0, ticks(800.0));
		cox.onRaidCompleted(1000.0, 20000, 20000);

		assertSplits(cox.consumeSplits(),
			new LootEvent.Split("Lower level", 379.0),
			new LootEvent.Split("Great Olm", 200.0));
	}

	@Test
	public void noOlmSplitWithoutALevelOrTheRaidTime()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onRaidStarted(0);
		cox.onRaidCompleted(1000.0, 20000, 20000);
		assertTrue(cox.consumeSplits().isEmpty());

		cox.onRaidStarted(0);
		cox.onLevelCompleted("Upper", 421.0, ticks(421.0));
		cox.onRaidCompleted(null, 20000, 20000);
		assertSplits(cox.consumeSplits(), new LootEvent.Split("Upper level", 421.0));
	}

	@Test
	public void raidStartClearsAFailedRaid()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onRaidStarted(0);
		cox.onLevelCompleted("Upper", 500.0, ticks(500.0));

		cox.onRaidStarted(0);
		cox.onLevelCompleted("Upper", 400.0, ticks(400.0));
		cox.onRaidCompleted(900.0, 20000, 20000);

		assertSplits(cox.consumeSplits(),
			new LootEvent.Split("Upper level", 400.0),
			new LootEvent.Split("Great Olm", 500.0));
	}

	@Test
	public void pointsAreConsumedByTheChest()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onRaidCompleted(900.0, 20000, 0);
		assertEquals(new LootEvent.Points(20000, 0), cox.consumePoints());
		assertNull(cox.consumePoints());
	}

	@Test
	public void levelCompleteMessage()
	{
		Matcher matcher = ChambersOfXeric.LEVEL_COMPLETE_PATTERN.matcher(Text.removeTags(
			"<col=ef20ff>Upper level complete! Duration: </col><col=ff0000>7:01</col>"));
		assertTrue(matcher.find());
		assertEquals("Upper", matcher.group("level"));
		assertEquals("7:01", matcher.group("time"));

		matcher = ChambersOfXeric.LEVEL_COMPLETE_PATTERN.matcher("Lower level complete! Duration: 18:20.40");
		assertTrue(matcher.find());
		assertEquals("Lower", matcher.group("level"));
		assertEquals("18:20.40", matcher.group("time"));

		assertFalse(ChambersOfXeric.LEVEL_COMPLETE_PATTERN.matcher(
			"Congratulations - your raid is complete!Team size: 3 players Duration: 21:30").find());
	}

	@Test
	public void raidTimeFromTheCompletionMessage()
	{
		LootEvent.KillTime time = ChambersOfXeric.completionTime(Text.removeTags(
			"<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>3 players</col>"
				+ " Duration:</col> <col=ff0000>21:30</col> Personal best: </col><col=ff0000>20:00</col>"));
		assertEquals(1290.0, time.getDuration(), 1e-9);
		assertEquals(1200.0, time.getPersonalBest(), 1e-9);
		assertFalse(time.isNewPersonalBest());

		time = ChambersOfXeric.completionTime(Text.removeTags(
			"<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col>"
				+ " Duration:</col> <col=ff0000>30:12.60</col> (new personal best)</col>"));
		assertEquals(1812.6, time.getDuration(), 1e-9);
		assertTrue(time.isNewPersonalBest());

		assertNull(ChambersOfXeric.completionTime("Congratulations - your raid is complete!"));
	}

	@Test
	public void roomsComeInOrderBeforeTheirLevel()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onRaidStarted(0);
		// Tekton cleared 3:38.40 in, Ice demon 4:00 later
		cox.onRoomCompleted("Tekton", 364);
		cox.onRoomCompleted("Ice demon", 764);
		cox.onLevelCompleted("Upper", 520.2, 867);
		// The next level's first room counts from the level's end
		cox.onRoomCompleted("Vasa", 1167);
		cox.onLevelCompleted("Middle", 800.4, 1334);
		cox.onRaidCompleted(1000.2, 20000, 10000);

		assertSplits(cox.consumeSplits(),
			new LootEvent.Split("Tekton", 218.4),
			new LootEvent.Split("Ice demon", 240.0),
			new LootEvent.Split("Upper level", 520.2),
			new LootEvent.Split("Vasa", 180.0),
			new LootEvent.Split("Middle level", 280.2),
			new LootEvent.Split("Great Olm", 199.8));
	}

	@Test
	public void withoutTheRaidStartTheFirstRoomSeenHasNoSplit()
	{
		ChambersOfXeric cox = new ChambersOfXeric();
		cox.onRoomCompleted("Tekton", 364);
		cox.onRoomCompleted("Ice demon", 764);
		cox.onLevelCompleted("Upper", 520.2, 867);
		cox.onRoomCompleted("Vasa", 1167);
		cox.onLevelCompleted("Middle", 800.4, 1334);

		// Tekton and Upper level have no start; Ice demon counts from Tekton
		assertSplits(cox.consumeSplits(),
			new LootEvent.Split("Ice demon", 240.0),
			new LootEvent.Split("Vasa", 180.0),
			new LootEvent.Split("Middle level", 280.2));
	}

	@Test
	public void roomsFromTheRaidMapTemplate()
	{
		// Plane 1, chunk x 413 and y 661 (part of the room at 103 << 2 and 165 << 2), rotated
		assertEquals("Tekton", CoxRooms.roomName(1 << 24 | 413 << 14 | 661 << 3 | 2 << 1));
		assertEquals("Ice demon", CoxRooms.roomName(102 * 4 << 14 | 167 * 4 << 3));
		assertEquals("Crabs", CoxRooms.roomName(2 << 24 | 104 * 4 << 14 | 167 * 4 << 3 | 3 << 1));
		// Scavengers: no obstacle to follow
		assertNull(CoxRooms.roomName(102 * 4 << 14 | 163 * 4 << 3));
		assertTrue(CoxRooms.isObstacle(net.runelite.api.gameval.ObjectID.RAIDS_BLOCKAGE_ORANGE));
	}

	private static int ticks(double seconds)
	{
		return (int) Math.round(seconds / 0.6);
	}

	private static void assertSplits(List<LootEvent.Split> actual, LootEvent.Split... expected)
	{
		assertEquals(expected.length, actual.size());
		for (int i = 0; i < expected.length; i++)
		{
			assertEquals(expected[i].getName(), actual.get(i).getName());
			assertEquals(expected[i].getDuration(), actual.get(i).getDuration(), 1e-9);
		}
	}
}
