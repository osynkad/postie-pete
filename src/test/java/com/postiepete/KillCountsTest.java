package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class KillCountsTest
{
	private static final int NPC_MAX_AGE = 2;
	private static final int CHEST_MAX_AGE = 1000;
	private static final int WITHOUT_LOOT = 10;

	private final KillCounts killCounts = new KillCounts(NPC_MAX_AGE, CHEST_MAX_AGE, WITHOUT_LOOT);

	private static LootEvent.Kill kill(String name, int count)
	{
		return new LootEvent.Kill(name, count);
	}

	@Test
	public void readsBossKillCount()
	{
		assertEquals(kill("TzKal-Zuk", 12),
			killCounts.onMessage("Your TzKal-Zuk kill count is: <col=ff0000>12</col>.", 0));
	}

	@Test
	public void readsRaidWithItsMode()
	{
		assertEquals(kill("Theatre of Blood: Hard Mode", 40),
			killCounts.onMessage("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>40</col>.", 0));
		assertEquals(kill("Chambers of Xeric Challenge Mode", 3),
			killCounts.onMessage("Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>3</col>.", 0));
	}

	@Test
	public void readsOtherKillCountWordings()
	{
		assertEquals(kill("Wintertodt", 1234),
			killCounts.onMessage("Your subdued Wintertodt count is: <col=ff0000>1,234</col>.", 0));
		assertEquals(kill("Gauntlet", 7),
			killCounts.onMessage("Your Gauntlet completion count is: <col=ff0000>7</col>.", 0));
		assertEquals(kill("Barrows chest", 99),
			killCounts.onMessage("Your Barrows chest count is: <col=ff0000>99</col>.", 0));
	}

	@Test
	public void ignoresOtherMessages()
	{
		assertNull(killCounts.onMessage("Your reward point tally is: 5.", 0));
		assertNull(killCounts.onMessage("Fight duration: <col=ff0000>1:23.40</col>. Personal best: 1:20.00", 0));
	}

	@Test
	public void npcLootClaimsARecentKillCountOnce()
	{
		killCounts.onMessage("Your Vorkath kill count is: <col=ff0000>500</col>.", 100);
		assertEquals(kill("Vorkath", 500), killCounts.claimForNpcLoot(100 + NPC_MAX_AGE));
		assertNull(killCounts.claimForNpcLoot(100 + NPC_MAX_AGE));
	}

	@Test
	public void npcLootDoesNotClaimAnOldKillCount()
	{
		killCounts.onMessage("Your Vorkath kill count is: <col=ff0000>500</col>.", 100);
		assertNull(killCounts.claimForNpcLoot(101 + NPC_MAX_AGE));
	}

	@Test
	public void npcLootKilledAfterAnotherActivityDoesNotTakeItsKillCount()
	{
		// A lap count from finishing a lap, then a Man killed a few seconds later
		killCounts.onMessage("Your Seers' Village Rooftop lap count is: <col=ff0000>50</col>.", 100);
		assertNull(killCounts.claimForNpcLoot(108));
	}

	@Test
	public void npcLootLeavesARaidsKillCountForTheChest()
	{
		killCounts.onMessage("Your completed Fractured Archive count is: <col=ff0000>8</col>.", 100);
		assertNull(killCounts.claimForNpcLoot(101));
		assertEquals(kill("Fractured Archive", 8), killCounts.claimForChest("Fractured Archive", 600));
	}

	@Test
	public void chestClaimsAKillCountThatNamesIt()
	{
		killCounts.onMessage("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>40</col>.", 100);
		assertEquals(kill("Theatre of Blood: Hard Mode", 40), killCounts.claimForChest("Theatre of Blood", 900));
	}

	@Test
	public void chestDoesNotClaimAnotherBosssKillCount()
	{
		killCounts.onMessage("Your Sol Heredit kill count is: <col=ff0000>2</col>.", 100);
		assertNull(killCounts.claimForChest("Fortis Colosseum", 101));
		// The Colosseum chest asks for Sol Heredit's by name, and the failed claim above left it there
		assertEquals(kill("Sol Heredit", 2), killCounts.claimForChest("Sol Heredit", 102));
	}

	@Test
	public void chestDoesNotClaimAnOldKillCount()
	{
		killCounts.onMessage("Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>5</col>.", 100);
		assertNull(killCounts.claimForChest("Tombs of Amascut", 101 + CHEST_MAX_AGE));
	}

	@Test
	public void infernoWithoutLootBecomesAKillOnceItsWaitIsOver()
	{
		killCounts.onMessage("Your TzKal-Zuk kill count is: <col=ff0000>3</col>.", 100);
		assertNull(killCounts.takeKillWithoutLoot(100 + WITHOUT_LOOT));
		assertEquals(kill("TzKal-Zuk", 3), killCounts.takeKillWithoutLoot(101 + WITHOUT_LOOT));
		assertNull(killCounts.takeKillWithoutLoot(102 + WITHOUT_LOOT));
	}

	@Test
	public void killWithLootIsNotAlsoAKillWithoutLoot()
	{
		killCounts.onMessage("Your TzKal-Zuk kill count is: <col=ff0000>3</col>.", 100);
		assertEquals(kill("TzKal-Zuk", 3), killCounts.claimForNpcLoot(100));
		assertNull(killCounts.takeKillWithoutLoot(200));
	}

	@Test
	public void otherKillCountsAreNeverKillsWithoutLoot()
	{
		killCounts.onMessage("Your Vorkath kill count is: <col=ff0000>500</col>.", 100);
		assertNull(killCounts.takeKillWithoutLoot(200));
		killCounts.onMessage("Your Ardougne Rooftop lap count is: <col=ff0000>12</col>.", 300);
		assertNull(killCounts.takeKillWithoutLoot(400));
		killCounts.onMessage("Your Sol Heredit kill count is: <col=ff0000>2</col>.", 500);
		assertNull(killCounts.takeKillWithoutLoot(600));
	}

	@Test
	public void wintertodtIsAKillWithoutLoot()
	{
		killCounts.onMessage("Your subdued Wintertodt count is: <col=ff0000>1,234</col>.", 100);
		assertEquals(kill("Wintertodt", 1234), killCounts.takeKillWithoutLoot(200));
	}
}
