package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RaidTeamsTest
{
	@Test
	public void coxTeamSizeFromTheCompletionMessage()
	{
		assertEquals(Integer.valueOf(3), RaidTeams.coxTeamSize(
			"Congratulations - your raid is complete!Team size: 3 players Duration: 21:30 Personal best: 20:00"));
		assertEquals(Integer.valueOf(1), RaidTeams.coxTeamSize(
			"Congratulations - your raid is complete!Team size: Solo Duration: 30:12 (new personal best)"));
		assertEquals(Integer.valueOf(24), RaidTeams.coxTeamSize(
			"Congratulations - your raid is complete!Team size: 24+ players Duration: 40:00"));
		assertNull(RaidTeams.coxTeamSize("Congratulations - your raid is complete!"));
	}

	@Test
	public void raidKillCountNamesMapToTheirChest()
	{
		assertEquals("Theatre of Blood", RaidTeams.chestName("Theatre of Blood: Hard Mode"));
		assertEquals("Theatre of Blood", RaidTeams.chestName("Theatre of Blood"));
		assertEquals("Tombs of Amascut", RaidTeams.chestName("Tombs of Amascut: Expert Mode"));
		assertEquals("Chambers of Xeric", RaidTeams.chestName("Chambers of Xeric Challenge Mode"));
		assertEquals("Fractured Archive", RaidTeams.chestName("Fractured Archive"));
		assertNull(RaidTeams.chestName("Nex"));
		assertNull(RaidTeams.chestName(null));
	}

	@Test
	public void countsOccupiedPartySlots()
	{
		assertEquals(3, RaidTeams.occupiedSlots(1, 2, 0, 1, 0));
		assertEquals(0, RaidTeams.occupiedSlots(0, 0, 0, 0, 0));
	}

	@Test
	public void sizeIsKeptUntilTheChest()
	{
		RaidTeams teams = new RaidTeams();
		teams.onCompleted("Theatre of Blood", 4);
		// Unknown sizes don't replace it
		teams.onCompleted("Theatre of Blood", null);

		assertEquals(Integer.valueOf(4), teams.takeSize("Theatre of Blood"));
		assertNull(teams.takeSize("Theatre of Blood"));
	}
}
