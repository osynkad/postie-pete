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
	public void doomFullRunAsSeenInGame()
	{
		Matcher newBest = PostiePetePlugin.DOOM_FULL_RUN_PATTERN.matcher(
			"Delve level 1 - 8 duration: 7:08.40 (new personal best)");
		assertTrue(newBest.find());
		LootEvent.KillTime time = PostiePetePlugin.parseKillTime(newBest);
		assertEquals(428.4, time.getDuration(), 0.001);
		assertEquals(428.4, time.getPersonalBest(), 0.001);
		assertTrue(time.isNewPersonalBest());

		Matcher notBest = PostiePetePlugin.DOOM_FULL_RUN_PATTERN.matcher(
			"Delve level 1 - 8 duration: 12:03.00. Personal best: 10:49.80");
		assertTrue(notBest.find());
		time = PostiePetePlugin.parseKillTime(notBest);
		assertEquals(723.0, time.getDuration(), 0.001);
		assertEquals(649.8, time.getPersonalBest(), 0.001);
		assertFalse(time.isNewPersonalBest());
	}

	@Test
	public void doomLevelStartIsNotACompletion()
	{
		assertFalse(PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher("Delve level: 8+ (23)").lookingAt());
		assertFalse(PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher("<col=ef1020>Delve level: 8+ (23)</col>").lookingAt());
	}

	@Test
	public void doomFullRunWithItsPersonalBest()
	{
		Matcher matcher = PostiePetePlugin.DOOM_FULL_RUN_PATTERN.matcher(
			"Delve level 1 - 8 duration: <col=ef1020>11:35</col>. Personal best: <col=ef1020>9:38</col>");
		assertTrue(matcher.find());
		LootEvent.KillTime time = PostiePetePlugin.parseKillTime(matcher);
		assertEquals(695, time.getDuration(), 0.001);
		assertEquals(578, time.getPersonalBest(), 0.001);
		assertFalse(time.isNewPersonalBest());
	}

	@Test
	public void doomFullRunNewPersonalBest()
	{
		Matcher matcher = PostiePetePlugin.DOOM_FULL_RUN_PATTERN.matcher(
			"Delve level 1 - 8 duration: @mes_hl_red@9:12.60</col> (new personal best)");
		assertTrue(matcher.find());
		LootEvent.KillTime time = PostiePetePlugin.parseKillTime(matcher);
		assertEquals(552.6, time.getDuration(), 0.001);
		assertEquals(552.6, time.getPersonalBest(), 0.001);
		assertTrue(time.isNewPersonalBest());
	}

	@Test
	public void doomLevelIsNotAFullRun()
	{
		assertFalse(PostiePetePlugin.DOOM_FULL_RUN_PATTERN.matcher(
			"Delve level: 8 duration: <col=ef1020>1:28</col>. Personal best: <col=ef1020>1:08</col>").find());
		assertFalse(PostiePetePlugin.DOOM_LEVEL_PATTERN.matcher(
			"Delve level 1 - 8 duration: <col=ef1020>11:35</col>. Personal best: <col=ef1020>9:38</col>").lookingAt());
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
}
