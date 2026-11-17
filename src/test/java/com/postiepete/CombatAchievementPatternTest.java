package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.regex.Matcher;
import net.runelite.client.util.Text;
import org.junit.Test;

public class CombatAchievementPatternTest
{
	private static Matcher match(String rawMessage)
	{
		return PostiePetePlugin.COMBAT_ACHIEVEMENT_PATTERN.matcher(Text.removeTags(rawMessage));
	}

	@Test
	public void matchesMessageWithTaskIdAndColourCode()
	{
		Matcher matcher = match("CA_ID:648|Congratulations, you've completed a hard combat task: "
			+ "@ach_comp@Perfect Mad Angel</col> (3 points).");
		assertTrue(matcher.matches());
		assertEquals("hard", matcher.group("tier"));
		assertEquals("Perfect Mad Angel", matcher.group("task"));
	}

	@Test
	public void matchesMessageWithoutTaskId()
	{
		Matcher matcher = match("Congratulations, you've completed an elite combat task: "
			+ "<col=06600c>Peach Conjurer</col> (4 points).");
		assertTrue(matcher.matches());
		assertEquals("elite", matcher.group("tier"));
		assertEquals("Peach Conjurer", matcher.group("task"));
	}

	@Test
	public void ignoresClanBroadcast()
	{
		assertFalse(match("CA_ID:516|Dasset has completed a master combat task: Duke Sucellus Master.").matches());
	}
}
