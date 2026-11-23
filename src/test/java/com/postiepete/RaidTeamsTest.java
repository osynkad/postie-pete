package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RaidTeamsTest
{
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

		assertEquals(new LootEvent.Team(4, null), teams.takeTeam("Theatre of Blood"));
		assertNull(teams.takeTeam("Theatre of Blood"));
	}

	@Test
	public void chambersOfXericKeepsItsScaling()
	{
		RaidTeams teams = new RaidTeams();
		teams.onCompleted("Chambers of Xeric", 3, 2);

		assertEquals(new LootEvent.Team(3, 2), teams.takeTeam("Chambers of Xeric"));
	}
}
