package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import net.runelite.client.util.Text;
import org.junit.Test;

public class WavePatternTest
{
	@Test
	public void colosseumWaveWithItsTime()
	{
		Matcher matcher = PostiePetePlugin.COLOSSEUM_WAVE_PATTERN.matcher(
			"Wave 2 completed! Wave duration: @mes_hl_red@1:09.60</col>");
		assertTrue(matcher.lookingAt());
		assertEquals("2", matcher.group("wave"));
		assertEquals("1:09.60", matcher.group("time"));
	}

	@Test
	public void colosseumWaveWithAColourTag()
	{
		Matcher matcher = PostiePetePlugin.COLOSSEUM_WAVE_PATTERN.matcher(
			"Wave 12 completed! Wave duration: <col=ef1020>2:31.80</col>");
		assertTrue(matcher.lookingAt());
		assertEquals("12", matcher.group("wave"));
		assertEquals("2:31.80", matcher.group("time"));
	}

	@Test
	public void colosseumWaveWithoutATime()
	{
		Matcher matcher = PostiePetePlugin.COLOSSEUM_WAVE_PATTERN.matcher("Wave 3 completed!");
		assertTrue(matcher.lookingAt());
		assertEquals("3", matcher.group("wave"));
		assertNull(matcher.group("time"));
	}

	@Test
	public void doomLevelWithItsTime()
	{
		Matcher matcher = PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher(
			"Delve level: 2 duration: @mes_hl_red@1:19.80</col>. Personal best: 1:10.20");
		assertTrue(matcher.lookingAt());
		assertEquals("2", matcher.group("level"));
		assertNull(matcher.group("deep"));
		assertEquals("1:19.80", matcher.group("time"));
	}

	@Test
	public void doomLevelPastEightHasItsRealLevel()
	{
		// As seen in game
		Matcher matcher = PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher(
			"Delve level: 8+ (22) duration: <col=ef1020>1:08.4</col>. Personal best: <col=ef1020>0:54.0</col>");
		assertTrue(matcher.lookingAt());
		assertEquals("22", matcher.group("deep"));
		assertEquals("1:08.4", matcher.group("time"));
	}

	@Test
	public void doomLevelStartIsNotACompletion()
	{
		assertFalse(PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher("Delve level: 8+ (23)").lookingAt());
		assertFalse(PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher("<col=ef1020>Delve level: 8+ (23)</col>").lookingAt());
	}

	@Test
	public void tzhaarWaveStart()
	{
		Matcher matcher = PostiePetePlugin.TZHAAR_WAVE_PATTERN.matcher(Text.removeTags("<col=ef1020>Wave: 12</col>"));
		assertTrue(matcher.matches());
		assertEquals("12", matcher.group("wave"));
	}

	@Test
	public void colosseumCompletionIsNotATzhaarWaveStart()
	{
		assertFalse(PostiePetePlugin.TZHAAR_WAVE_PATTERN.matcher(
			Text.removeTags("Wave 2 completed! Wave duration: <col=ef1020>1:09.60</col>")).matches());
	}

	@Test
	public void theatreOfBloodRoomWithItsTime()
	{
		Matcher matcher = PostiePetePlugin.TOB_ROOM_COMPLETE_PATTERN.matcher(Text.removeTags(
			"Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>2:05.40</col><br>"
				+ "Total: <col=ff0000>2:05.40</col>"));
		assertTrue(matcher.lookingAt());
		assertEquals("The Maiden of Sugadinti", matcher.group("room"));
		assertEquals("2:05.40", matcher.group("time"));
	}

	@Test
	public void theatreOfBloodVerzikRoomInHardMode()
	{
		Matcher matcher = PostiePetePlugin.TOB_ROOM_COMPLETE_PATTERN.matcher(
			"Wave 'The Final Challenge' (Hard Mode) complete!Duration: 4:12Total: 24:30");
		assertTrue(matcher.lookingAt());
		assertEquals("The Final Challenge", matcher.group("room"));
		assertEquals("4:12", matcher.group("time"));
	}

	@Test
	public void colosseumWaveIsNotATheatreOfBloodRoom()
	{
		assertFalse(PostiePetePlugin.TOB_ROOM_COMPLETE_PATTERN.matcher(
			"Wave 2 completed! Wave duration: 1:09.60").lookingAt());
	}
}
